CREATE TABLE MOTE_REFERAT (
	id                       SERIAL             PRIMARY KEY,
	uuid                     VARCHAR(50)        NOT NULL UNIQUE,
	created_at               TIMESTAMP          NOT NULL,
	updated_at               TIMESTAMP          NOT NULL,
	mote_id                  INTEGER REFERENCES MOTE (id) ON DELETE CASCADE,
	situasjon                TEXT,
	konklusjon               TEXT,
	arbeidstaker_oppgave     TEXT,
	arbeidsgiver_oppgave     TEXT,
	veileder_oppgave         TEXT,
	document                 JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE MOTEDELTAKER_ANNEN (
	id                       SERIAL             PRIMARY KEY,
	uuid                     VARCHAR(50)        NOT NULL UNIQUE,
	created_at               TIMESTAMP          NOT NULL,
	updated_at               TIMESTAMP          NOT NULL,
	mote_referat_id          INTEGER REFERENCES MOTE_REFERAT (id) ON DELETE CASCADE,
	funksjon                 TEXT NOT NULL,
	navn                     TEXT NOT NULL
);

CREATE INDEX IX_MOTE_REFERAT_MOTE_ID ON MOTE_REFERAT (mote_id);

CREATE INDEX IX_MOTEDELTAKER_ANNEN_REFERAT_ID ON MOTEDELTAKER_ANNEN (mote_referat_id);
