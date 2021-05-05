-- ROLLBACK-START
------------------
-- DROP INDEX IX_MOTEDELTAKER_ARBEIDSGIVER_VARSEL_MD_AT_ID;
-- DROP TABLE MOTEDELTAKER_ARBEIDSGIVER_VARSEL;
---------------
-- ROLLBACK-END

CREATE TABLE MOTEDELTAKER_ARBEIDSGIVER_VARSEL
(
    id                           SERIAL PRIMARY KEY,
    uuid                         VARCHAR(50)  NOT NULL UNIQUE,
    created_at                   TIMESTAMP    NOT NULL,
    updated_at                   TIMESTAMP    NOT NULL,
    motedeltaker_arbeidsgiver_id INTEGER REFERENCES MOTEDELTAKER_ARBEIDSGIVER (id) ON DELETE CASCADE,
    varseltype                   VARCHAR(30)  NOT NULL,
    pdf                          bytea        NOT NULL,
    status                       VARCHAR(100) NOT NULL,
    lest_dato                    TIMESTAMP,
    fritekst                     TEXT         NOT NULL DEFAULT '',
    document                     JSONB        NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX IX_MOTEDELTAKER_ARBEIDSGIVER_VARSEL_MD_AT_ID ON MOTEDELTAKER_ARBEIDSGIVER_VARSEL (motedeltaker_arbeidsgiver_id);
