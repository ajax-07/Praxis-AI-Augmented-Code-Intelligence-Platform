-- add refresh token table to persist the token backup on server-side

CREATE TABLE refresh_token (
  id               UUID PRIMARY KEY,
  token            TEXT NOT NULL UNIQUE,
  expiration       TIMESTAMPTZ NOT NULL,
  user_id          UUID NOT NULL,
    -- auditing fields
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_by VARCHAR(255)

);