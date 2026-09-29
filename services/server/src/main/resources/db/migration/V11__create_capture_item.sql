CREATE TABLE capture_item (
    capture_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_capture_item_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_capture_item_content
        CHECK (char_length(btrim(content)) BETWEEN 1 AND 20000),
    CONSTRAINT ck_capture_item_version
        CHECK (version >= 0)
);

CREATE INDEX idx_capture_item_user_active_time
    ON capture_item (user_id, captured_at DESC, capture_id DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE capture_item IS '用户主动保存的原始文字记录';
COMMENT ON COLUMN capture_item.capture_id IS '客户端生成的 UUID，同时用于断网重试去重';
COMMENT ON COLUMN capture_item.user_id IS '记录所属用户';
COMMENT ON COLUMN capture_item.content IS '用户保存的原始文字';
COMMENT ON COLUMN capture_item.captured_at IS '用户实际记录时间，由客户端提交';
COMMENT ON COLUMN capture_item.version IS '乐观锁版本';
COMMENT ON COLUMN capture_item.created_at IS '服务器入库时间';
COMMENT ON COLUMN capture_item.updated_at IS '最后修改时间';
COMMENT ON COLUMN capture_item.deleted_at IS '逻辑删除时间';
