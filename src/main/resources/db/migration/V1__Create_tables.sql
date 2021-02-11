-- ROLLBACK-START
------------------
-- DROP TABLE MOTE;
-- DROP TABLE TID_STED;
-- DROP TABLE MOTEDELTAKER_ARBEIDSTAKER;
-- DROP TABLE MOTEDELTAKER_ARBEIDSGIVER;
-- DROP TABLE MOTE_STATUS_ENDRET;
---------------
-- ROLLBACK-END

CREATE TABLE MOTE (
  id                       SERIAL             PRIMARY KEY,
  uuid                     VARCHAR(50)        NOT NULL UNIQUE,
  created_at               TIMESTAMP          NOT NULL,
  updated_at               TIMESTAMP          NOT NULL,
  planlagtmote_uuid        VARCHAR(50)        UNIQUE,
  status                   VARCHAR(30)        NOT NULL,
  opprettet_av             VARCHAR(7)         NOT NULL,
  tildelt_veileder_ident   VARCHAR(7),
  tildelt_enhet            VARCHAR(10)
);

CREATE TABLE TID_STED (
  id                       SERIAL             PRIMARY KEY,
  uuid                     VARCHAR(50)        NOT NULL UNIQUE,
  created_at               TIMESTAMP          NOT NULL,
  updated_at               TIMESTAMP          NOT NULL,
  mote_id                  INTEGER REFERENCES MOTE (id) ON DELETE CASCADE,
  sted                     TEXT               NOT NULL,
  tid                      timestamp          NOT NULL
);

CREATE TABLE MOTEDELTAKER_ARBEIDSTAKER (
  id                       SERIAL             PRIMARY KEY,
  uuid                     VARCHAR(50)        NOT NULL UNIQUE,
  created_at               TIMESTAMP          NOT NULL,
  updated_at               TIMESTAMP          NOT NULL,
  mote_id                  INTEGER REFERENCES MOTE (id) ON DELETE CASCADE,
  personident              VARCHAR(11)        NOT NULL
);

CREATE TABLE MOTEDELTAKER_ARBEIDSGIVER (
  id                       SERIAL             PRIMARY KEY,
  uuid                     VARCHAR(50)        NOT NULL UNIQUE,
  created_at               TIMESTAMP          NOT NULL,
  updated_at               TIMESTAMP          NOT NULL,
  mote_id                  INTEGER REFERENCES MOTE (id) ON DELETE CASCADE,
  virksomhetsnummer        VARCHAR(9)         NOT NULL,
  leder_navn               VARCHAR(255),
  leder_epost              VARCHAR(255)
);

CREATE TABLE MOTE_STATUS_ENDRET (
  id                       SERIAL             PRIMARY KEY,
  uuid                     VARCHAR(50)        NOT NULL UNIQUE,
  created_at               TIMESTAMP          NOT NULL,
  updated_at               TIMESTAMP          NOT NULL,
  mote_id                  INTEGER REFERENCES MOTE (id) ON DELETE CASCADE,
  status                   VARCHAR(30)        NOT NULL,
  opprettet_av             VARCHAR(7)         NOT NULL
);
