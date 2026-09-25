import unittest

from pydantic import ValidationError

from rag_service.models import RagRetrieveRequest


class RagRetrieveRequestTest(unittest.TestCase):
    def test_accepts_more_than_one_hundred_record_ids(self) -> None:
        ids = list(range(1, 102))

        request = RagRetrieveRequest(
            userId=7, question="以前写过什么？", topK=5, documentIds=ids
        )

        self.assertEqual(request.documentIds, ids)

    def test_rejects_non_positive_record_id(self) -> None:
        with self.assertRaises(ValidationError):
            RagRetrieveRequest(userId=7, question="散步", documentIds=[0])


if __name__ == "__main__":
    unittest.main()
