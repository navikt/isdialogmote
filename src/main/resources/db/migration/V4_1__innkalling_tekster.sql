ALTER TABLE MOTEDELTAKER_ARBEIDSGIVER
ADD COLUMN fritekst_innkalling TEXT NOT NULL DEFAULT '';

ALTER TABLE MOTEDELTAKER_ARBEIDSTAKER
ADD COLUMN fritekst_innkalling TEXT NOT NULL DEFAULT '';

ALTER TABLE TID_STED
ADD COLUMN videolink TEXT NOT NULL DEFAULT '';
