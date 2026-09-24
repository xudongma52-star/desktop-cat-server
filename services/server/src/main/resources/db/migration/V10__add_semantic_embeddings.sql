ALTER TABLE ai.document_chunk
    ADD COLUMN semantic_embedding vector(1024),
    ADD COLUMN semantic_model VARCHAR(120);

ALTER TABLE ai.document_chunk
    ADD CONSTRAINT ck_ai_document_chunk_semantic_pair
    CHECK (
        (semantic_embedding IS NULL AND semantic_model IS NULL)
        OR
        (semantic_embedding IS NOT NULL AND semantic_model IS NOT NULL)
    );
