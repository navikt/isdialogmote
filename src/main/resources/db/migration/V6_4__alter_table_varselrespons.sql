ALTER TABLE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
ADD COLUMN svar_type VARCHAR(30),
ADD COLUMN svar_tekst TEXT,
ADD COLUMN svar_tidspunkt TIMESTAMP;

ALTER TABLE MOTEDELTAKER_ARBEIDSGIVER_VARSEL
ADD COLUMN svar_type VARCHAR(30),
ADD COLUMN svar_tekst TEXT,
ADD COLUMN svar_tidspunkt TIMESTAMP;
