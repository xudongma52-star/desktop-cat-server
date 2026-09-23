import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rag_service.models import RagDocument
from rag_service.retrieval import retrieve, split_text


class RetrievalTest(unittest.TestCase):
    def test_retrieves_the_most_relevant_chinese_record(self) -> None:
        documents = [
            RagDocument(
                documentId=1,
                title="实习第三周",
                content="今天工作有些累。晚上出去散步以后，我的压力减轻了很多。",
            ),
            RagDocument(
                documentId=2,
                title="周末做饭",
                content="今天学会了番茄炒蛋，味道比上次更好。",
            ),
        ]

        matches = retrieve("以前压力大时我是怎么放松的", documents, 2)

        self.assertGreaterEqual(len(matches), 1)
        self.assertEqual(matches[0].documentId, 1)
        self.assertIn("散步", matches[0].content)
        self.assertGreater(matches[0].score, 0)

    def test_returns_empty_matches_when_there_are_no_documents(self) -> None:
        self.assertEqual(retrieve("散步", [], 3), [])

    def test_splits_long_text_with_bounded_chunks(self) -> None:
        chunks = split_text("第一段。" * 160)

        self.assertGreater(len(chunks), 1)
        self.assertTrue(all(0 < len(chunk) <= 420 for chunk in chunks))


if __name__ == "__main__":
    unittest.main()
