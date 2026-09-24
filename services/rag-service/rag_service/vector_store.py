"""Persist and search only the current user's permitted record chunks."""

from __future__ import annotations

import hashlib
import json
import logging
import math
import os

import psycopg

from .embedding_client import embed_text, model_name
from .models import RagDocument, RagMatch
from .retrieval import split_text, tokenize


logger = logging.getLogger(__name__)
DIMENSIONS = 1536
MODEL = "char-hash-v1"


def _embedding(text: str) -> str:
    values = [0.0] * DIMENSIONS
    for token in tokenize(text):
        digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
        bucket = int.from_bytes(digest, "big") % DIMENSIONS
        values[bucket] += 1.0
    magnitude = math.sqrt(sum(value * value for value in values))
    if magnitude:
        values = [value / magnitude for value in values]
    return json.dumps(values, separators=(",", ":"))


def retrieve_indexed(
    user_id: int, question: str, documents: list[RagDocument], top_k: int
) -> list[RagMatch]:
    lexical_matches = _retrieve_lexical(user_id, question, documents, top_k)
    if not documents:
        return lexical_matches
    semantic_matches = _retrieve_semantic(
        user_id, question, [document.documentId for document in documents], top_k
    )
    return semantic_matches if semantic_matches is not None else lexical_matches


def _retrieve_lexical(
    user_id: int, question: str, documents: list[RagDocument], top_k: int
) -> list[RagMatch]:
    ids = [document.documentId for document in documents]
    if not ids:
        return []

    with psycopg.connect(os.getenv("DATABASE_URL", ""), connect_timeout=5) as connection:
        with connection.cursor() as cursor:
            # Read and lock the authoritative rows before indexing. A concurrent edit
            # cannot leave a newer version overwritten by an older request.
            cursor.execute(
                """SELECT record_id, title, content, version
                   FROM personal_record
                   WHERE user_id = %s AND record_id = ANY(%s)
                     AND deleted_at IS NULL AND rag_enabled = TRUE
                   FOR UPDATE""",
                (user_id, ids),
            )
            records = cursor.fetchall()
            for record_id, title, content, version in records:
                chunks = split_text(content[:30_000])
                hashes = [hashlib.sha256(chunk.encode("utf-8")).hexdigest() for chunk in chunks]
                cursor.execute(
                    """SELECT source_version, chunk_index, content_hash
                       FROM ai.document_chunk
                       WHERE owner_user_id = %s AND source_type = 'PERSONAL_RECORD'
                         AND source_id = %s AND embedding_model = %s
                       ORDER BY chunk_index""",
                    (user_id, record_id, MODEL),
                )
                existing = cursor.fetchall()
                if existing == [(version + 1, index, value) for index, value in enumerate(hashes)]:
                    continue
                cursor.execute(
                    """DELETE FROM ai.document_chunk
                       WHERE owner_user_id = %s AND source_type = 'PERSONAL_RECORD'
                         AND source_id = %s AND embedding_model = %s""",
                    (user_id, record_id, MODEL),
                )
                for index, (chunk, content_hash) in enumerate(zip(chunks, hashes, strict=True)):
                    cursor.execute(
                        """INSERT INTO ai.document_chunk
                           (source_type, source_id, source_version, owner_user_id,
                            chunk_index, content, content_hash, embedding,
                            embedding_model, embedding_dimension)
                           VALUES ('PERSONAL_RECORD', %s, %s, %s, %s, %s, %s,
                                   %s::vector, %s, %s)""",
                        (record_id, version + 1, user_id, index, chunk, content_hash,
                         _embedding(f"{title or ''} {chunk}"), MODEL, DIMENSIONS),
                    )

            # Remove chunks for candidate records that were disabled or deleted since
            # Java read them. Search also joins the source row as a final safeguard.
            cursor.execute(
                """DELETE FROM ai.document_chunk AS chunk
                   WHERE chunk.owner_user_id = %s AND chunk.source_type = 'PERSONAL_RECORD'
                     AND chunk.source_id = ANY(%s) AND chunk.embedding_model = %s
                     AND NOT EXISTS (
                         SELECT 1 FROM personal_record AS record
                         WHERE record.record_id = chunk.source_id
                           AND record.user_id = chunk.owner_user_id
                           AND record.deleted_at IS NULL AND record.rag_enabled = TRUE
                     )""",
                (user_id, ids, MODEL),
            )
            vector = _embedding(question)
            cursor.execute(
                """WITH candidates AS MATERIALIZED (
                       SELECT chunk.source_id, chunk.content, chunk.chunk_index,
                              chunk.embedding
                       FROM ai.document_chunk AS chunk
                       JOIN personal_record AS record
                         ON record.record_id = chunk.source_id
                        AND record.user_id = chunk.owner_user_id
                       WHERE chunk.owner_user_id = %s
                         AND chunk.source_type = 'PERSONAL_RECORD'
                         AND chunk.source_id = ANY(%s)
                         AND chunk.embedding_model = %s
                         AND chunk.source_version = record.version + 1
                         AND chunk.index_status = 'READY'
                         AND record.deleted_at IS NULL AND record.rag_enabled = TRUE
                   )
                   SELECT source_id, content, 1 - (embedding <=> %s::vector) AS score
                   FROM candidates
                   ORDER BY embedding <=> %s::vector, source_id, chunk_index
                   LIMIT %s""",
                (user_id, ids, MODEL, vector, vector, top_k),
            )
            matches = [
                RagMatch(documentId=record_id, content=content, score=round(max(0, score), 4))
                for record_id, content, score in cursor.fetchall()
                if score > 0
            ]
        connection.commit()
    logger.info("event=vector_retrieval userId=%s candidates=%s matches=%s",
                user_id, len(records), len(matches))
    return matches


def _retrieve_semantic(
    user_id: int, question: str, ids: list[int], top_k: int
) -> list[RagMatch] | None:
    model = model_name()
    # Fetch work in a short transaction. Never hold a source-record lock while
    # calling Ark, since model latency must not delay an article edit or delete.
    with psycopg.connect(os.getenv("DATABASE_URL", ""), connect_timeout=5) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """SELECT chunk.chunk_id, chunk.content, chunk.source_version,
                          record.title
                   FROM ai.document_chunk AS chunk
                   JOIN personal_record AS record
                     ON record.record_id = chunk.source_id
                    AND record.user_id = chunk.owner_user_id
                   WHERE chunk.owner_user_id = %s
                     AND chunk.source_type = 'PERSONAL_RECORD'
                     AND chunk.source_id = ANY(%s)
                     AND chunk.source_version = record.version + 1
                     AND chunk.embedding_model = %s
                     AND chunk.index_status = 'READY'
                     AND record.deleted_at IS NULL AND record.rag_enabled = TRUE
                     AND (chunk.semantic_embedding IS NULL OR chunk.semantic_model <> %s)
                   ORDER BY chunk.source_id, chunk.chunk_index
                   LIMIT 20""",
                (user_id, ids, MODEL, model),
            )
            pending = cursor.fetchall()

    for chunk_id, content, source_version, title in pending:
        vector = embed_text(f"{title or ''} {content}", query=False)
        if vector is None:
            return None
        with psycopg.connect(os.getenv("DATABASE_URL", ""), connect_timeout=5) as connection:
            with connection.cursor() as cursor:
                # A version change during the API call makes this update a no-op.
                cursor.execute(
                    """UPDATE ai.document_chunk AS chunk
                       SET semantic_embedding = %s::vector,
                           semantic_model = %s,
                           updated_at = CURRENT_TIMESTAMP
                       WHERE chunk.chunk_id = %s AND chunk.owner_user_id = %s
                         AND chunk.source_version = %s
                         AND EXISTS (
                             SELECT 1 FROM personal_record AS record
                             WHERE record.record_id = chunk.source_id
                               AND record.user_id = chunk.owner_user_id
                               AND record.version + 1 = chunk.source_version
                               AND record.deleted_at IS NULL
                               AND record.rag_enabled = TRUE
                         )""",
                    (vector, model, chunk_id, user_id, source_version),
                )

    query_vector = embed_text(question, query=True)
    if query_vector is None:
        return None

    with psycopg.connect(os.getenv("DATABASE_URL", ""), connect_timeout=5) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """SELECT count(1)
                   FROM ai.document_chunk AS chunk
                   JOIN personal_record AS record
                     ON record.record_id = chunk.source_id
                    AND record.user_id = chunk.owner_user_id
                   WHERE chunk.owner_user_id = %s
                     AND chunk.source_type = 'PERSONAL_RECORD'
                     AND chunk.source_id = ANY(%s)
                     AND chunk.source_version = record.version + 1
                     AND chunk.embedding_model = %s
                     AND chunk.index_status = 'READY'
                     AND record.deleted_at IS NULL AND record.rag_enabled = TRUE
                     AND (chunk.semantic_embedding IS NULL OR chunk.semantic_model <> %s)""",
                (user_id, ids, MODEL, model),
            )
            if cursor.fetchone()[0]:
                return None
            # Materializing the small, user-scoped set keeps ranking exact and
            # prevents another user's vectors from affecting retrieval.
            cursor.execute(
                """WITH candidates AS MATERIALIZED (
                       SELECT chunk.source_id, chunk.content, chunk.chunk_index,
                              chunk.semantic_embedding
                       FROM ai.document_chunk AS chunk
                       JOIN personal_record AS record
                         ON record.record_id = chunk.source_id
                        AND record.user_id = chunk.owner_user_id
                       WHERE chunk.owner_user_id = %s
                         AND chunk.source_type = 'PERSONAL_RECORD'
                         AND chunk.source_id = ANY(%s)
                         AND chunk.source_version = record.version + 1
                         AND chunk.embedding_model = %s
                         AND chunk.semantic_model = %s
                         AND chunk.semantic_embedding IS NOT NULL
                         AND chunk.index_status = 'READY'
                         AND record.deleted_at IS NULL AND record.rag_enabled = TRUE
                   )
                   SELECT source_id, content,
                          1 - (semantic_embedding <=> %s::vector) AS score
                   FROM candidates
                   ORDER BY semantic_embedding <=> %s::vector,
                            source_id, chunk_index
                   LIMIT %s""",
                (user_id, ids, MODEL, model, query_vector, query_vector, top_k),
            )
            matches = [
                RagMatch(documentId=record_id, content=content,
                         score=round(max(0, score), 4))
                for record_id, content, score in cursor.fetchall()
                if score > 0
            ]
    logger.info("event=semantic_retrieval userId=%s matches=%s model=%s",
                user_id, len(matches), model)
    return matches
