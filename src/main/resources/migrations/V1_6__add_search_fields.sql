CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE items
  ADD COLUMN title_vec tsvector
    GENERATED ALWAYS AS (to_tsvector('english', title)) STORED;

ALTER TABLE items
  ADD COLUMN short_description_vec tsvector
    GENERATED ALWAYS AS (to_tsvector('english', short_description)) STORED;

CREATE INDEX title_vec_idx ON items USING GIN (title_vec);
CREATE INDEX short_description_vec_idx ON items USING GIN (short_description gin_trgm_ops);
