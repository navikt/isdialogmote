ALTER TABLE motedeltaker_arbeidstaker_varsel
    ADD COLUMN svar_published_to_kafka_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE motedeltaker_arbeidsgiver_varsel
    ADD COLUMN svar_published_to_kafka_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE motedeltaker_behandler_varsel_svar
    ADD COLUMN svar_published_to_kafka_at TIMESTAMPTZ DEFAULT NULL;
