"""eval.py에서 키 없이 돌릴 수 있는 부분(입력 만들기, 형식 검사, 채점, 집계)의 시험. 네트워크를 쓰지 않는다.

    python3 -m unittest -v test_eval.py
"""

import json
import unittest

import eval as ev

CATS = ev.load_config()["categories"]


def link(platform, memo="", cat="", kw=""):
    return ev.Link(id="L01", platform=platform, url="https://example.com/a", memo=memo,
                   label_category=cat, label_keywords=ev.split_keywords(kw))


class KeysTest(unittest.TestCase):
    def test_missing_keys_lists_only_names(self):
        models = [{"key_env": "ANTHROPIC_API_KEY"}, {"key_env": "OPENAI_API_KEY"}, {"key_env": "ANTHROPIC_API_KEY"}]
        self.assertEqual(ev.missing_keys(models, {"OPENAI_API_KEY": "x"}), ["ANTHROPIC_API_KEY"])

    def test_empty_value_counts_as_missing(self):
        self.assertEqual(ev.missing_keys([{"key_env": "K"}], {"K": ""}), ["K"])

    def test_run_stops_without_keys(self):
        config = {"models": [{"id": "m", "key_env": "LLM_EVAL_TEST_UNSET_KEY"}], "categories": CATS}

        class Args:
            models = None

        self.assertEqual(ev.cmd_run(Args, config), 2)


class InputTest(unittest.TestCase):
    PAGE = """<html><head><title>T</title><meta property="og:title" content="OG 제목">
    <meta name="description" content="설명"><script>var x=1</script></head>
    <body><nav>메뉴</nav><p>본문   첫 줄</p><p>둘째</p></body></html>"""

    def test_parse_page_prefers_og_and_skips_scripts(self):
        page = ev.parse_page(self.PAGE)
        self.assertEqual(page["title"], "OG 제목")
        self.assertEqual(page["description"], "설명")
        self.assertEqual(page["body"], "본문 첫 줄 둘째")

    def test_naver_blog_urls_become_mobile(self):
        self.assertEqual(ev.naver_blog_mobile_url("https://blog.naver.com/abc/223000000001"),
                         "https://m.blog.naver.com/abc/223000000001")
        self.assertEqual(ev.naver_blog_mobile_url("https://blog.naver.com/PostView.naver?blogId=abc&logNo=22"),
                         "https://m.blog.naver.com/abc/22")

    def test_memo_level_uses_memo_without_fetching(self):
        def no_network(url):
            raise AssertionError("호출하면 안 됨")

        item = ev.build_input(link("instagram", memo="성수 카페"), get=no_network)
        self.assertEqual((item["memo"], item["error"]), ("성수 카페", ""))

    def test_partial_level_uses_oembed_and_memo(self):
        calls = []

        def fake(url):
            calls.append(url)
            return json.dumps({"title": "핀 제목", "author_name": "브랜드"})

        item = ev.build_input(link("pinterest", memo="거실"), get=fake)
        self.assertTrue(calls[0].startswith(ev.OEMBED["pinterest"]))
        self.assertEqual((item["title"], item["author"], item["memo"]), ("핀 제목", "브랜드", "거실"))

    def test_pinterest_urls_become_pin_id(self):
        self.assertEqual(ev.pinterest_pin_url("https://www.pinterest.com/pin/kimchi-fried-rice--398146423279562352/"),
                         "https://www.pinterest.com/pin/398146423279562352/")
        self.assertEqual(ev.pinterest_pin_url("https://kr.pinterest.com/pin/275845545923386094/"),
                         "https://www.pinterest.com/pin/275845545923386094/")

    def test_user_agent_says_bot(self):
        # 노션 공개 페이지는 봇이라고 밝힌 UA에만 본문을 준다 (11/19 수집 조사)
        self.assertIn("bot", ev.USER_AGENT)

    def test_youtube_uses_oembed_only_without_key(self):
        calls = []

        def fake(url, headers=None):
            calls.append(url)
            return json.dumps({"title": "영상", "author_name": "채널"})

        yt = ev.Link("L01", "youtube", "https://www.youtube.com/watch?v=abc", "", "", [])
        item = ev.build_input(yt, get=fake, environ={})
        self.assertEqual(len(calls), 1)
        self.assertEqual((item["title"], item["description"]), ("영상", ""))

    def test_youtube_description_from_data_api_with_key_in_header(self):
        seen = []

        def fake(url, headers=None):
            seen.append((url, headers or {}))
            if "oembed" in url:
                return json.dumps({"title": "영상", "author_name": "채널"})
            return json.dumps({"items": [{"snippet": {"description": "설명 전체", "tags": ["요리", "찌개"]}}]})

        yt = ev.Link("L01", "youtube", "https://youtu.be/abc?si=x", "", "", [])
        item = ev.build_input(yt, get=fake, environ={"YOUTUBE_API_KEY": "DUMMY-KEY"})
        api_url, api_headers = seen[1]
        self.assertIn("id=abc", api_url)
        self.assertNotIn("DUMMY-KEY", api_url)  # 키는 URL에 넣지 않는다 (오류 메시지·로그로 새지 않게)
        self.assertEqual(api_headers.get("X-Goog-Api-Key"), "DUMMY-KEY")
        self.assertEqual(item["description"], "설명 전체\n태그: 요리, 찌개")

    def test_youtube_video_id(self):
        self.assertEqual(ev.youtube_video_id("https://www.youtube.com/watch?v=DnQ09ZZCjCs&t=3"), "DnQ09ZZCjCs")
        self.assertEqual(ev.youtube_video_id("https://youtu.be/DnQ09ZZCjCs?si=x"), "DnQ09ZZCjCs")
        self.assertEqual(ev.youtube_video_id("https://www.youtube.com/shorts/DnQ09ZZCjCs"), "DnQ09ZZCjCs")

    def test_body_is_truncated_and_marked(self):
        item = ev.build_input(link("blog"), get=lambda u: f"<p>{'가' * 50}</p>", body_max_chars=10)
        self.assertEqual(len(item["body"]), 10)
        self.assertTrue(item["body_truncated"])

    def test_fetch_failure_is_recorded(self):
        def fail(url):
            raise OSError("403")

        item = ev.build_input(link("news"), get=fail)
        self.assertIn("403", item["error"])


class OutputTest(unittest.TestCase):
    def test_validate(self):
        good = {"category": CATS[0]["name"], "summary": "요약", "keywords": ["a"]}
        self.assertEqual(ev.validate_output(good, CATS), "")
        self.assertIn("카테고리", ev.validate_output({**good, "category": "기타"}, CATS))
        self.assertEqual(ev.validate_output({**good, "keywords": []}, CATS), "주제어 없음")

    def test_run_one_records_format_and_api_errors(self):
        model = {"id": "m"}
        item = {"id": "L01", "url": "u", "title": "", "author": "", "description": "", "body": "", "memo": "m"}
        bad_json = ev.run_one(model, item, CATS, call=lambda *a: ("not json", 10, 2))
        self.assertEqual(bad_json["format_error"], "JSON 아님")

        def boom(*a):
            raise RuntimeError("429")

        self.assertIn("429", ev.run_one(model, item, CATS, call=boom)["api_error"])
        ok = json.dumps({"category": CATS[1]["name"], "summary": "s", "keywords": ["k"]})
        row = ev.run_one(model, item, CATS, call=lambda *a: (ok, 100, 20))
        self.assertEqual((row["output"]["category"], row["input_tokens"]), (CATS[1]["name"], 100))


class ScoreTest(unittest.TestCase):
    def test_keyword_hits_ignore_spacing_and_allow_containment(self):
        self.assertEqual(ev.keyword_hits(["성수 카페", "라떼", "없음"], ["성수카페", "바닐라라떼"]), 2)

    def test_cost_needs_price(self):
        self.assertIsNone(ev.cost_usd({"price_per_mtok": None}, 1000, 1000))
        self.assertAlmostEqual(ev.cost_usd({"price_per_mtok": {"input": 1.0, "output": 2.0}}, 1_000_000, 500_000), 2.0)

    def test_summarize_skips_unlabeled_and_errors(self):
        links = {
            "L01": ev.Link("L01", "blog", "u", "", "맛집·음식", ["라떼"]),
            "L02": ev.Link("L02", "instagram", "u", "m", "", []),
            "L03": ev.Link("L03", "x", "u", "", "여행·장소", []),
        }
        rows = [
            {"id": "L01", "input_tokens": 100, "output_tokens": 10, "latency_s": 1.0,
             "output": {"category": "맛집·음식", "summary": "s", "keywords": ["라떼"]}},
            {"id": "L02", "input_tokens": 50, "output_tokens": 5, "latency_s": 1.0,
             "output": {"category": "생활·정보", "summary": "s", "keywords": ["k"]}},
            {"id": "L03", "api_error": "429", "latency_s": 0.5},
        ]
        s = ev.summarize({"price_per_mtok": None}, links, rows)
        self.assertEqual((s["ok"], s["api_errors"], s["cat_hit"], s["cat_n"], s["kw_hit"]), (2, 1, 1, 1, 1))
        self.assertEqual(s["input_tokens"], 150)
        self.assertEqual(s["by_level"], {"auto": (1, 1)})
        table = ev.render_tables([({"id": "m"}, s)])
        self.assertIn("단가 미확인", table)


if __name__ == "__main__":
    unittest.main()
