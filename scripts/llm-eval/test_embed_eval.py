"""embed_eval.py에서 키 없이 돌릴 수 있는 부분(문장 만들기, 채점, 기준값, 캐시)의 시험. 네트워크를 쓰지 않는다.

    python3 -m unittest -v test_embed_eval.py
"""

import json
import math
import tempfile
import unittest
from pathlib import Path

import embed_eval as ee


def doc(id="L01", title="제목", summary="요약", keywords=("가", "나")):
    return ee.Doc(id=id, platform="youtube", level="auto", title=title, summary=summary, keywords=list(keywords))


def query(id="Q01", kind="relevant", gold=("L01",)):
    return ee.Query(id=id, kind=kind, keyword_query="검색어", chat_query="채팅 질문", gold_ids=list(gold))


class TextTest(unittest.TestCase):
    def test_summary_target_uses_summary_only(self):
        self.assertEqual("title: none | text: 요약", ee.doc_text(doc(), "summary"))

    def test_full_target_uses_title_summary_and_keywords(self):
        self.assertEqual("title: 제목 | text: 요약 주제어: 가, 나", ee.doc_text(doc(), "full"))

    def test_full_target_without_title_uses_none(self):
        self.assertEqual("title: none | text: 요약 주제어: 가, 나", ee.doc_text(doc(title=""), "full"))

    def test_full_target_without_keywords_has_no_keyword_part(self):
        self.assertEqual("title: 제목 | text: 요약", ee.doc_text(doc(keywords=()), "full"))

    def test_empty_doc_has_no_text(self):
        self.assertIsNone(ee.doc_text(doc(summary=""), "summary"))
        self.assertIsNone(ee.doc_text(doc(title="", summary="", keywords=()), "full"))

    def test_full_target_keeps_doc_with_title_but_no_summary(self):
        self.assertEqual("title: 제목 | text: ", ee.doc_text(doc(summary="", keywords=()), "full"))

    def test_query_text_by_style(self):
        q = query()
        self.assertEqual("task: search result | query: 검색어", ee.query_text(q, "keyword"))
        self.assertEqual("task: search result | query: 채팅 질문", ee.query_text(q, "chat"))


class LoadTest(unittest.TestCase):
    def test_committed_queries_are_ten_relevant_and_three_irrelevant(self):
        queries = ee.load_queries()
        self.assertEqual(10, sum(q.kind == "relevant" for q in queries))
        self.assertEqual(3, sum(q.kind == "irrelevant" for q in queries))
        for q in queries:
            self.assertTrue(q.keyword_query and q.chat_query, q.id)
            self.assertEqual(q.kind == "relevant", bool(q.gold_ids), q.id)

    def test_gold_ids_must_exist_in_docs(self):
        with self.assertRaisesRegex(ValueError, "L99"):
            ee.check_gold([query(gold=("L99",))], [doc()])

    def test_gold_must_have_text_for_every_target(self):
        with self.assertRaisesRegex(ValueError, "L01"):
            ee.check_gold([query(gold=("L01",))], [doc(summary="")])


class SnapshotTest(unittest.TestCase):
    def test_snapshot_rows_take_title_from_input_cache_and_summary_from_model_output(self):
        with tempfile.TemporaryDirectory() as d:
            cache, out = Path(d) / "cache", Path(d) / "out.jsonl"
            cache.mkdir()
            (cache / "L01.json").write_text(json.dumps(
                {"id": "L01", "platform": "x", "level": "partial", "title": "글 제목", "body": "본문"}), encoding="utf-8")
            (cache / "L02.json").write_text(json.dumps(
                {"id": "L02", "platform": "shop", "level": "memo", "title": ""}), encoding="utf-8")
            out.write_text("\n".join([
                json.dumps({"id": "L01", "output": {"summary": "요약", "keywords": ["가", "나"]}}),
                json.dumps({"id": "L02", "api_error": "boom"}),
            ]) + "\n", encoding="utf-8")

            rows = ee.snapshot_rows(cache, out)

        self.assertEqual([
            {"id": "L01", "platform": "x", "level": "partial", "title": "글 제목", "summary": "요약", "keywords": "가;나"},
            {"id": "L02", "platform": "shop", "level": "memo", "title": "", "summary": "", "keywords": ""},
        ], rows)


class MathTest(unittest.TestCase):
    def test_cosine(self):
        self.assertAlmostEqual(1.0, ee.cosine([1, 0], [2, 0]))
        self.assertAlmostEqual(0.0, ee.cosine([1, 0], [0, 3]))
        self.assertAlmostEqual(-1.0, ee.cosine([1, 1], [-1, -1]))

    def test_truncate_normalize(self):
        v = ee.truncate_normalize([3, 4, 12], 2)
        self.assertAlmostEqual(0.6, v[0])
        self.assertAlmostEqual(0.8, v[1])
        self.assertAlmostEqual(1.0, math.hypot(*v))


class ScoreTest(unittest.TestCase):
    # 유사도 순서: L1 0.9, L2 0.8, ..., L7 0.3
    RANKING = [(f"L{i}", round(1.0 - i / 10, 2)) for i in range(1, 8)]

    def test_hit_when_any_gold_in_top5(self):
        s = ee.score_query(self.RANKING, ["L5", "L7"])
        self.assertTrue(s.hit)
        self.assertEqual((1, 2), (s.gold_in_top, s.gold_total))
        self.assertEqual(5, s.first_rank)
        self.assertAlmostEqual(0.3, s.min_gold_sim)
        self.assertAlmostEqual(0.9, s.top1_sim)

    def test_miss_when_gold_only_below_top5(self):
        s = ee.score_query(self.RANKING, ["L6"])
        self.assertFalse(s.hit)
        self.assertEqual(6, s.first_rank)
        self.assertEqual((0, 1), (s.gold_in_top, s.gold_total))

    def test_hit_after_threshold_drops_results_at_or_below_threshold(self):
        s = ee.score_query(self.RANKING, ["L3", "L4"])
        self.assertTrue(ee.hit_after_threshold(s, threshold=0.65))  # L3 0.7 남음
        self.assertFalse(ee.hit_after_threshold(s, threshold=0.7))  # 0.7 이하는 버린다

    def test_threshold_is_highest_top1_of_irrelevant_queries(self):
        rankings = {"N01": [("L1", 0.41)], "N02": [("L2", 0.52)], "N03": [("L3", 0.47)]}
        self.assertAlmostEqual(0.52, ee.irrelevant_threshold(rankings))

    def test_rank_sorts_by_similarity(self):
        docs = {"A": [1.0, 0.0], "B": [0.0, 1.0], "C": [0.7, 0.7]}
        ranking = ee.rank([1.0, 0.1], docs)
        self.assertEqual(["A", "C", "B"], [i for i, _ in ranking])


class CacheTest(unittest.TestCase):
    def test_calls_only_for_missing_texts_in_chunks_and_saves(self):
        calls = []

        def fake_embed(texts, dim):
            calls.append(list(texts))
            return [[float(len(t)), float(dim)] for t in texts]

        with tempfile.TemporaryDirectory() as d:
            cache = ee.EmbeddingCache(Path(d), model="m", dim=8)
            first = cache.embed(["a", "bb", "ccc"], fake_embed, chunk=2)
            second = ee.EmbeddingCache(Path(d), model="m", dim=8).embed(["bb", "dddd"], fake_embed, chunk=2)

        self.assertEqual([["a", "bb"], ["ccc"], ["dddd"]], calls)
        self.assertEqual([1.0, 8.0], first["a"])
        self.assertEqual([2.0, 8.0], second["bb"])

    def test_keeps_finished_chunks_when_a_later_chunk_fails(self):
        def flaky(texts, dim):
            if "c" in texts:
                raise RuntimeError("boom")
            return [[1.0] for _ in texts]

        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(RuntimeError):
                ee.EmbeddingCache(Path(d), model="m", dim=8).embed(["a", "b", "c"], flaky, chunk=2)
            self.assertEqual({"a", "b"}, set(ee.EmbeddingCache(Path(d), model="m", dim=8).load().keys()))

    def test_wrong_vector_count_is_an_error(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaisesRegex(RuntimeError, "2"):
                ee.EmbeddingCache(Path(d), model="m", dim=8).embed(["a", "b"], lambda t, d: [[1.0]], chunk=5)


class RetryTest(unittest.TestCase):
    def test_retries_rate_limit_then_succeeds(self):
        attempts, waits = [], []

        def call(texts, dim):
            attempts.append(1)
            if len(attempts) == 1:
                raise RuntimeError("429 RESOURCE_EXHAUSTED 'retryDelay': '7s'")
            return [[1.0]]

        self.assertEqual([[1.0]], ee.with_retry(call, sleep=waits.append)(["a"], 8))
        self.assertEqual([8.0], waits)

    def test_other_errors_are_not_retried(self):
        def call(texts, dim):
            raise RuntimeError("400 INVALID_ARGUMENT")

        with self.assertRaises(RuntimeError):
            ee.with_retry(call, sleep=lambda s: self.fail("기다리면 안 된다"))(["a"], 8)


class RunTest(unittest.TestCase):
    def test_run_stops_without_key_and_prints_only_name(self):
        out = []
        code = ee.require_key({}, print_=out.append)
        self.assertEqual(2, code)
        self.assertIn("GEMINI_API_KEY", out[0])

    def test_key_value_is_never_printed(self):
        out = []
        self.assertEqual(0, ee.require_key({"GEMINI_API_KEY": "secret-value"}, print_=out.append))
        self.assertFalse(any("secret-value" in line for line in out))


if __name__ == "__main__":
    unittest.main()
