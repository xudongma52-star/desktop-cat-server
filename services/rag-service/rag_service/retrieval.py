from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
import math
import re
import unicodedata

from .models import RagDocument, RagMatch


CHUNK_SIZE = 420
CHUNK_OVERLAP = 60
MIN_CHUNK_SIZE = 30
SENTENCE_BOUNDARIES = "。！？!?；;\n"


@dataclass(frozen=True)
class TextChunk:
    document_id: int
    title: str | None
    content: str
    chunk_index: int


def split_text(content: str) -> list[str]:
    """按固定窗口切分正文，并尽量在中文句末结束当前片段。"""
    text = content.strip()
    if not text:
        return []

    chunks: list[str] = []
    start = 0
    while start < len(text):
        end = min(start + CHUNK_SIZE, len(text))
        if end < len(text):
            search_start = min(start + MIN_CHUNK_SIZE, end)
            boundary = max(text.rfind(mark, search_start, end) for mark in SENTENCE_BOUNDARIES)
            if boundary >= search_start:
                end = boundary + 1

        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)
        if end >= len(text):
            break
        start = max(start + 1, end - CHUNK_OVERLAP)

    return chunks


def tokenize(text: str) -> list[str]:
    """生成字符一元组和二元组，让中文检索无需分词词典或模型文件。"""
    normalized = unicodedata.normalize("NFKC", text).lower()
    characters = [character for character in normalized if character.isalnum()]
    unigrams = [f"u:{character}" for character in characters]
    bigrams = [f"b:{characters[index]}{characters[index + 1]}" for index in range(len(characters) - 1)]
    return unigrams + bigrams


def retrieve(question: str, documents: list[RagDocument], top_k: int) -> list[RagMatch]:
    chunks = [
        TextChunk(document.documentId, document.title, content, index)
        for document in documents
        for index, content in enumerate(split_text(document.content))
    ]
    if not chunks:
        return []

    token_counters = [Counter(tokenize(_searchable_text(chunk))) for chunk in chunks]
    document_frequencies: Counter[str] = Counter()
    for counter in token_counters:
        document_frequencies.update(counter.keys())

    inverse_document_frequencies = {
        token: math.log((len(chunks) + 1) / (frequency + 1)) + 1
        for token, frequency in document_frequencies.items()
    }
    query_counter = Counter(tokenize(question))
    query_vector = _weighted_vector(query_counter, inverse_document_frequencies)
    query_norm = _vector_norm(query_vector)
    if query_norm == 0:
        return []

    ranked: list[tuple[float, TextChunk]] = []
    for chunk, counter in zip(chunks, token_counters, strict=True):
        chunk_vector = _weighted_vector(counter, inverse_document_frequencies)
        score = _cosine_similarity(query_vector, query_norm, chunk_vector)
        if score > 0:
            ranked.append((score, chunk))

    ranked.sort(key=lambda item: (-item[0], item[1].document_id, item[1].chunk_index))
    return [
        RagMatch(
            documentId=chunk.document_id,
            content=_compact_whitespace(chunk.content),
            score=round(score, 4),
        )
        for score, chunk in ranked[:top_k]
    ]


def _searchable_text(chunk: TextChunk) -> str:
    return f"{chunk.title or ''}\n{chunk.content}"


def _weighted_vector(
    counter: Counter[str], inverse_document_frequencies: dict[str, float]
) -> dict[str, float]:
    token_count = sum(counter.values())
    if token_count == 0:
        return {}
    return {
        token: count / token_count * inverse_document_frequencies.get(token, 1.0)
        for token, count in counter.items()
    }


def _vector_norm(vector: dict[str, float]) -> float:
    return math.sqrt(sum(value * value for value in vector.values()))


def _cosine_similarity(
    query_vector: dict[str, float], query_norm: float, document_vector: dict[str, float]
) -> float:
    document_norm = _vector_norm(document_vector)
    if document_norm == 0:
        return 0.0
    dot_product = sum(
        query_weight * document_vector.get(token, 0.0)
        for token, query_weight in query_vector.items()
    )
    return dot_product / (query_norm * document_norm)


def _compact_whitespace(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()
