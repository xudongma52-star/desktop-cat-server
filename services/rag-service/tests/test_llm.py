import io
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rag_service.llm import (
    build_retrieval_question,
    classify_captures,
    draft_capture_article,
    generate_answer,
)
from rag_service.models import CaptureAiItem, RagConversationMessage, RagMatch


class AnswerGenerationTest(unittest.TestCase):
    def test_skips_request_without_api_key(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            self.assertIsNone(generate_answer("怎么放松？", [self._match()]))

    def test_returns_answer_from_ark_response(self) -> None:
        response = io.BytesIO(
            json.dumps(
                {"choices": [{"message": {"content": "你以前会去散步。[1]"}}]}
            ).encode("utf-8")
        )
        with patch.dict(os.environ, {"ARK_API_KEY": "test-key"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response) as urlopen:
                answer = generate_answer("怎么放松？", [self._match()])

        self.assertEqual(answer, "你以前会去散步。[1]")
        request = urlopen.call_args.args[0]
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 110)
        body = json.loads(request.data)
        self.assertEqual(body["model"], "doubao-seed-2-1-turbo-260628")
        self.assertIn("散步", body["messages"][1]["content"])
        self.assertNotIn("test-key", request.data.decode("utf-8"))

    def test_includes_history_for_answer_and_recent_turn_for_retrieval(self) -> None:
        history = [
            RagConversationMessage(role="USER", content="我以前遇到过什么困难？"),
            RagConversationMessage(role="ASSISTANT", content="你记录过一次项目延期。"),
        ]
        response = io.BytesIO(json.dumps({"choices": [{"message": {
            "content": "你后来重新安排了计划。[1]"
        }}]}).encode("utf-8"))

        with patch.dict(os.environ, {"ARK_API_KEY": "test-key"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response) as urlopen:
                answer = generate_answer("那后来呢？", [self._match()], history)

        self.assertEqual(answer, "你后来重新安排了计划。[1]")
        body = json.loads(urlopen.call_args.args[0].data)
        self.assertIn("项目延期", body["messages"][1]["content"])
        retrieval_question = build_retrieval_question("那后来呢？", history)
        self.assertIn("项目延期", retrieval_question)
        self.assertIn("那后来呢", retrieval_question)

    def _match(self) -> RagMatch:
        return RagMatch(documentId=1, content="晚上散步以后轻松了许多。", score=0.8)


class CaptureOrganizationTest(unittest.TestCase):
    def test_classification_sends_text_and_image_and_parses_json(self) -> None:
        capture_id = "8ee36f6f-25ce-42bf-90df-856ca1d78cf0"
        response = io.BytesIO(json.dumps({"choices": [{"message": {
            "content": f'```json\n{{"decisions":[{{"captureId":"{capture_id}","target":"RECORD"}}]}}\n```'
        }}]}).encode("utf-8"))
        item = CaptureAiItem(
            captureId=capture_id,
            content="学习了事务回滚。",
            imageMimeType="image/png",
            imageBase64="aW1hZ2U=",
        )

        with patch.dict(os.environ, {"ARK_API_KEY": "test-key"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response) as urlopen:
                result = classify_captures([item])

        self.assertEqual(result["decisions"][0]["target"], "RECORD")
        body = json.loads(urlopen.call_args.args[0].data)
        user_content = body["messages"][1]["content"]
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 180)
        self.assertEqual(body["response_format"], {"type": "json_object"})
        self.assertTrue(any(part.get("type") == "image_url" for part in user_content))
        self.assertIn(capture_id, json.dumps(user_content, ensure_ascii=False))

    def test_article_draft_parses_structured_result(self) -> None:
        response = io.BytesIO(json.dumps({"choices": [{"message": {
            "content": '{"title":"事务笔记","content":"今天整理了事务回滚。"}'
        }}]}).encode("utf-8"))
        item = CaptureAiItem(
            captureId="6c06dcad-01c8-4af1-8252-e2c7d4a8b293",
            content="今天整理了事务回滚。",
        )

        with patch.dict(os.environ, {"ARK_API_KEY": "test-key"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response):
                result = draft_capture_article([item])

        self.assertEqual(result, {"title": "事务笔记", "content": "今天整理了事务回滚。"})


if __name__ == "__main__":
    unittest.main()
