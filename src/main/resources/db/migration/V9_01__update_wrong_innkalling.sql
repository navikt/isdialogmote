UPDATE MOTDELTAKER_ARBEIDSTAKER_VARSEL
    SET document = '[{"key": null, "type": "HEADER_H1", "texts": ["Innkalling til dialogm□te"], "title": null}, {"key": null, "typ e": "PARAGRAPH", "texts": ["Sendt 16. juni 2023, kl. 07.24"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["Tirsdag 27. juni 2023 kl. 12.00"], "title": "M□tetidspunkt"}, {"key ": null, "type": "PARAGRAPH", "texts": ["NAV Ullensaker, Trondheimsvegen 82, 2050 Jessheim "], "title": "M□tested"}, {"key": null, "type": "PARAGRAPH", "texts": [""], "title": "Arbeidsgiver"}, {"key": null, "type": "PARAGRAPH", "texts": ["Hei"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["Feilsendt innkalling - innholdet er slettet"], "title": null}, {"key": null, "type": " PARAGRAPH", "texts": [""], "title": null}, {"key": null, "type": "PARAGRAP H", "texts": [""], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": [""], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": [""], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": [""], "title": "F□r m□tet"}, {"key": null, "type": "PARAGRAP H", "texts": ["Vennlig hilsen", "Veileder", "NAV"], "title": null}] ',
    WHERE uuid = '1dbfea71-1378-471f-b4bd-836e7068f05c';

UPDATE PDF
    SET pdf = ''::bytea
    WHERE uuid = '65ec56ef-519e-4c6e-8f55-c598057882d8';