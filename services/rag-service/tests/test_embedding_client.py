import io
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rag_service.embedding_client import DIMENSIONS, embed_text


class EmbeddingClientTest(unittest.TestCase):
    def test_sends_query_role_and_accepts_1024_dimensions(self) -> None:
        response = io.BytesIO(json.dumps({"data": {"embedding": [0.25] * DIMENSIONS}}).encode())
        with patch.dict(os.environ, {"ARK_API_KEY": "test-key"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response) as urlopen:
                vector = embed_text("如何放松", query=True)

        self.assertEqual(len(json.loads(vector)), DIMENSIONS)
        request = urlopen.call_args.args[0]
        body = json.loads(request.data)
        self.assertEqual(body["model"], "doubao-embedding-vision-251215")
        self.assertEqual(body["dimensions"], DIMENSIONS)
        self.assertIn("Query:", body["instructions"])
        self.assertNotIn("test-key", request.data.decode())

    def test_invalid_dimension_falls_back(self) -> None:
        response = io.BytesIO(json.dumps({"data": {"embedding": [0.25]}}).encode())
        with patch.dict(os.environ, {"ARK_API_KEY": "test-key"}, clear=True):
            with patch("urllib.request.urlopen", return_value=response):
                self.assertIsNone(embed_text("一段记录", query=False))

    def test_missing_key_falls_back(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            self.assertIsNone(embed_text("一段记录", query=False))


if __name__ == "__main__":
    unittest.main()
