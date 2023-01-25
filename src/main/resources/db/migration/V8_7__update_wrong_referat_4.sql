UPDATE MOTE_REFERAT
    SET situasjon = '[Teksten er fjernet]',
        konklusjon = '.',
        arbeidstaker_oppgave = '.',
        arbeidsgiver_oppgave = '.',
        veileder_oppgave = null,
        document = '[{"key": null,"type": "HEADER_H1","texts": ["Referat fra dialogmøte"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["-"],"title": null},{"key": null,"type": "HEADER_H2","texts": ["[Arbeidstaker]"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["F.nr. -"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["-"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["Videolink"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["Arbeidstaker: -","Fra NAV: -","Fra arbeidsgiver: -","Behandler: Fastlege -"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["[Arbeidsgiver]"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["Formålet med dialogmøtet var å oppsummere situasjonen, drøfte mulighetene for å arbeide og legge en plan for tiden framover."],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["Sykdom og diagnose er underlagt taushetsplikt. Derfor er helsen din bare et tema hvis du selv velger å være åpen om den. Av hensyn til personvernet inneholder referatet uansett ikke slike opplysninger. Se artikkel 9, Lov om behandling av personopplysninger."],"title": null},{"key": null,"type": "HEADER_H2","texts": ["Dette skjedde i møtet"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["."],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["."],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["."],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["."],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["."],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["[Teksten er fjernet]"],"title": null},{"key": null,"type": "HEADER_H2","texts": ["Dette informerte NAV om i møtet"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["[Teksten er fjernet]"],"title": null},{"key": null,"type": "PARAGRAPH","texts": ["Vennlig hilsen","[Veileder]","NAV"],"title": null}]',
        behandler_oppgave = null
    WHERE uuid = 'b54e558d-624c-4407-83c9-081dd2f76547';

UPDATE PDF
    SET pdf = ''::bytea
    WHERE uuid = '7caa48f9-0d53-49fd-a539-1cd78f6a77a9';
