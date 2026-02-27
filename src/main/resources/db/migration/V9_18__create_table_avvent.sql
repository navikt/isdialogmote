CREATE TABLE AVVENT
(
    id          SERIAL      PRIMARY KEY,
    uuid        UUID        NOT NULL UNIQUE,
    frist       DATE        NOT NULL,
    personident VARCHAR(11) NOT NULL,
    beskrivelse TEXT,
    created_by  VARCHAR(7)  NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    is_lukket   BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IX_AVVENT_PERSONIDENT on AVVENT(personident);
