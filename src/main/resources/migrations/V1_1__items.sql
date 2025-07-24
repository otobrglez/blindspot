CREATE TABLE items
(
    id                    VARCHAR(255) PRIMARY KEY,
    title                 VARCHAR(255) NOT NULL,
    original_release_year INTEGER      NOT NULL DEFAULT 0,
    short_description     TEXT,
    kind                  VARCHAR(10)  NOT NULL,
    created_at            timestamptz           default now(),
    updated_at            timestamptz           default now(),
    CONSTRAINT unique_id UNIQUE (id)
);


