ALTER TABLE capture_item ADD COLUMN image_storage_key VARCHAR(512);

ALTER TABLE capture_item DROP CONSTRAINT ck_capture_item_content;
ALTER TABLE capture_item ADD CONSTRAINT ck_capture_item_content
    CHECK (char_length(btrim(content)) <= 20000
        AND (char_length(btrim(content)) > 0 OR image_storage_key IS NOT NULL));

CREATE UNIQUE INDEX uk_capture_item_image_storage_key
    ON capture_item (image_storage_key)
    WHERE image_storage_key IS NOT NULL;

COMMENT ON COLUMN capture_item.image_storage_key IS '随手记录原图在数据目录中的相对路径；每条记录至多一张';
