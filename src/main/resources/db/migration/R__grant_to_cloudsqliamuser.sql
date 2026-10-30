REVOKE ALL ON ALL TABLES IN SCHEMA public FROM cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "isyfo-analyse";
--GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
-- update privileges for new tables
-- added table moteendring in V9_23__create_table_moteendring.sql
