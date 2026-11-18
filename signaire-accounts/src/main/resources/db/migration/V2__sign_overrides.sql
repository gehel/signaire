-- Signs added, edited or deleted by administrators. Elasticsearch is rebuilt from the bundles: these changes
-- are applied again after each load.
CREATE TABLE sign_override (
    uid        VARCHAR(64) PRIMARY KEY,
    deleted    BOOLEAN     NOT NULL,
    -- the whole sign, as JSON; null for deleted signs
    sign_json  TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by BIGINT REFERENCES account (id) ON DELETE SET NULL
);
