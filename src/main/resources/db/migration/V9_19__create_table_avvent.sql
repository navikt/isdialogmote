CREATE TABLE AVVENT (
    id                   SERIAL      PRIMARY KEY,
    uuid                 VARCHAR(50) NOT NULL UNIQUE,
    created_at           TIMESTAMP   NOT NULL,
    updated_at           TIMESTAMP   NOT NULL,
    dialogmote_flyt_id   INTEGER     NOT NULL REFERENCES DIALOGMOTE_FLYT(id),
    frist                DATE        NOT NULL,
    beskrivelse          TEXT,
    personident          VARCHAR(11) NOT NULL,
    created_by           VARCHAR(7)  NOT NULL
);
