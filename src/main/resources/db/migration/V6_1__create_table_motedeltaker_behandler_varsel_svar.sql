CREATE TABLE MOTEDELTAKER_BEHANDLER_VARSEL_SVAR
(
    id                               SERIAL PRIMARY KEY,
    uuid                             VARCHAR(50) NOT NULL UNIQUE,
    created_at                       TIMESTAMP   NOT NULL,
    motedeltaker_behandler_varsel_id INTEGER REFERENCES MOTEDELTAKER_BEHANDLER_VARSEL (id) ON DELETE CASCADE,
    svar_type                        VARCHAR(30) NOT NULL,
    svar_tekst                       TEXT
);

CREATE INDEX IX_MOTEDELTAKER_BEHANDLER_VARSEL_SVAR_VARSEL_ID ON MOTEDELTAKER_BEHANDLER_VARSEL_SVAR (motedeltaker_behandler_varsel_id);
