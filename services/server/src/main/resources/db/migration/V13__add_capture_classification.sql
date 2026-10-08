ALTER TABLE capture_item
    ADD COLUMN classification_target VARCHAR(16),
    ADD COLUMN classification_origin VARCHAR(16),
    ADD COLUMN record_resolution VARCHAR(20),
    ADD CONSTRAINT ck_capture_item_classification_target
        CHECK (classification_target IN ('RECORD', 'EMOTION')),
    ADD CONSTRAINT ck_capture_item_classification_origin
        CHECK (classification_origin IN ('AI', 'USER')),
    ADD CONSTRAINT ck_capture_item_classification_pair
        CHECK ((classification_target IS NULL AND classification_origin IS NULL)
            OR (classification_target IS NOT NULL AND classification_origin IS NOT NULL)),
    ADD CONSTRAINT ck_capture_item_record_resolution
        CHECK (record_resolution IN ('DIRECT', 'AI_ARTICLE')),
    ADD CONSTRAINT ck_capture_item_record_resolution_target
        CHECK (record_resolution IS NULL OR classification_target = 'RECORD');

CREATE INDEX idx_capture_item_user_unclassified_time
    ON capture_item (user_id, captured_at DESC, capture_id DESC)
    WHERE deleted_at IS NULL AND classification_target IS NULL;

ALTER TABLE personal_record DROP CONSTRAINT ck_personal_record_type;
ALTER TABLE personal_record
    ADD CONSTRAINT ck_personal_record_type
        CHECK (record_type IN ('DIARY', 'THOUGHT', 'WORK_NOTE', 'NOTE'));

COMMENT ON COLUMN personal_record.record_type IS
    '文章类型：DIARY、THOUGHT、WORK_NOTE、NOTE';
COMMENT ON COLUMN capture_item.classification_target IS
    '碎片分类结果：RECORD、EMOTION；为空表示尚未分类';
COMMENT ON COLUMN capture_item.classification_origin IS
    '碎片分类来源：AI、USER';
COMMENT ON COLUMN capture_item.record_resolution IS
    '记录类碎片的处理方式：DIRECT、AI_ARTICLE；为空表示尚未处理';

CREATE TABLE personal_record_capture_source (
    record_id BIGINT NOT NULL,
    capture_id UUID NOT NULL,

    CONSTRAINT pk_personal_record_capture_source
        PRIMARY KEY (record_id, capture_id),
    CONSTRAINT fk_personal_record_capture_source_record
        FOREIGN KEY (record_id) REFERENCES personal_record (record_id) ON DELETE RESTRICT,
    CONSTRAINT fk_personal_record_capture_source_capture
        FOREIGN KEY (capture_id) REFERENCES capture_item (capture_id) ON DELETE RESTRICT
);

COMMENT ON TABLE personal_record_capture_source IS '正式记录与原始碎片的来源关系';
COMMENT ON COLUMN personal_record_capture_source.record_id IS '正式记录主键';
COMMENT ON COLUMN personal_record_capture_source.capture_id IS '来源碎片主键';
