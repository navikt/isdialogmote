ALTER TABLE MOTEDELTAKER_BEHANDLER
    ADD COLUMN deltatt        BOOLEAN DEFAULT TRUE,
    ADD COLUMN mottar_referat BOOLEAN DEFAULT TRUE;