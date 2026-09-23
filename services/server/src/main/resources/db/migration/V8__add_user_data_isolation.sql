ALTER TABLE personal_record ADD COLUMN user_id BIGINT;
ALTER TABLE daily_emotion ADD COLUMN user_id BIGINT;

DO $$
DECLARE
    legacy_owner_user_id BIGINT;
BEGIN
    SELECT user_id
    INTO legacy_owner_user_id
    FROM app_user
    WHERE username = 'MaxCat';

    IF legacy_owner_user_id IS NULL
        AND (EXISTS (SELECT 1 FROM personal_record)
            OR EXISTS (SELECT 1 FROM daily_emotion)) THEN
        RAISE EXCEPTION
            'Legacy records belong to MaxCat, but the MaxCat account does not exist.';
    END IF;

    UPDATE personal_record
    SET user_id = legacy_owner_user_id
    WHERE user_id IS NULL;

    UPDATE daily_emotion
    SET user_id = legacy_owner_user_id
    WHERE user_id IS NULL;
END $$;

ALTER TABLE personal_record ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE daily_emotion ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE personal_record
    ADD CONSTRAINT fk_personal_record_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE RESTRICT;

ALTER TABLE daily_emotion
    ADD CONSTRAINT fk_daily_emotion_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE RESTRICT;

DROP INDEX idx_personal_record_active_date;
CREATE INDEX idx_personal_record_user_active_date
    ON personal_record (user_id, record_date DESC, record_id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_personal_record_user_type_active_date
    ON personal_record (user_id, record_type, record_date DESC, record_id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_personal_record_user_recall_active_date
    ON personal_record (user_id, record_date DESC, record_id DESC)
    WHERE deleted_at IS NULL AND recall_enabled = TRUE;

DROP INDEX idx_daily_emotion_date;
CREATE INDEX idx_daily_emotion_user_date
    ON daily_emotion (user_id, record_date, created_at, emotion_id);

COMMENT ON COLUMN personal_record.user_id IS '文章所属用户，同时作为租户键';
COMMENT ON COLUMN daily_emotion.user_id IS '心情记录所属用户，同时作为租户键';
