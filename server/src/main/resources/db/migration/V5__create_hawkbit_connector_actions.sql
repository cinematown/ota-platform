CREATE TABLE hawkbit_connector_actions (
    tenant VARCHAR(64) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    action_id BIGINT NOT NULL,
    software_module_id BIGINT NOT NULL,
    install_after_download BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    install_requested BOOLEAN NOT NULL DEFAULT FALSE,
    last_firmware_state INTEGER,
    last_update_result INTEGER,
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant, endpoint, action_id),

    CONSTRAINT ck_hawkbit_connector_action_endpoint
        CHECK (btrim(endpoint) <> ''),

    CONSTRAINT ck_hawkbit_connector_action_tenant
        CHECK (btrim(tenant) <> ''),

    CONSTRAINT ck_hawkbit_connector_action_id
        CHECK (action_id > 0),

    CONSTRAINT ck_hawkbit_connector_module_id
        CHECK (software_module_id > 0),

    CONSTRAINT ck_hawkbit_connector_action_status
        CHECK (status IN (
            'RECEIVED',
            'DOWNLOAD',
            'DOWNLOADED',
            'RUNNING',
            'FINISHED',
            'WARNING',
            'ERROR',
            'CANCELED',
            'CANCEL_REJECTED'
        ))
);

CREATE UNIQUE INDEX uk_hawkbit_connector_active_endpoint
    ON hawkbit_connector_actions (tenant, endpoint)
    WHERE status NOT IN (
        'FINISHED',
        'ERROR',
        'CANCELED'
    );