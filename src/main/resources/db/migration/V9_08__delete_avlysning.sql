DELETE
FROM motedeltaker_arbeidstaker_varsel
WHERE id = '221126';

DELETE
FROM motedeltaker_arbeidsgiver_varsel
WHERE id = '221126';

DELETE
FROM mote_status_endret
WHERE id = '304416';

DELETE
FROM pdf
WHERE id IN ('678784', '678785');

UPDATE mote
SET status = 'INNKALT'
WHERE id = '127415';
