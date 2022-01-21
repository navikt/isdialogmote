
CREATE TABLE PDF
(
    id                           SERIAL PRIMARY KEY,
    uuid                         VARCHAR(50)  NOT NULL UNIQUE,
    created_at                   TIMESTAMP    NOT NULL,
    updated_at                   TIMESTAMP    NOT NULL,
    pdf                          bytea        NOT NULL
);

ALTER TABLE MOTE_REFERAT
ADD COLUMN pdf_id INTEGER REFERENCES PDF (id) ON DELETE RESTRICT,
ALTER COLUMN pdf DROP NOT NULL;

ALTER TABLE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
ADD COLUMN pdf_id INTEGER REFERENCES PDF (id) ON DELETE RESTRICT,
ALTER COLUMN pdf DROP NOT NULL;

ALTER TABLE MOTEDELTAKER_ARBEIDSGIVER_VARSEL
ADD COLUMN pdf_id INTEGER REFERENCES PDF (id) ON DELETE RESTRICT,
ALTER COLUMN pdf DROP NOT NULL;

ALTER TABLE MOTEDELTAKER_BEHANDLER_VARSEL
ADD COLUMN pdf_id INTEGER REFERENCES PDF (id) ON DELETE RESTRICT,
ALTER COLUMN pdf DROP NOT NULL;

DO $$
DECLARE v_id INTEGER;
DECLARE v_row RECORD;
BEGIN
FOR v_row IN
        SELECT id::integer,pdf::bytea FROM MOTE_REFERAT
    LOOP
        INSERT INTO PDF VALUES (DEFAULT,(overlay(overlay(md5(random()::text || clock_timestamp()::text) placing '4' from 13) placing '8' from 17)::uuid)::text,now(),now(),v_row.pdf::bytea) RETURNING id into v_id;
        UPDATE MOTE_REFERAT SET pdf_id = v_id where id = v_row.id::integer;
    END LOOP;
FOR v_row IN
        SELECT id::integer,pdf::bytea FROM MOTEDELTAKER_ARBEIDSTAKER_VARSEL
    LOOP
        INSERT INTO PDF VALUES (DEFAULT,(overlay(overlay(md5(random()::text || clock_timestamp()::text) placing '4' from 13) placing '8' from 17)::uuid)::text,now(),now(),v_row.pdf::bytea) RETURNING id into v_id;
        UPDATE MOTEDELTAKER_ARBEIDSTAKER_VARSEL SET pdf_id = v_id where id = v_row.id::integer;
    END LOOP;
FOR v_row IN
        SELECT id::integer,pdf::bytea FROM MOTEDELTAKER_ARBEIDSGIVER_VARSEL
    LOOP
        INSERT INTO PDF VALUES (DEFAULT,(overlay(overlay(md5(random()::text || clock_timestamp()::text) placing '4' from 13) placing '8' from 17)::uuid)::text,now(),now(),v_row.pdf::bytea) RETURNING id into v_id;
        UPDATE MOTEDELTAKER_ARBEIDSGIVER_VARSEL SET pdf_id = v_id where id = v_row.id::integer;
    END LOOP;
FOR v_row IN
        SELECT id::integer,pdf::bytea FROM MOTEDELTAKER_BEHANDLER_VARSEL
    LOOP
        INSERT INTO PDF VALUES (DEFAULT,(overlay(overlay(md5(random()::text || clock_timestamp()::text) placing '4' from 13) placing '8' from 17)::uuid)::text,now(),now(),v_row.pdf::bytea) RETURNING id into v_id;
        UPDATE MOTEDELTAKER_BEHANDLER_VARSEL SET pdf_id = v_id where id = v_row.id::integer;
    END LOOP;
END
$$;
