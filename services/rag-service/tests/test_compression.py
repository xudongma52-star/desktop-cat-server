import io
import json
import os
from pathlib import Path
import sys
import unittest
import socket
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi import HTTPException
from rag_service.llm import compress_memory, generate_answer
from rag_service.main import app, compress_knowledge_memory
import uvicorn
from rag_service.models import MemorySummary, RagCompressRequest, RagMatch


class CompressionTest(unittest.TestCase):
    def test_live_http_compression_then_retrieval_with_local_model_stub(self):
        calls = []

        class ModelHandler(BaseHTTPRequestHandler):
            def do_POST(self):
                payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                calls.append(payload)
                content = (json.dumps({"topic": "项目A", "userFacts": ["用户纠正日期为七月"]})
                           if payload["max_tokens"] == 800 else "七月重新安排了计划。[1]")
                body = json.dumps({"choices": [{"message": {"content": content}}]}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        model = ThreadingHTTPServer(("127.0.0.1", 0), ModelHandler)
        model_thread = threading.Thread(target=model.serve_forever, daemon=True)
        model_thread.start()
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        base = f"http://127.0.0.1:{listener.getsockname()[1]}"
        server = uvicorn.Server(uvicorn.Config(app, log_level="error"))
        server_thread = threading.Thread(target=lambda: server.run(sockets=[listener]), daemon=True)
        server_thread.start()

        def post(path, payload):
            request = urllib.request.Request(base + path, data=json.dumps(payload).encode(),
                                             headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=5) as response:
                return json.load(response)

        try:
            deadline = time.monotonic() + 5
            while not server.started and time.monotonic() < deadline:
                time.sleep(.01)
            self.assertTrue(server.started)
            with patch.dict(os.environ, {"ARK_API_KEY": "local-test",
                                         "ARK_BASE_URL": f"http://127.0.0.1:{model.server_port}/chat"}):
                compressed = post("/internal/rag/compress", {"turns": [
                    {"messageId": 1, "role": "USER", "content": "不是六月，是七月。"},
                    {"messageId": 2, "role": "ASSISTANT", "content": "已纠正。"}
                ]})
                with patch("rag_service.main.retrieve_indexed", return_value=[
                        RagMatch(documentId=1, content="七月重新安排了计划。", score=.8)]):
                    answer = post("/internal/rag/retrieve", {"userId": 7, "question": "那后来呢？",
                        "documentIds": [1], "summary": compressed["summary"], "history": []})
            self.assertEqual(compressed["summary"]["userFacts"], ["用户纠正日期为七月"])
            self.assertTrue(answer["answerGenerated"])
            self.assertIn("[1]", answer["answer"])
            self.assertEqual(len(calls), 2)
            self.assertIn("项目A", calls[1]["messages"][1]["content"])
        finally:
            server.should_exit = True
            server_thread.join(timeout=5)
            listener.close()
            model.shutdown()
            model.server_close()
            model_thread.join(timeout=5)

    def test_uses_existing_model_and_structured_output(self):
        request = RagCompressRequest(previousSummary=MemorySummary(topic="项目A"), turns=[
            {"messageId": 1, "role": "USER", "content": "不是六月，是七月。"},
            {"messageId": 2, "role": "ASSISTANT", "content": "已纠正日期。"},
        ])
        response = io.BytesIO(json.dumps({"choices": [{"message": {"content": json.dumps({
            "topic": "项目A", "userFacts": ["用户纠正日期为七月"]
        })}}]}).encode())
        with patch.dict(os.environ, {"ARK_API_KEY": "test", "ARK_MODEL": "existing-model"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response) as call:
                result = compress_memory(request)
        body = json.loads(call.call_args.args[0].data)
        self.assertEqual(body["model"], "existing-model")
        self.assertEqual(body["temperature"], 0)
        self.assertEqual(body["max_tokens"], 800)
        self.assertEqual(body["response_format"], {"type": "json_object"})
        self.assertEqual(call.call_args.kwargs["timeout"], 60)
        self.assertEqual(body["thinking"], {"type": "disabled"})
        self.assertEqual(result.userFacts, ["用户纠正日期为七月"])

    def test_invalid_model_json_returns_unavailable(self):
        response = io.BytesIO(json.dumps({"choices": [{"message": {"content": "{bad"}}]}).encode())
        with patch.dict(os.environ, {"ARK_API_KEY": "test"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response):
                with self.assertRaises(HTTPException) as caught:
                    compress_knowledge_memory(RagCompressRequest(
                        turns=[{"role": "USER", "content": "测试"}]))
        self.assertEqual(caught.exception.status_code, 503)

    def test_empty_summary_is_rejected(self):
        with patch("rag_service.llm._request_organization_json", return_value={}):
            with self.assertRaises(ValueError):
                compress_memory(RagCompressRequest(turns=[{"role": "USER", "content": "测试"}]))

    def test_summary_is_added_as_context_not_as_cited_evidence(self):
        response = io.BytesIO(json.dumps({"choices": [{"message": {"content": "答案[1]"}}]}).encode())
        with patch.dict(os.environ, {"ARK_API_KEY": "test"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response) as call:
                generate_answer("那后来呢？", [RagMatch(documentId=1, content="原文事实", score=.8)],
                                [], MemorySummary(topic="项目A"))
        prompt = json.loads(call.call_args.args[0].data)["messages"][1]["content"]
        self.assertIn("项目A", prompt)
        self.assertIn("不能作为事实来源", prompt)
        self.assertIn("[1] 原文事实", prompt)


if __name__ == "__main__":
    unittest.main()
