REVOKE ALL ON ALL TABLES IN SCHEMA public FROM cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "isyfo-analyse";
GRANT SELECT, INSERT, UPDATE, DELETE ON MOTEDELTAKER_BEHANDLER_VARSEL_SVAR TO cloudsqliamuser;
GRANT SELECT ON MOTEDELTAKER_BEHANDLER_VARSEL_SVAR TO "isyfo-analyse";
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
