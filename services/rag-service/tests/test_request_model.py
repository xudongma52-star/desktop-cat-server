import unittest

from pydantic import ValidationError

from rag_service.models import (
    CaptureAiItem,
    CaptureDecision,
    RagConversationMessage,
    RagRetrieveRequest,
)


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

    def test_accepts_up_to_one_hundred_history_messages(self) -> None:
        history = [
            RagConversationMessage(
                role="USER" if index % 2 == 0 else "ASSISTANT",
                content=f"消息{index}",
            )
            for index in range(100)
        ]

        request = RagRetrieveRequest(
            userId=7, question="继续", documentIds=[1], history=history
        )

        self.assertEqual(len(request.history), 100)

    def test_rejects_more_than_one_hundred_history_messages(self) -> None:
        history = [
            RagConversationMessage(role="USER", content=f"消息{index}")
            for index in range(101)
        ]
        with self.assertRaises(ValidationError):
            RagRetrieveRequest(
                userId=7, question="继续", documentIds=[1], history=history
            )


class CaptureOrganizationModelTest(unittest.TestCase):
    def test_image_requires_mime_type(self) -> None:
        with self.assertRaises(ValidationError):
            CaptureAiItem(
                captureId="8ee36f6f-25ce-42bf-90df-856ca1d78cf0",
                content="",
                imageBase64="aW1hZ2U=",
            )

    def test_decision_rejects_unknown_target(self) -> None:
        with self.assertRaises(ValidationError):
            CaptureDecision(
                captureId="8ee36f6f-25ce-42bf-90df-856ca1d78cf0",
                target="OTHER",
            )


if __name__ == "__main__":
    unittest.main()
