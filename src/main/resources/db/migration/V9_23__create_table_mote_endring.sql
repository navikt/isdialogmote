CREATE TABLE MOTE_ENDRING (
  id                     SERIAL       PRIMARY KEY,
  uuid                   VARCHAR(50)  NOT NULL UNIQUE,
  created_at             TIMESTAMP    NOT NULL,
  mote_status_endret_id  INTEGER      NOT NULL REFERENCES MOTE_STATUS_ENDRET (id) ON DELETE CASCADE,
  type                   VARCHAR(30)  NOT NULL,
  UNIQUE (mote_status_endret_id, type)
);

CREATE INDEX IX_MOTE_ENDRING_TYPE ON MOTE_ENDRING (type);

INSERT INTO MOTE_ENDRING (uuid, created_at, mote_status_endret_id, type)
SELECT gen_random_uuid()::text, created_at, id, 'TID_STED'
FROM MOTE_STATUS_ENDRET
WHERE status = 'NYTT_TID_STED';
