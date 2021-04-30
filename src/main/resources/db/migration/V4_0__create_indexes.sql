-- ROLLBACK-START
------------------
-- DROP INDEX IX_MOTE_TILDELT_VEILEDER_IDENT;
-- DROP INDEX IX_MOTE_TILDELT_ENHET;
-- DROP INDEX IX_TID_STED_MOTE_ID;
-- DROP INDEX IX_MOTEDELTAKER_ARBEIDSGIVER_MOTE_ID;
-- DROP INDEX IX_MOTEDELTAKER_ARBEIDSGIVER_VIRKSOMHETSNUMMER;
-- DROP INDEX IX_MOTEDELTAKER_ARBEIDSTAKER_MOTE_ID;
-- DROP INDEX IX_MOTEDELTAKER_ARBEIDSTAKER_PERSONINDENT;
-- DROP INDEX IX_MOTE_STATUS_ENDRET_MOTE_ID;
-- DROP INDEX IX_MOTEDELTAKER_ARBEIDSTAKER_VARSEL_MD_AT_ID;
---------------
-- ROLLBACK-END

CREATE INDEX IX_MOTE_TILDELT_VEILEDER_IDENT ON MOTE (tildelt_veileder_ident);

CREATE INDEX IX_MOTE_TILDELT_ENHET ON MOTE (tildelt_enhet);

CREATE INDEX IX_TID_STED_MOTE_ID ON TID_STED (mote_id);

CREATE INDEX IX_MOTEDELTAKER_ARBEIDSGIVER_MOTE_ID ON MOTEDELTAKER_ARBEIDSGIVER (mote_id);

CREATE INDEX IX_MOTEDELTAKER_ARBEIDSGIVER_VIRKSOMHETSNUMMER ON MOTEDELTAKER_ARBEIDSGIVER (virksomhetsnummer, mote_id);

CREATE INDEX IX_MOTEDELTAKER_ARBEIDSTAKER_MOTE_ID ON MOTEDELTAKER_ARBEIDSTAKER (mote_id);

CREATE INDEX IX_MOTEDELTAKER_ARBEIDSTAKER_PERSONINDENT ON MOTEDELTAKER_ARBEIDSTAKER (personident, mote_id);

CREATE INDEX IX_MOTE_STATUS_ENDRET_MOTE_ID ON MOTE_STATUS_ENDRET (mote_id);

CREATE INDEX IX_MOTEDELTAKER_ARBEIDSTAKER_VARSEL_MD_AT_ID ON MOTEDELTAKER_ARBEIDSTAKER_VARSEL (motedeltaker_arbeidstaker_id);
