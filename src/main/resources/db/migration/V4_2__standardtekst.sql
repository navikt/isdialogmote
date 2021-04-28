-- ROLLBACK-START
------------------
-- DROP TABLE STANDARDTEKST;
---------------
-- ROLLBACK-END

CREATE TABLE STANDARDTEKST (
  id                       SERIAL             PRIMARY KEY,
  created_at               TIMESTAMP          NOT NULL,
  updated_at               TIMESTAMP          NOT NULL,
  nokkel                   VARCHAR(50)        NOT NULL,
  gyldig_fra               TIMESTAMP          NOT NULL,
  tekst                    TEXT               NOT NULL DEFAULT '',
  UNIQUE (nokkel, gyldig_fra)
);
