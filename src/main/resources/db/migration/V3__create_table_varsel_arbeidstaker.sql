-- ROLLBACK-START
------------------
-- DROP TABLE MOTEDELTAKER_ARBEIDSTAKER_VARSEL;
---------------
-- ROLLBACK-END

CREATE TABLE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
(
    id                           SERIAL PRIMARY KEY,
    uuid                         VARCHAR(50)  NOT NULL UNIQUE,
    created_at                   TIMESTAMP    NOT NULL,
    updated_at                   TIMESTAMP    NOT NULL,
    motedeltaker_arbeidstaker_id INTEGER REFERENCES MOTEDELTAKER_ARBEIDSTAKER (id) ON DELETE CASCADE,
    varseltype                   VARCHAR(30)  NOT NULL,
    digitalt                     BOOLEAN      NOT NULL,
    pdf                          bytea        NOT NULL,
    status                       VARCHAR(100) NOT NULL,
    lest_dato                    TIMESTAMP
);
