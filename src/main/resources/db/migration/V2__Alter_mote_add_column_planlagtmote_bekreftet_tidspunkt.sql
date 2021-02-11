-- ROLLBACK-START
------------------
-- ALTER TABLE MOTE DROP planlagtmote_bekreftet;
---------------
-- ROLLBACK-END

ALTER TABLE MOTE
ADD COLUMN planlagtmote_bekreftet_tidspunkt TIMESTAMP;
