CREATE TABLE auth_device (
    device_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credential_hash CHAR(64) NOT NULL,
    client_id VARCHAR(32) NOT NULL DEFAULT 'DESKTOP',
    device_name VARCHAR(64) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    app_version VARCHAR(32),
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_auth_device_credential_hash
        UNIQUE (credential_hash),
    CONSTRAINT fk_auth_device_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_auth_device_credential_hash
        CHECK (credential_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_auth_device_client
        CHECK (client_id = 'DESKTOP'),
    CONSTRAINT ck_auth_device_name
        CHECK (char_length(btrim(device_name)) BETWEEN 1 AND 64),
    CONSTRAINT ck_auth_device_platform
        CHECK (char_length(btrim(platform)) BETWEEN 1 AND 32),
    CONSTRAINT ck_auth_device_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_auth_device_user
    ON auth_device (user_id, created_at DESC);

CREATE INDEX idx_auth_device_active
    ON auth_device (user_id, expires_at)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE auth_device IS '已通过统一身份认证授权的客户端设备';
COMMENT ON COLUMN auth_device.device_id IS '设备会话唯一标识，由服务端生成';
COMMENT ON COLUMN auth_device.user_id IS '设备所属用户';
COMMENT ON COLUMN auth_device.credential_hash IS '长期设备凭证的 SHA-256 摘要，不保存明文';
COMMENT ON COLUMN auth_device.client_id IS '客户端类型，当前固定为 DESKTOP';
COMMENT ON COLUMN auth_device.device_name IS '便于用户识别的设备名称';
COMMENT ON COLUMN auth_device.platform IS '客户端操作系统平台';
COMMENT ON COLUMN auth_device.app_version IS '客户端版本';
COMMENT ON COLUMN auth_device.expires_at IS '设备免登录授权失效时间';
COMMENT ON COLUMN auth_device.last_used_at IS '最近一次换取临时访问令牌的时间';
COMMENT ON COLUMN auth_device.revoked_at IS '设备授权撤销时间，为空表示未撤销';
COMMENT ON COLUMN auth_device.created_at IS '设备首次授权时间';
COMMENT ON COLUMN auth_device.updated_at IS '设备记录最后更新时间';
