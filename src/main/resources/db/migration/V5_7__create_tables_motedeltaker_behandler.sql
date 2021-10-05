CREATE TABLE MOTEDELTAKER_BEHANDLER (
  id                       SERIAL             PRIMARY KEY,
  uuid                     VARCHAR(50)        NOT NULL UNIQUE,
  created_at               TIMESTAMP          NOT NULL,
  updated_at               TIMESTAMP          NOT NULL,
  mote_id                  INTEGER REFERENCES MOTE (id) ON DELETE CASCADE,
  behandler_ref            VARCHAR            NOT NULL,
  behandler_navn           VARCHAR(255),
  behandler_kontor         VARCHAR(255),
  behandler_type           VARCHAR(30)
);

CREATE TABLE MOTEDELTAKER_BEHANDLER_VARSEL
(
  id                           SERIAL PRIMARY KEY,
  uuid                         VARCHAR(50)  NOT NULL UNIQUE,
  created_at                   TIMESTAMP    NOT NULL,
  updated_at                   TIMESTAMP    NOT NULL,
  motedeltaker_behandler_id    INTEGER REFERENCES MOTEDELTAKER_BEHANDLER (id) ON DELETE CASCADE,
  varseltype                   VARCHAR(30)  NOT NULL,
  document                     JSONB NOT NULL DEFAULT '[]'::jsonb,
  pdf                          bytea        NOT NULL,
  status                       VARCHAR(100) NOT NULL
);

CREATE INDEX IX_MOTEDELTAKER_BEHANDLER_BEHANDLER_REF ON MOTEDELTAKER_BEHANDLER (behandler_ref);
CREATE INDEX IX_MOTEDELTAKER_BEHANDLER_MOTE_ID ON MOTEDELTAKER_BEHANDLER (mote_id);
CREATE INDEX IX_MOTEDELTAKER_BEHANDLER_VARSEL_BEHANDLER_ID ON MOTEDELTAKER_BEHANDLER_VARSEL (motedeltaker_behandler_id);

