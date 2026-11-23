#!/usr/bin/env python3
"""저장물 분석 LLM 모델 비교 실험 (#25).

링크 세트(docs/llm-evaluation/links.csv)의 각 링크로 분석 입력을 만들고, 모델마다
카테고리·요약·주제어를 뽑게 해 정답 라벨과 비교한다. 결과 해석과 실행 방법은
docs/llm-evaluation.md에 있다.

    python3 eval.py keys  [--models ID,...]   필요한 키 환경변수가 있는지 확인
    python3 eval.py fetch                     링크마다 분석 입력을 만들어 .cache/에 저장
    python3 eval.py run   [--models ID,...] [--fresh]
                                              모델 호출, out/<모델>.jsonl에 저장. 기본은 이전 결과에서
                                              API 오류가 난 링크만 다시 부른다
    python3 eval.py score                     out/의 결과를 채점해 표 출력, 사람이 볼 review CSV 저장

API 키는 환경변수로만 읽고, 값은 출력하지 않는다.
"""

from __future__ import annotations

import argparse
import csv
import html
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from html.parser import HTMLParser
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
LINKS_CSV = ROOT / "docs" / "llm-evaluation" / "links.csv"
CACHE_DIR = HERE / ".cache"
OUT_DIR = HERE / "out"
CONFIG = HERE / "config.json"

# scope.md "플랫폼별 수집 수준"
LEVELS = {
    "youtube": "auto", "naver_blog": "auto", "news": "auto", "blog": "auto", "notion": "auto",
    "x": "partial", "pinterest": "partial",
    "instagram": "memo", "shop": "memo",
}
OEMBED = {
    "x": "https://publish.twitter.com/oembed?omit_script=1&url=",
    "pinterest": "https://www.pinterest.com/oembed.json?url=",
    "youtube": "https://www.youtube.com/oembed?format=json&url=",
}
# 봇이라고 밝힌다. 노션 공개 페이지는 봇 UA에만 본문을 준다 (11/19 수집 조사). 다른 서비스의 봇 이름은 쓰지 않는다.
USER_AGENT = "Mozilla/5.0 (compatible; link-eval-bot/0.1)"
YOUTUBE_VIDEOS_API = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id="


# ---------- 설정과 링크 세트 ----------

def load_config(path: Path = CONFIG) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


@dataclass
class Link:
    id: str
    platform: str
    url: str
    memo: str
    label_category: str
    label_keywords: list[str]
    label_details: dict[str, str] = field(default_factory=dict)  # 카테고리별 상세 정보 정답. 열 이름 label_<필드 키>

    @property
    def level(self) -> str:
        return LEVELS[self.platform]


def load_links(path: Path = LINKS_CSV) -> list[Link]:
    links = []
    with path.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            platform = row["platform"].strip()
            if platform not in LEVELS:
                raise ValueError(f"{row['id']}: 모르는 플랫폼 {platform!r}")
            links.append(Link(
                id=row["id"].strip(),
                platform=platform,
                url=row["url"].strip(),
                memo=(row.get("memo") or "").strip(),
                label_category=(row.get("label_category") or "").strip(),
                label_keywords=split_keywords(row.get("label_keywords") or ""),
                label_details={k[len("label_"):]: (v or "").strip() for k, v in row.items()
                               if k.startswith("label_") and k not in ("label_category", "label_keywords")
                               and (v or "").strip()},
            ))
    return links


def split_keywords(value: str) -> list[str]:
    return [k.strip() for k in value.split(";") if k.strip()]


def select_models(config: dict, ids: str | None) -> list[dict]:
    models = config["models"]
    if not ids:
        return models
    wanted = [i.strip() for i in ids.split(",") if i.strip()]
    known = {m["id"] for m in models}
    unknown = [i for i in wanted if i not in known]
    if unknown:
        raise SystemExit(f"config.json에 없는 모델: {', '.join(unknown)}")
    return [m for m in models if m["id"] in wanted]


def missing_keys(models: list[dict], environ=os.environ) -> list[str]:
    """비어 있는 키 환경변수 이름. 값은 읽어 비교만 하고 어디에도 남기지 않는다."""
    names = sorted({m["key_env"] for m in models})
    return [n for n in names if not environ.get(n)]


# ---------- 분석 입력 만들기 ----------

class _PageParser(HTMLParser):
    SKIP = {"script", "style", "noscript", "svg", "nav", "footer", "header", "form"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.meta: dict[str, str] = {}
        self.title = ""
        self._in_title = False
        self._skip = 0
        self.chunks: list[str] = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "meta":
            key = a.get("property") or a.get("name")
            if key and a.get("content"):
                self.meta.setdefault(key.lower(), a["content"].strip())
        elif tag == "title":
            self._in_title = True
        elif tag in self.SKIP:
            self._skip += 1

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False
        elif tag in self.SKIP and self._skip:
            self._skip -= 1

    def handle_data(self, data):
        if self._in_title:
            self.title += data
        elif not self._skip and data.strip():
            self.chunks.append(data.strip())


def parse_page(page_html: str) -> dict:
    p = _PageParser()
    p.feed(page_html)
    body = re.sub(r"\s+", " ", " ".join(p.chunks)).strip()
    return {
        "title": p.meta.get("og:title") or p.title.strip(),
        "description": p.meta.get("og:description") or p.meta.get("description", ""),
        "body": body,
    }


def naver_blog_mobile_url(url: str) -> str:
    """blog.naver.com/<id>/<logNo> 와 PostView.naver?blogId=&logNo= 를 본문이 바로 오는 모바일 주소로 바꾼다."""
    u = urllib.parse.urlparse(url)
    if "blog.naver.com" not in u.netloc:
        return url
    q = urllib.parse.parse_qs(u.query)
    if "blogId" in q and "logNo" in q:
        return f"https://m.blog.naver.com/{q['blogId'][0]}/{q['logNo'][0]}"
    parts = [s for s in u.path.split("/") if s]
    if len(parts) >= 2 and parts[1].isdigit():
        return f"https://m.blog.naver.com/{parts[0]}/{parts[1]}"
    return url


def pinterest_pin_url(url: str) -> str:
    """/pin/<설명>--<숫자>/ 형식은 oEmbed가 400을 내므로 /pin/<숫자>/로 바꾼다."""
    m = re.search(r"/pin/(?:[^/]*?-)?(\d+)/?", urllib.parse.urlparse(url).path)
    return f"https://www.pinterest.com/pin/{m.group(1)}/" if m else url


def youtube_video_id(url: str) -> str:
    u = urllib.parse.urlparse(url)
    if u.netloc.endswith("youtu.be"):
        return u.path.strip("/").split("/")[0]
    q = urllib.parse.parse_qs(u.query)
    if "v" in q:
        return q["v"][0]
    parts = [p for p in u.path.split("/") if p]
    if len(parts) >= 2 and parts[0] in ("shorts", "live", "embed"):
        return parts[1]
    return ""


def http_get(url: str, headers: dict | None = None, timeout: float = 15) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept-Language": "ko,en;q=0.8",
                                               **(headers or {})})
    with urllib.request.urlopen(req, timeout=timeout) as res:
        charset = res.headers.get_content_charset() or "utf-8"
        return res.read().decode(charset, errors="replace")


def strip_tags(text: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", text))).strip()


def youtube_description(video_id: str, api_key: str, get=http_get) -> str:
    """Data API videos.list의 설명 전체와 태그. 키는 URL이 아니라 헤더로 보낸다."""
    data = json.loads(get(YOUTUBE_VIDEOS_API + urllib.parse.quote(video_id), headers={"X-Goog-Api-Key": api_key}))
    items = data.get("items") or []
    if not items:
        return ""
    snippet = items[0].get("snippet", {})
    text = snippet.get("description", "")
    if snippet.get("tags"):
        text += "\n태그: " + ", ".join(snippet["tags"])
    return text


def build_input(link: Link, get=http_get, body_max_chars: int = 6000, environ=os.environ) -> dict:
    """수집 수준에 맞춰 모델에 줄 입력을 만든다. 실패하면 error에 이유를 남기고 가진 것만 넣는다."""
    result = {"id": link.id, "platform": link.platform, "level": link.level, "url": link.url,
              "title": "", "author": "", "description": "", "body": "", "memo": "",
              "body_truncated": False, "error": ""}
    try:
        if link.level == "memo":
            result["memo"] = link.memo
        elif link.level == "partial" or link.platform == "youtube":
            url = pinterest_pin_url(link.url) if link.platform == "pinterest" else link.url
            data = json.loads(get(OEMBED[link.platform] + urllib.parse.quote(url, safe="")))
            result["title"] = data.get("title", "")
            result["author"] = data.get("author_name", "")
            if link.platform == "x":
                result["body"] = strip_tags(data.get("html", ""))
            if link.platform == "youtube" and environ.get("YOUTUBE_API_KEY"):
                # 설명은 공식 Data API로만 받는다. 영상 페이지의 OG·HTML은 약관상 쓰지 않는다 (11/19 수집 조사).
                try:
                    result["description"] = youtube_description(
                        youtube_video_id(link.url), environ["YOUTUBE_API_KEY"], get=get)
                except Exception as e:  # noqa: BLE001 - 설명은 없어도 진행한다
                    result["error"] = f"description: {type(e).__name__}"
        else:
            url = naver_blog_mobile_url(link.url) if link.platform == "naver_blog" else link.url
            page = parse_page(get(url))
            result.update(title=page["title"], description=page["description"], body=page["body"])
    except Exception as e:  # noqa: BLE001 - 링크 하나의 실패로 전체를 멈추지 않는다
        result["error"] = str(e)
    if link.level == "partial":
        result["memo"] = link.memo  # 부분 자동은 모자라면 메모를 받는다
    if len(result["body"]) > body_max_chars:
        result["body"] = result["body"][:body_max_chars]
        result["body_truncated"] = True
    return result


# ---------- 프롬프트와 응답 ----------

def fields_of(config: dict, category: str) -> list[dict]:
    return [f for f in config["detail_fields"] if f["category"] == category]


def system_prompt(config: dict) -> str:
    cats = "\n".join(f"- {c['name']}: {c['hint']}" for c in config["categories"])
    detail_lines = []
    for c in config["categories"]:
        fs = fields_of(config, c["name"])
        if not fs:
            detail_lines.append(f"- {c['name']}: 없음 (모두 빈 문자열)")
            continue
        parts = []
        for f in fs:
            if f["kind"] == "choice":
                parts.append(f"{f['label']}({f['key']}): {', '.join(f['choices'])} 중 하나")
            else:
                parts.append(f"{f['label']}({f['key']})")
        detail_lines.append(f"- {c['name']}: " + " / ".join(parts))
    return (
        "사용자가 나중에 다시 쓰려고 저장한 링크를 정리한다. 주어진 정보만 근거로 아래를 한국어로 만든다.\n"
        "category: 다음 중 하나만 고른다. 형식(영상·이미지)이나 주제가 아니라, 저장한 사람이 나중에 이 링크로 할 일"
        "(가 보기, 사기, 생활에서 따라 하기, 일·공부에 쓰기, 참고하기, 혜택 쓰기)로 고른다. "
        "할 일 없이 읽거나 보고 끝나는 것만 콘텐츠·아티클로 고른다. 제목보다 실제 내용을 따른다.\n"
        f"{cats}\n"
        "alt_category: 두 카테고리에 걸치거나 분류가 애매하면 두 번째 후보. 애매하지 않으면 빈 문자열.\n"
        "summary: 무엇에 관한 링크인지 두 문장 이내. 정보가 부족하면 아는 만큼만 쓰고 지어내지 않는다.\n"
        "keywords: 나중에 검색할 때 쓸 핵심 주제어 3~5개. 고유명사(가게, 장소, 제품, 사람 이름)를 우선한다.\n"
        "details: 고른 category의 필드만 채우고 나머지 필드는 빈 문자열로 둔다. "
        "주어진 정보에 근거가 없으면 빈 문자열로 두고 추측하지 않는다.\n"
        + "\n".join(detail_lines)
    )


def user_prompt(item: dict) -> str:
    fields = [("URL", item["url"]), ("제목", item["title"]), ("작성자", item["author"]),
              ("설명", item["description"]), ("본문", item["body"]), ("사용자 메모", item["memo"])]
    return "\n".join(f"{k}: {v}" for k, v in fields if v)


def output_schema(config: dict) -> dict:
    names = [c["name"] for c in config["categories"]]
    detail_props = {}
    for f in config["detail_fields"]:
        detail_props[f["key"]] = ({"type": "string", "enum": f["choices"] + [""]} if f["kind"] == "choice"
                                  else {"type": "string"})
    return {
        "type": "object",
        "properties": {
            "category": {"type": "string", "enum": names},
            "alt_category": {"type": "string", "enum": names + [""]},
            "summary": {"type": "string"},
            "keywords": {"type": "array", "items": {"type": "string"}},
            "details": {"type": "object", "properties": detail_props,
                        "required": list(detail_props), "additionalProperties": False},
        },
        "required": ["category", "alt_category", "summary", "keywords", "details"],
        "additionalProperties": False,
    }


def validate_output(data: object, config: dict) -> str:
    """형식이 맞으면 빈 문자열, 아니면 이유."""
    names = {c["name"] for c in config["categories"]}
    if not isinstance(data, dict):
        return "객체가 아님"
    if data.get("category") not in names:
        return f"카테고리 밖: {data.get('category')!r}"
    alt = data.get("alt_category", "")
    if alt not in names | {""} or alt == data["category"]:
        return f"alt_category 잘못됨: {alt!r}"
    if not isinstance(data.get("summary"), str) or not data["summary"].strip():
        return "요약 없음"
    kw = data.get("keywords")
    if not isinstance(kw, list) or not kw or not all(isinstance(k, str) for k in kw):
        return "주제어 없음"
    details = data.get("details")
    if not isinstance(details, dict):
        return "details 없음"
    for f in config["detail_fields"]:
        v = details.get(f["key"])
        if not isinstance(v, str):
            return f"details.{f['key']} 없음"
        if f["kind"] == "choice" and v and v not in f["choices"]:
            return f"details.{f['key']} 선택지 밖: {v!r}"
    return ""


# ---------- 채점 ----------

LEVEL_NAMES = {"auto": "자동 정리", "partial": "부분 자동", "memo": "링크 + 메모"}


def _norm(s: str) -> str:
    return re.sub(r"[\s·\-_/]", "", s).lower()


def keyword_hits(labels: list[str], predicted: list[str]) -> int:
    """정답 주제어 중 예측 주제어와 겹치는 수. 공백·기호를 지우고 한쪽이 다른 쪽을 포함하면 맞은 것으로 본다."""
    preds = [_norm(p) for p in predicted if _norm(p)]
    hits = 0
    for label in labels:
        n = _norm(label)
        if n and any(n in p or p in n for p in preds):
            hits += 1
    return hits


def cost_usd(model: dict, input_tokens: int, output_tokens: int) -> float | None:
    price = model.get("price_per_mtok")
    if not price:
        return None
    return (input_tokens * price["input"] + output_tokens * price["output"]) / 1_000_000


def summarize(model: dict, links: dict[str, Link], rows: list[dict], config: dict) -> dict:
    """모델 하나의 결과 행을 집계한다. 정답 라벨이 없는 링크는 정확도에서 뺀다.

    상세 정보는 정답 카테고리의 필드만 본다. (카테고리, 필드, 수집 수준)마다
    label_n: 정답이 있는 수, filled: 그중 모델이 채운 수, correct: 그중 정답과 같은 수(공백·기호 무시),
    empty_n: 정답이 빈 수, false_fill: 그중 모델이 채운 수.
    """
    by_level: dict[str, list[int]] = {}
    details: dict[tuple, dict] = {}
    total = {"n": len(rows), "ok": 0, "format_errors": 0, "api_errors": 0,
             "cat_n": 0, "cat_hit": 0, "kw_label": 0, "kw_hit": 0,
             "alt_n": 0, "alt_rescue": 0, "wrong": [],
             "input_tokens": 0, "output_tokens": 0, "latency_s": 0.0}
    for r in rows:
        total["input_tokens"] += r.get("input_tokens", 0)
        total["output_tokens"] += r.get("output_tokens", 0)
        total["latency_s"] += r.get("latency_s", 0.0)
        if r.get("api_error"):
            total["api_errors"] += 1
            continue
        if r.get("format_error"):
            total["format_errors"] += 1
            continue
        total["ok"] += 1
        out = r["output"]
        link = links[r["id"]]
        if out.get("alt_category"):
            total["alt_n"] += 1
        if link.label_keywords:
            total["kw_label"] += len(link.label_keywords)
            total["kw_hit"] += keyword_hits(link.label_keywords, out["keywords"])
        if not link.label_category:
            continue
        hit = int(out["category"] == link.label_category)
        total["cat_n"] += 1
        total["cat_hit"] += hit
        by_level.setdefault(link.level, []).append(hit)
        if not hit:
            total["wrong"].append((link.id, link.label_category, out["category"], out.get("alt_category", "")))
            if out.get("alt_category") == link.label_category:
                total["alt_rescue"] += 1
        for f in fields_of(config, link.label_category):
            st = details.setdefault((link.label_category, f["key"], link.level),
                                    {"label_n": 0, "filled": 0, "correct": 0, "empty_n": 0, "false_fill": 0})
            label = link.label_details.get(f["key"], "")
            pred = out["details"].get(f["key"], "").strip()
            if label:
                st["label_n"] += 1
                if pred:
                    st["filled"] += 1
                    st["correct"] += int(_norm(pred) == _norm(label))
            else:
                st["empty_n"] += 1
                st["false_fill"] += int(bool(pred))
    total["cost_usd"] = cost_usd(model, total["input_tokens"], total["output_tokens"])
    total["by_level"] = {lv: (sum(h), len(h)) for lv, h in by_level.items()}
    total["details"] = details
    return total


def pct(hit: int, n: int) -> str:
    return f"{hit / n * 100:.0f}% ({hit}/{n})" if n else "—"


def render_tables(summaries: list[tuple[dict, dict]], config: dict) -> str:
    out = ["## 전체", "",
           "| 모델 | 성공 | 형식 오류 | API 오류 | 카테고리 정확도 | 주제어 재현율 | 두 번째 후보 | 입력 토큰 | 출력 토큰 | 비용(USD) | 평균 지연(s) |",
           "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
    for model, s in summaries:
        cost = "단가 미확인" if s["cost_usd"] is None else f"{s['cost_usd']:.4f}"
        latency = f"{s['latency_s'] / s['n']:.1f}" if s["n"] else "—"
        out.append(f"| {model['id']} | {s['ok']}/{s['n']} | {s['format_errors']} | {s['api_errors']} | "
                   f"{pct(s['cat_hit'], s['cat_n'])} | {pct(s['kw_hit'], s['kw_label'])} | {pct(s['alt_n'], s['ok'])} | "
                   f"{s['input_tokens']} | {s['output_tokens']} | {cost} | {latency} |")

    out += ["", "## 수집 수준별 카테고리 정확도", "", "| 모델 | " + " | ".join(LEVEL_NAMES.values()) + " |",
            "| --- | --- | --- | --- |"]
    for model, s in summaries:
        cells = [pct(*s["by_level"].get(lv, (0, 0))) for lv in LEVEL_NAMES]
        out.append(f"| {model['id']} | " + " | ".join(cells) + " |")

    out += ["", "## 카테고리 × 필드 × 수집 수준",
            "", "칸마다 추출률 / 정확도 / 헛채움률. 추출률·정확도는 정답이 있는 링크 기준, 헛채움률은 정답이 빈 링크 기준. "
            "자유 텍스트 필드의 정확도는 공백·기호만 무시한 자동 비교라 review CSV로 사람이 확인한다.", "",
            "| 모델 | 카테고리 | 필드 | " + " | ".join(LEVEL_NAMES.values()) + " |",
            "| --- | --- | --- | --- | --- | --- |"]
    for model, s in summaries:
        for f in config["detail_fields"]:
            cells = []
            for lv in LEVEL_NAMES:
                st = s["details"].get((f["category"], f["key"], lv))
                if not st:
                    cells.append("—")
                    continue
                cells.append(f"{pct(st['filled'], st['label_n'])} / {pct(st['correct'], st['label_n'])} / "
                             f"{pct(st['false_fill'], st['empty_n'])}")
            out.append(f"| {model['id']} | {f['category']} | {f['label']} | " + " | ".join(cells) + " |")

    out += ["", "## 분류가 정답과 다른 사례", "",
            "| 모델 | 링크 | 정답 | 모델 | 두 번째 후보 |", "| --- | --- | --- | --- | --- |"]
    for model, s in summaries:
        for lid, label, pred, alt in s["wrong"]:
            out.append(f"| {model['id']} | {lid} | {label} | {pred} | {alt or '—'} |")
        out.append(f"| {model['id']} | 합계 | 틀린 {len(s['wrong'])}건 중 두 번째 후보가 정답 {s['alt_rescue']}건 | | |")
    return "\n".join(out)


def review_rows(links: dict[str, Link], rows: list[dict], config: dict) -> list[dict]:
    """사람이 볼 표: 요약 점수 칸(빈 칸)과 자유 텍스트 필드의 정답·모델 값."""
    text_keys = [f["key"] for f in config["detail_fields"] if f["kind"] == "text"]
    result = []
    for r in rows:
        if "output" not in r:
            continue
        link, out = links[r["id"]], r["output"]
        row = {"id": r["id"], "label_category": link.label_category, "category": out["category"],
               "alt_category": out.get("alt_category", ""), "summary": out["summary"], "summary_score": "",
               "keywords": "; ".join(out["keywords"])}
        for k in text_keys:
            row[f"label_{k}"] = link.label_details.get(k, "")
            row[f"pred_{k}"] = out["details"].get(k, "")
        result.append(row)
    return result


# ---------- 모델 호출 ----------

def call_model(model: dict, system: str, user: str, schema: dict) -> tuple[str, int, int]:
    """(응답 텍스트, 입력 토큰, 출력 토큰). SDK는 쓰는 제공자만 불러온다."""
    provider = model["provider"]
    if provider == "anthropic":
        import anthropic
        client = anthropic.Anthropic()
        kwargs = {"output_config": {"format": {"type": "json_schema", "schema": schema}}}
        if model.get("effort"):
            kwargs["output_config"]["effort"] = model["effort"]
        res = client.messages.create(model=model["id"], max_tokens=2048, system=system,
                                     messages=[{"role": "user", "content": user}], **kwargs)
        if res.stop_reason == "refusal":
            raise RuntimeError("refusal")
        text = next(b.text for b in res.content if b.type == "text")
        return text, res.usage.input_tokens, res.usage.output_tokens
    if provider == "openai":
        # 처음 실행할 때 SDK 문서와 맞는지 확인한다 (docs/llm-evaluation.md "확인하지 못한 것").
        from openai import OpenAI
        client = OpenAI()
        res = client.responses.create(
            model=model["id"], instructions=system, input=user,
            text={"format": {"type": "json_schema", "name": "analysis", "schema": schema, "strict": True}},
        )
        return res.output_text, res.usage.input_tokens, res.usage.output_tokens
    if provider == "gemini":
        # 처음 실행할 때 SDK 문서와 맞는지 확인한다.
        from google import genai
        client = genai.Client()
        res = client.models.generate_content(
            model=model["id"], contents=user,
            config={"system_instruction": system, "response_mime_type": "application/json",
                    "response_json_schema": schema},
        )
        u = res.usage_metadata
        return res.text, u.prompt_token_count, (u.candidates_token_count or 0) + (u.thoughts_token_count or 0)
    raise ValueError(f"모르는 제공자: {provider}")


def run_one(model: dict, item: dict, config: dict, call=call_model) -> dict:
    row = {"id": item["id"], "model": model["id"]}
    started = time.monotonic()
    try:
        text, tin, tout = call(model, system_prompt(config), user_prompt(item), output_schema(config))
    except Exception as e:  # noqa: BLE001 - 한 건의 실패는 기록하고 다음으로 간다
        row.update(api_error=f"{type(e).__name__}: {e}", latency_s=time.monotonic() - started)
        return row
    row.update(latency_s=round(time.monotonic() - started, 2), input_tokens=tin, output_tokens=tout, raw=text)
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        row["format_error"] = "JSON 아님"
        return row
    reason = validate_output(data, config)
    if reason:
        row["format_error"] = reason
    else:
        row["output"] = data
    return row


# 요청 한도(429)와 제공자 쪽 일시 과부하(503, Anthropic 529)는 기다리면 풀린다.
RETRYABLE_MARKERS = ("429", "RESOURCE_EXHAUSTED", "rate_limit", "RateLimit",
                     "503", "UNAVAILABLE", "529", "overloaded")


def is_retryable(error: str) -> bool:
    return any(m in error for m in RETRYABLE_MARKERS)


def retry_delay_s(error: str, default: float = 60.0, cap: float = 120.0) -> float:
    """제공자가 알려 준 대기 시간(Gemini의 retryDelay 등)을 쓰고, 없으면 기본값을 쓴다."""
    m = re.search(r"retryDelay'?\"?:\s*'?\"?(\d+(?:\.\d+)?)s", error)
    return min(float(m.group(1)) + 1, cap) if m else default


def run_with_retry(model: dict, item: dict, config: dict, call=call_model, sleep=time.sleep,
                   max_retries: int = 5) -> dict:
    """요청 한도나 일시 과부하면 기다렸다가 다시 부른다. 다른 오류는 바로 돌려준다."""
    row = run_one(model, item, config, call=call)
    for _ in range(max_retries):
        error = row.get("api_error", "")
        if not is_retryable(error):
            break
        wait = retry_delay_s(error)
        print(f"{model['id']} {item['id']} {error[:40]}…, {wait:.0f}초 뒤 다시 부른다")
        sleep(wait)
        row = run_one(model, item, config, call=call)
    return row


def write_rows(path: Path, rows: list[dict]) -> None:
    path.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")


def load_done_rows(path: Path) -> dict[str, dict]:
    """이전 결과에서 API 오류 없이 끝난 행(형식 오류 포함)만 링크 ID별로 돌려준다."""
    if not path.exists():
        return {}
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    return {r["id"]: r for r in rows if not r.get("api_error")}


# ---------- 명령 ----------

def cmd_keys(args, config) -> int:
    models = select_models(config, args.models)
    missing = missing_keys(models)
    if missing:
        print("다음 환경변수가 비어 있다: " + ", ".join(missing))
        print("셸에서 export 한 뒤 다시 실행하거나, --models 로 키가 있는 모델만 고른다.")
        return 2
    print("필요한 키 환경변수가 모두 있다: " + ", ".join(sorted({m['key_env'] for m in models})))
    return 0


def cmd_fetch(args, config) -> int:
    CACHE_DIR.mkdir(exist_ok=True)
    if not os.environ.get("YOUTUBE_API_KEY"):
        print("YOUTUBE_API_KEY가 비어 있어 유튜브는 제목·채널만 넣는다.")
    for link in load_links():
        item = build_input(link, body_max_chars=config["body_max_chars"])
        (CACHE_DIR / f"{link.id}.json").write_text(json.dumps(item, ensure_ascii=False, indent=1), encoding="utf-8")
        status = f"오류: {item['error']}" if item["error"] else "ok"
        print(f"{link.id} {link.platform:<10} 본문 {len(item['body']):>5}자 {status}")
    return 0


def cmd_run(args, config) -> int:
    models = select_models(config, args.models)
    missing = missing_keys(models)
    if missing:
        print("다음 환경변수가 비어 있어 실행하지 않는다: " + ", ".join(missing))
        return 2
    items = [json.loads((CACHE_DIR / f"{l.id}.json").read_text(encoding="utf-8")) for l in load_links()
             if (CACHE_DIR / f"{l.id}.json").exists()]
    if not items:
        print("입력이 없다. 먼저 `python3 eval.py fetch`를 실행한다.")
        return 2
    OUT_DIR.mkdir(exist_ok=True)
    for model in models:
        path = OUT_DIR / f"{model['id']}.jsonl"
        done = {} if getattr(args, "fresh", False) else load_done_rows(path)
        if done:
            print(f"{model['id']}: 이전 결과 {len(done)}건은 다시 부르지 않는다 (--fresh로 전부 다시)")
        rows = []
        for item in items:
            if item["id"] in done:
                rows.append(done[item["id"]])
                continue
            row = run_with_retry(model, item, config)
            rows.append(row)
            print(f"{model['id']} {item['id']} {row.get('api_error') or row.get('format_error') or 'ok'}")
            # 한 건마다 저장해, 중간에 멈춰도 이전 결과와 지금까지의 결과가 남게 한다.
            rest = [done[i["id"]] for i in items[len(rows):] if i["id"] in done]
            write_rows(path, rows + rest)
        write_rows(path, rows)
    return 0


def cmd_score(args, config) -> int:
    links = {l.id: l for l in load_links()}
    summaries = []
    for model in config["models"]:
        path = OUT_DIR / f"{model['id']}.jsonl"
        if not path.exists():
            continue
        rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
        summaries.append((model, summarize(model, links, rows, config)))
        review = review_rows(links, rows, config)
        if review:
            with (OUT_DIR / f"review-{model['id']}.csv").open("w", encoding="utf-8", newline="") as f:
                w = csv.DictWriter(f, fieldnames=list(review[0]))
                w.writeheader()
                w.writerows(review)
    if not summaries:
        print("out/에 결과가 없다. 먼저 `python3 eval.py run`을 실행한다.")
        return 2
    print(render_tables(summaries, config))
    print("\n요약 점수(1~5)와 자유 텍스트 필드는 out/review-<모델>.csv에서 사람이 확인한다.")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    for name in ("keys", "run"):
        p = sub.add_parser(name)
        p.add_argument("--models", help="쉼표로 구분한 모델 ID. 없으면 config.json의 전체")
        if name == "run":
            p.add_argument("--fresh", action="store_true", help="이전 결과를 무시하고 전부 다시 부른다")
    sub.add_parser("fetch")
    sub.add_parser("score")
    args = parser.parse_args(argv)
    config = load_config()
    return {"keys": cmd_keys, "fetch": cmd_fetch, "run": cmd_run, "score": cmd_score}[args.cmd](args, config)


if __name__ == "__main__":
    sys.exit(main())
