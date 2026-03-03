CREATE TABLE DIALOGMOTE_FLYT (
    id         SERIAL      PRIMARY KEY,
    uuid       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);
