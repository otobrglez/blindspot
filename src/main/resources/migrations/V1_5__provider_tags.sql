ALTER TABLE items
    ADD COLUMN provider_tags JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX idx_items_provider_tags ON items USING GIN(provider_tags);