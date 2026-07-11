-- Praxis initial schema.
-- Identity module owns: tenant, app_user.
-- The remaining tables are created now (per the LLD) so later modules
-- (Intake/Prism/Conductor/Cortex/Verdict/Chronicle/Ledger/Recall) don't
-- need a second migration just to exist; Identity is the only one that
-- writes to them yet.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE tenant (
                        id             UUID PRIMARY KEY,
                        name           TEXT NOT NULL,
                        code_residency TEXT NOT NULL DEFAULT 'CLOUD_ALLOWED',
                        plan           TEXT NOT NULL DEFAULT 'FREE',

                        -- auditing fields
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at TIMESTAMPTZ,
                        created_by VARCHAR(255),
                        updated_by VARCHAR(255)
);

CREATE TABLE app_user (
                          id            UUID PRIMARY KEY,
                          tenant_id     UUID NOT NULL REFERENCES tenant(id),
                          email         TEXT NOT NULL UNIQUE,
                          password_hash TEXT NOT NULL,
                          role          TEXT NOT NULL DEFAULT 'MEMBER',

                          -- auditing fields
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at TIMESTAMPTZ,
                          created_by VARCHAR(255),
                          updated_by VARCHAR(255)
);

CREATE INDEX idx_app_user_tenant ON app_user(tenant_id);

CREATE TABLE repository (
                            id          UUID PRIMARY KEY,
                            tenant_id   UUID NOT NULL REFERENCES tenant(id),
                            name        TEXT NOT NULL,
                            source_type TEXT NOT NULL,
                            source_ref  TEXT NOT NULL,

                            -- auditing fields
                            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at TIMESTAMPTZ,
                            created_by VARCHAR(255),
                            updated_by VARCHAR(255)
);

CREATE TABLE analysis (
                          id             UUID PRIMARY KEY,
                          repository_id  UUID NOT NULL REFERENCES repository(id),
                          tenant_id      UUID NOT NULL REFERENCES tenant(id),
                          status         TEXT NOT NULL,
                          health_score   INT,
                          prompt_version TEXT NOT NULL,
                          error_message  TEXT,
                          started_at     TIMESTAMPTZ,
                          completed_at   TIMESTAMPTZ,

                          -- auditing fields
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at TIMESTAMPTZ,
                          created_by VARCHAR(255),
                          updated_by VARCHAR(255)
);

CREATE TABLE file_result (
                             id          UUID PRIMARY KEY,
                             analysis_id UUID NOT NULL REFERENCES analysis(id),
                             path        TEXT NOT NULL,
                             loc         INT,
                             complexity  INT,
                             class_count INT,
                             source      TEXT,

                             -- auditing fields
                             created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                             updated_at TIMESTAMPTZ ,
                             created_by VARCHAR(255),
                             updated_by VARCHAR(255)
);

CREATE TABLE code_unit (
                           id             UUID PRIMARY KEY,
                           analysis_id    UUID NOT NULL REFERENCES analysis(id),
                           file_result_id UUID NOT NULL REFERENCES file_result(id),
                           unit_type      TEXT NOT NULL,
                           name           TEXT NOT NULL,
                           start_line     INT,
                           end_line       INT,
                           source_hash    TEXT NOT NULL,
                           risk_score     INT,

                           -- auditing fields
                           created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                           updated_at TIMESTAMPTZ,
                           created_by VARCHAR(255),
                           updated_by VARCHAR(255)
);

CREATE TABLE issue_finding (
                               id            UUID PRIMARY KEY,
                               code_unit_id  UUID REFERENCES code_unit(id),
                               analysis_id   UUID NOT NULL REFERENCES analysis(id),
                               type          TEXT NOT NULL,
                               severity      TEXT NOT NULL,
                               source        TEXT NOT NULL,
                               message       TEXT NOT NULL,
                               suggestion    TEXT,
                               start_line    INT,
                               end_line      INT,

                               -- auditing fields
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                               updated_at TIMESTAMPTZ,
                               created_by VARCHAR(255),
                               updated_by VARCHAR(255)
);

CREATE TABLE embedding (
                           code_unit_id UUID PRIMARY KEY REFERENCES code_unit(id),
                           vector       vector(768),

                           -- auditing fields
                           created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                           updated_at TIMESTAMPTZ,
                           created_by VARCHAR(255),
                           updated_by VARCHAR(255)
);

CREATE TABLE llm_call (
                          id          UUID PRIMARY KEY,
                          analysis_id UUID REFERENCES analysis(id),
                          tenant_id   UUID NOT NULL REFERENCES tenant(id),
                          provider    TEXT NOT NULL,
                          model       TEXT NOT NULL,
                          task_type   TEXT NOT NULL,
                          tokens_in   INT NOT NULL,
                          tokens_out  INT NOT NULL,
                          cost_cents  INT NOT NULL,
                          cache_hit   BOOLEAN NOT NULL DEFAULT false,

                          -- auditing fields
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at TIMESTAMPTZ,
                          created_by VARCHAR(255),
                          updated_by VARCHAR(255)
);
