UPDATE MOTE_REFERAT
SET situasjon = '[Teksten er fjernet]',
    konklusjon = '.',
    arbeidstaker_oppgave = '.',
    arbeidsgiver_oppgave = '.',
    veileder_oppgave = null,
    document = '[{"key": null, "type": "HEADER_H1", "texts": ["Referat fra dialogmøte"], "title": null}, {"key": null, "type": "HEADER_H2", "texts": ["-"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["F.nr. -"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": "Møtetidspunkt"}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": "Møtested"}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": "Deltakere i møtet"}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": "Arbeidsgiver"}, {"key": null, "type": "PARAGRAPH", "texts": ["Formålet med dialogmøtet var å oppsummere situasjonen, drøfte mulighetene for å arbeide og legge en plan for tiden framover."], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["Sykdom og diagnose er underlagt taushetsplikt. Derfor er helsen din bare et tema hvis du selv velger å være åpen om den. Av hensyn til personvernet inneholder referatet uansett ikke slike opplysninger. Se artikkel 9, Lov om behandling av personopplysninger."], "title": null}, {"key": null, "type": "HEADER_H2", "texts": ["Dette skjedde i møtet"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["-"], "title": null}, {"key": null, "type": "PARAGRAPH", "texts": ["Med vennlig hilsen", "[Veileder]", "Nav"], "title": null}]',
    behandler_oppgave = null
WHERE uuid = 'b5c87dad-841d-4589-9335-eda92a075e33';

UPDATE PDF
    SET pdf = ''::bytea
    WHERE uuid = '41a7766b-e5af-4734-8459-a390cca0a508';
