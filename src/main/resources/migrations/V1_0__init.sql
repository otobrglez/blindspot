CREATE TABLE countries
(
    country_code VARCHAR(2) PRIMARY KEY,
    country      VARCHAR(255) NOT NULL,
    currency     VARCHAR(255) NOT NULL,
    created_at   timestamptz default now(),
    updated_at   timestamptz default now()
);

CREATE TABLE packages
(
    id             BIGSERIAL PRIMARY KEY,
    country_code   VARCHAR(2)   NOT NULL REFERENCES countries (country_code),
    clear_name     VARCHAR(255) NOT NULL,
    technical_name VARCHAR(255) NOT NULL,
    slug           VARCHAR(255) NOT NULL,
    created_at     timestamptz default now(),
    updated_at     timestamptz default now(),
    CONSTRAINT fk_country FOREIGN KEY (country_code) REFERENCES countries (country_code),
    CONSTRAINT unique_country_slug UNIQUE (country_code, slug)
);


