ALTER TABLE MOTEDELTAKER_ARBEIDSGIVER_VARSEL
ADD COLUMN altinn_sent_at timestamptz DEFAULT NULL;

ALTER TABLE MOTE_REFERAT
ADD COLUMN altinn_sent_at timestamptz DEFAULT NULL;
