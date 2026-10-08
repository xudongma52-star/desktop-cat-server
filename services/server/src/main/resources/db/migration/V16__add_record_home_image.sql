ALTER TABLE personal_record ADD COLUMN cover_image_key VARCHAR(512);
ALTER TABLE personal_record ADD COLUMN home_excerpt TEXT;
COMMENT ON COLUMN personal_record.cover_image_key IS '首页图文配图原文件的相对存储路径';
COMMENT ON COLUMN personal_record.home_excerpt IS '首页展示摘要；为空时从正文生成';
