"""Call Ark for semantic vectors without exposing personal text in logs."""

from __future__ import annotations

import json
import logging
import math
import os
import urllib.error
import urllib.request


logger = logging.getLogger(__name__)
DEFAULT_MODEL = "doubao-embedding-vision-251215"
DEFAULT_URL = "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal"
DIMENSIONS = 1024
TIMEOUT_SECONDS = 15


def model_name() -> str:
    return os.getenv("ARK_EMBEDDING_MODEL", DEFAULT_MODEL).strip()


def embed_text(text: str, *, query: bool) -> str | None:
    key = os.getenv("ARK_API_KEY", "").strip()
    if not key:
        return None

    role = "Query" if query else "Corpus"
    instructions = (
        "Target_modality: text.\n"
        "Instruction: Represent personal writing for semantic retrieval.\n"
        f"{role}:"
    )
    payload = json.dumps({
        "model": model_name(),
        "input": [{"type": "text", "text": text}],
        "dimensions": DIMENSIONS,
        "encoding_format": "float",
        "instructions": instructions,
    }, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        os.getenv("ARK_EMBEDDING_URL", DEFAULT_URL),
        data=payload,
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            body = json.load(response)
        vector = body["data"]["embedding"]
        if not isinstance(vector, list) or len(vector) != DIMENSIONS:
            raise ValueError("Unexpected embedding dimension")
        if not all(isinstance(value, (int, float)) and math.isfinite(value) for value in vector):
            raise ValueError("Invalid embedding value")
        return json.dumps(vector, separators=(",", ":"))
    except (urllib.error.URLError, TimeoutError, KeyError, TypeError, ValueError):
        logger.warning("event=ark_embedding_failed model=%s role=%s", model_name(), role,
                       exc_info=True)
        return None
