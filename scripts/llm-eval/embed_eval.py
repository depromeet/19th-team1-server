#!/usr/bin/env python3
"""임베딩 검색 품질 실험 (#33).

#25 링크 세트의 분석 결과(요약·주제어)를 임베딩해 두고, 질문 10개가 상위 5개 안에 정답 저장물을
찾는지 본다. 임베딩 대상(요약만 / 제목+요약+주제어), 질문 말투(검색어 / 채팅), 출력 차원(768 / 3072)을
비교하고, '관련 없음' 질문 3개로 유사도 기준값을 정한다. 결과 해석과 실행 방법은 docs/llm-evaluation.md 9절에 있다.

    python3 embed_eval.py snapshot   #25 결과(.cache/, out/)로 검색 대상 CSV를 만든다. 키 필요 없음
    python3 embed_eval.py run        임베딩을 받아 .cache/embeddings/에 저장. 받은 문장은 다시 부르지 않는다
    python3 embed_eval.py score      표 출력, out/embedding-detail.csv 저장

API 키는 환경변수(GEMINI_API_KEY)로만 읽고, 값은 출력하지 않는다.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
import sys
import time
from dataclasses import dataclass
from pathlib import Path

import eval as ev

DOCS_CSV = ev.ROOT / "docs" / "llm-evaluation" / "embedding-docs.csv"
QUERIES_CSV = ev.ROOT / "docs" / "llm-evaluation" / "embedding-queries.csv"
EMBED_CACHE_DIR = ev.CACHE_DIR / "embeddings"
DETAIL_CSV = ev.OUT_DIR / "embedding-detail.csv"
SOURCE_MODEL = "gemini-3.5-flash-lite"  # 검색 대상의 요약·주제어를 만든 #25 모델

MODEL = "gemini-embedding-2"
KEY_ENV = "GEMINI_API_KEY"
DIMS = (768, 3072)
TARGETS = ("summary", "full")
TARGET_NAMES = {"summary": "요약만", "full": "제목+요약+주제어"}
STYLES = ("keyword", "chat")
STYLE_NAMES = {"keyword": "검색어", "chat": "채팅"}
TOP_K = 5
PASS_HITS = 8
CHUNK = 50  # 한 번에 보내는 문장 수


# ---------- 검색 대상과 질문 ----------

@dataclass
class Doc:
    id: str
    platform: str
    level: str
    title: str
    summary: str
    keywords: list[str]


@dataclass
class Query:
    id: str
    kind: str  # relevant | irrelevant
    keyword_query: str
    chat_query: str
    gold_ids: list[str]


def snapshot_rows(cache_dir: Path, out_path: Path) -> list[dict]:
    """#25 입력 캐시의 제목과 모델 결과의 요약·주제어를 한 줄씩 모은다. 결과가 없거나 오류면 비워 둔다."""
    outputs = {}
    for line in out_path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            row = json.loads(line)
            outputs[row["id"]] = row.get("output") or {}
    rows = []
    for path in sorted(cache_dir.glob("L*.json")):
        item = json.loads(path.read_text(encoding="utf-8"))
        out = outputs.get(item["id"], {})
        rows.append({
            "id": item["id"],
            "platform": item["platform"],
            "level": item["level"],
            "title": (item.get("title") or "").strip(),
            "summary": (out.get("summary") or "").strip(),
            "keywords": ";".join(k.strip() for k in out.get("keywords") or [] if k.strip()),
        })
    return rows


def load_docs(path: Path = DOCS_CSV) -> list[Doc]:
    with path.open(encoding="utf-8", newline="") as f:
        return [Doc(id=r["id"], platform=r["platform"], level=r["level"], title=r["title"], summary=r["summary"],
                    keywords=ev.split_keywords(r["keywords"])) for r in csv.DictReader(f)]


def load_queries(path: Path = QUERIES_CSV) -> list[Query]:
    with path.open(encoding="utf-8", newline="") as f:
        return [Query(id=r["id"], kind=r["kind"], keyword_query=r["keyword_query"].strip(),
                      chat_query=r["chat_query"].strip(), gold_ids=ev.split_keywords(r["gold_ids"]))
                for r in csv.DictReader(f)]


def check_gold(queries: list[Query], docs: list[Doc]) -> None:
    """정답이 검색 대상에 있고, 두 임베딩 대상 모두에서 문장이 있어야 한다."""
    by_id = {d.id: d for d in docs}
    for q in queries:
        for g in q.gold_ids:
            if g not in by_id:
                raise ValueError(f"{q.id}: 검색 대상에 없는 정답 {g}")
            if any(doc_text(by_id[g], t) is None for t in TARGETS):
                raise ValueError(f"{q.id}: 정답 {g}의 임베딩 문장이 비어 있다")


# ---------- 임베딩 문장 ----------
# gemini-embedding-2는 task_type 대신 접두어를 쓴다(https://aistudio.google.com/docs/embeddings "Task types with Embeddings 2").

def doc_text(doc: Doc, target: str) -> str | None:
    """임베딩할 저장물 문장. 내용이 없으면 None(검색 대상에서 뺀다)."""
    if target == "summary":
        return f"title: none | text: {doc.summary}" if doc.summary else None
    if target == "full":
        if not (doc.title or doc.summary or doc.keywords):
            return None
        text = doc.summary
        if doc.keywords:
            text = f"{text} 주제어: {', '.join(doc.keywords)}".strip()
        return f"title: {doc.title or 'none'} | text: {text}"
    raise ValueError(f"모르는 임베딩 대상: {target}")


def query_text(q: Query, style: str) -> str:
    return f"task: search result | query: {q.keyword_query if style == 'keyword' else q.chat_query}"


def all_texts(docs: list[Doc], queries: list[Query]) -> list[str]:
    texts = [t for d in docs for tg in TARGETS if (t := doc_text(d, tg)) is not None]
    texts += [query_text(q, s) for q in queries for s in STYLES]
    return list(dict.fromkeys(texts))


# ---------- 계산 ----------

def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    return dot / (math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b)))


def truncate_normalize(vec: list[float], dim: int) -> list[float]:
    head = vec[:dim]
    norm = math.sqrt(sum(x * x for x in head))
    return [x / norm for x in head]


def rank(query_vec: list[float], doc_vecs: dict[str, list[float]]) -> list[tuple[str, float]]:
    return sorted(((i, cosine(query_vec, v)) for i, v in doc_vecs.items()), key=lambda p: -p[1])


@dataclass
class QueryScore:
    hit: bool
    gold_in_top: int
    gold_total: int
    first_rank: int
    min_gold_sim: float
    top1_sim: float
    gold_top_sims: list[float]  # 상위 k개 안에 든 정답들의 유사도
    top: list[tuple[str, float]]


def score_query(ranking: list[tuple[str, float]], gold_ids: list[str], k: int = TOP_K) -> QueryScore:
    """적중: 상위 k개 안에 정답이 하나라도 있다. 첫 정답 순위와 정답 최저 유사도는 전체 순위에서 본다."""
    ranks = {i: n for n, (i, _) in enumerate(ranking, start=1)}
    sims = dict(ranking)
    top = ranking[:k]
    gold_top_sims = [s for i, s in top if i in gold_ids]
    return QueryScore(
        hit=bool(gold_top_sims),
        gold_in_top=len(gold_top_sims),
        gold_total=len(gold_ids),
        first_rank=min(ranks[g] for g in gold_ids),
        min_gold_sim=min(sims[g] for g in gold_ids),
        top1_sim=ranking[0][1],
        gold_top_sims=gold_top_sims,
        top=top,
    )


def hit_after_threshold(score: QueryScore, threshold: float) -> bool:
    """기준값 이하 결과를 버린 뒤에도 상위 k개 안에 정답이 남는가."""
    return any(s > threshold for s in score.gold_top_sims)


def irrelevant_threshold(rankings: dict[str, list[tuple[str, float]]]) -> float:
    """관련 없음 질문들이 받은 1위 유사도 중 가장 큰 값."""
    return max(r[0][1] for r in rankings.values())


# ---------- 임베딩 호출과 캐시 ----------

class EmbeddingCache:
    """문장 → 벡터. 모델·차원마다 파일 하나. 묶음 하나를 받을 때마다 저장해 중간에 멈춰도 남는다."""

    def __init__(self, root: Path, model: str, dim: int):
        self.path = root / f"{model}-{dim}.json"
        self.dim = dim

    def load(self) -> dict[str, list[float]]:
        if not self.path.exists():
            return {}
        return {e["text"]: e["values"] for e in json.loads(self.path.read_text(encoding="utf-8")).values()}

    def _save(self, vecs: dict[str, list[float]]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        data = {hashlib.sha256(t.encode()).hexdigest(): {"text": t, "values": v} for t, v in vecs.items()}
        self.path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

    def embed(self, texts: list[str], embed_fn, chunk: int = CHUNK, on_call=None) -> dict[str, list[float]]:
        vecs = self.load()
        missing = [t for t in dict.fromkeys(texts) if t not in vecs]
        for start in range(0, len(missing), chunk):
            part = missing[start:start + chunk]
            got = embed_fn(part, self.dim)
            if len(got) != len(part):
                raise RuntimeError(f"문장 {len(part)}개를 보냈는데 벡터 {len(got)}개가 왔다")
            vecs.update(zip(part, got))
            self._save(vecs)
            if on_call:
                on_call(len(part))
        return {t: vecs[t] for t in texts}


def with_retry(call, sleep=time.sleep, max_retries: int = 5):
    """요청 한도(429)나 일시 과부하(503)면 제공자가 알려 준 시간만큼 기다렸다가 다시 부른다."""
    def wrapped(texts, dim):
        for attempt in range(max_retries + 1):
            try:
                return call(texts, dim)
            except Exception as e:  # SDK 예외 종류에 묶이지 않게 메시지로 판단한다
                error = f"{type(e).__name__}: {e}"
                if attempt == max_retries or not ev.is_retryable(error):
                    raise
                wait = ev.retry_delay_s(error)
                print(f"{error[:60]}…, {wait:.0f}초 뒤 다시 부른다")
                sleep(wait)
    return wrapped


_client = None


def gemini_embed(texts: list[str], dim: int) -> list[list[float]]:
    """문장마다 Content를 따로 넣어야 벡터를 따로 받는다(parts로 넣으면 하나로 합쳐진다)."""
    global _client
    from google import genai
    from google.genai import types
    if _client is None:
        _client = genai.Client()
    res = _client.models.embed_content(
        model=MODEL,
        contents=[types.Content(parts=[types.Part.from_text(text=t)]) for t in texts],
        config=types.EmbedContentConfig(output_dimensionality=dim),
    )
    return [list(e.values) for e in res.embeddings]


def require_key(environ=os.environ, print_=print) -> int:
    if environ.get(KEY_ENV):
        print_(f"{KEY_ENV} 있음")
        return 0
    print_(f"환경변수 {KEY_ENV}가 비어 있다. 셸에서 export 한 뒤 다시 실행한다.")
    return 2


# ---------- 채점 ----------

@dataclass
class Result:
    dim: int
    target: str
    style: str
    threshold: float
    relevant: dict[str, QueryScore]
    irrelevant: dict[str, list[tuple[str, float]]]
    pool: int

    @property
    def hits(self) -> int:
        return sum(s.hit for s in self.relevant.values())

    @property
    def hits_after(self) -> int:
        return sum(hit_after_threshold(s, self.threshold) for s in self.relevant.values())

    @property
    def mean_recall(self) -> float:
        return sum(s.gold_in_top / s.gold_total for s in self.relevant.values()) / len(self.relevant)


def evaluate(docs: list[Doc], queries: list[Query], vecs: dict[str, list[float]], dim: int, target: str,
             style: str) -> Result:
    doc_vecs = {d.id: vecs[t] for d in docs if (t := doc_text(d, target)) is not None}
    relevant, irrelevant = {}, {}
    for q in queries:
        ranking = rank(vecs[query_text(q, style)], doc_vecs)
        if q.kind == "relevant":
            relevant[q.id] = score_query(ranking, q.gold_ids)
        else:
            irrelevant[q.id] = ranking
    return Result(dim, target, style, irrelevant_threshold(irrelevant), relevant, irrelevant, len(doc_vecs))


def render(results: list[Result]) -> str:
    lines = ["## 요약", "",
             f"| 차원 | 임베딩 대상 | 질문 말투 | 적중 (상위 {TOP_K}) | 통과 | 평균 정답 재현율 | 기준값 T | T 적용 뒤 적중 | 검색 대상 수 |",
             "| --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
    for r in results:
        n = len(r.relevant)
        lines.append(f"| {r.dim} | {TARGET_NAMES[r.target]} | {STYLE_NAMES[r.style]} | {r.hits}/{n} "
                     f"| {'통과' if r.hits >= PASS_HITS else '미달'} | {r.mean_recall:.0%} | {r.threshold:.3f} "
                     f"| {r.hits_after}/{n} | {r.pool} |")
    for r in results:
        lines += ["", f"## {r.dim}차원 · {TARGET_NAMES[r.target]} · {STYLE_NAMES[r.style]} (T={r.threshold:.3f})", "",
                  "| 질문 | 적중 | 정답 재현율 | 첫 정답 순위 | 정답 최저 유사도 | T | 상위 5개 |",
                  "| --- | --- | --- | --- | --- | --- | --- |"]
        for qid, s in r.relevant.items():
            top = ", ".join(f"{i} {sim:.3f}" for i, sim in s.top)
            lines.append(f"| {qid} | {'O' if s.hit else 'X'} | {s.gold_in_top}/{s.gold_total} | {s.first_rank} "
                         f"| {s.min_gold_sim:.3f} | {r.threshold:.3f} | {top} |")
        for qid, ranking in r.irrelevant.items():
            top = ", ".join(f"{i} {sim:.3f}" for i, sim in ranking[:TOP_K])
            lines.append(f"| {qid} (관련 없음) | — | — | — | — | 1위 {ranking[0][1]:.3f} | {top} |")
    return "\n".join(lines)


def detail_rows(results: list[Result], queries: list[Query]) -> list[dict]:
    by_id = {q.id: q for q in queries}
    rows = []
    for r in results:
        base = {"dim": r.dim, "target": r.target, "style": r.style, "threshold": f"{r.threshold:.4f}"}
        for qid, s in r.relevant.items():
            q = by_id[qid]
            rows.append({**base, "query_id": qid, "kind": "relevant",
                         "query": q.keyword_query if r.style == "keyword" else q.chat_query,
                         "gold_ids": ";".join(q.gold_ids), "hit": int(s.hit),
                         "hit_after_threshold": int(hit_after_threshold(s, r.threshold)),
                         "gold_in_top": s.gold_in_top, "gold_total": s.gold_total, "first_rank": s.first_rank,
                         "min_gold_sim": f"{s.min_gold_sim:.4f}", "top1_sim": f"{s.top1_sim:.4f}",
                         "top": ";".join(f"{i}:{sim:.4f}" for i, sim in s.top)})
        for qid, ranking in r.irrelevant.items():
            q = by_id[qid]
            rows.append({**base, "query_id": qid, "kind": "irrelevant",
                         "query": q.keyword_query if r.style == "keyword" else q.chat_query,
                         "gold_ids": "", "hit": "", "hit_after_threshold": "", "gold_in_top": "", "gold_total": "",
                         "first_rank": "", "min_gold_sim": "", "top1_sim": f"{ranking[0][1]:.4f}",
                         "top": ";".join(f"{i}:{sim:.4f}" for i, sim in ranking[:TOP_K])})
    return rows


def truncation_check(v768: dict[str, list[float]], v3072: dict[str, list[float]]) -> tuple[int, float]:
    """768차원 응답이 3072차원 응답의 앞 768개를 정규화한 것과 같은지. (비교한 문장 수, 최저 코사인)"""
    common = [t for t in v768 if t in v3072]
    if not common:
        return 0, float("nan")
    return len(common), min(cosine(v768[t], truncate_normalize(v3072[t], 768)) for t in common)


# ---------- 명령 ----------

def cmd_snapshot(args) -> int:
    out = ev.OUT_DIR / f"{SOURCE_MODEL}.jsonl"
    if not out.exists() or not ev.CACHE_DIR.exists():
        print(f"#25 결과가 없다: {out} 와 {ev.CACHE_DIR} 가 필요하다 (eval.py fetch, run)")
        return 2
    rows = snapshot_rows(ev.CACHE_DIR, out)
    with DOCS_CSV.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    print(f"{DOCS_CSV.relative_to(ev.ROOT)}: {len(rows)}개")
    return 0


def load_checked() -> tuple[list[Doc], list[Query]]:
    docs, queries = load_docs(), load_queries()
    check_gold(queries, docs)
    return docs, queries


def cmd_run(args) -> int:
    if require_key() != 0:
        return 2
    docs, queries = load_checked()
    texts = all_texts(docs, queries)
    calls = []
    for dim in args.dims:
        cache = EmbeddingCache(EMBED_CACHE_DIR, MODEL, dim)
        before = len(cache.load())
        cache.embed(texts, with_retry(gemini_embed), chunk=args.chunk, on_call=calls.append)
        print(f"{dim}차원: 문장 {len(texts)}개, 이미 있던 것 {min(before, len(texts))}개")
    print(f"호출 {len(calls)}회 (묶음 크기 {args.chunk})")
    return 0


def cmd_score(args) -> int:
    docs, queries = load_checked()
    texts = all_texts(docs, queries)
    results, by_dim = [], {}
    for dim in args.dims:
        vecs = EmbeddingCache(EMBED_CACHE_DIR, MODEL, dim).load()
        missing = [t for t in texts if t not in vecs]
        if missing:
            print(f"{dim}차원 임베딩이 {len(missing)}개 없다. run을 먼저 실행한다")
            return 2
        by_dim[dim] = vecs
        results += [evaluate(docs, queries, vecs, dim, t, s) for t in TARGETS for s in STYLES]
    print(render(results))
    if 768 in by_dim and 3072 in by_dim:
        n, worst = truncation_check({t: by_dim[768][t] for t in texts}, by_dim[3072])
        print(f"\n768차원 응답 vs 3072차원 앞 768개 정규화: 문장 {n}개, 최저 코사인 {worst:.6f}")
    rows = detail_rows(results, queries)
    ev.OUT_DIR.mkdir(exist_ok=True)
    with DETAIL_CSV.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    print(f"\n질문별 상세: {DETAIL_CSV.relative_to(ev.ROOT)}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("snapshot")
    for name in ("run", "score"):
        p = sub.add_parser(name)
        p.add_argument("--dims", type=lambda s: [int(x) for x in s.split(",")], default=list(DIMS),
                       help="쉼표로 구분한 출력 차원. 기본 768,3072")
        if name == "run":
            p.add_argument("--chunk", type=int, default=CHUNK, help=f"한 번에 보내는 문장 수. 기본 {CHUNK}")
    args = parser.parse_args(argv)
    return {"snapshot": cmd_snapshot, "run": cmd_run, "score": cmd_score}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
