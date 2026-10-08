ALTER TABLE knowledge_chat
    ADD COLUMN memory_summary JSONB,
    ADD COLUMN summary_through_message_id BIGINT,
    ADD CONSTRAINT ck_knowledge_chat_summary_pair CHECK (
        (memory_summary IS NULL AND summary_through_message_id IS NULL)
        OR (memory_summary IS NOT NULL AND jsonb_typeof(memory_summary) = 'object'
            AND summary_through_message_id IS NOT NULL AND summary_through_message_id > 0)
    );

COMMENT ON COLUMN knowledge_chat.memory_summary IS '滚动对话摘要，仅用于理解上下文，不作为知识库事实依据';
COMMENT ON COLUMN knowledge_chat.summary_through_message_id IS '摘要覆盖到的最后一条助手消息ID，由程序确定';
