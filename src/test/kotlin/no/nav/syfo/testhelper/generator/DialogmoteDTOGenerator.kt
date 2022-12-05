package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.DocumentComponentType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.UserConstants.BEHANDLER_FNR
import no.nav.syfo.testhelper.UserConstants.BEHANDLER_KONTOR
import no.nav.syfo.testhelper.UserConstants.BEHANDLER_NAVN
import no.nav.syfo.testhelper.UserConstants.BEHANDLER_REF
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import java.time.LocalDateTime

fun generateAvlysningDTO() =
    AvlysningDTO(
        begrunnelse = "Passer ikke",
        avlysning = emptyList(),
    )

fun generateAvlysDialogmoteDTO() =
    AvlysDialogmoteDTO(
        arbeidstaker = generateAvlysningDTO(),
        arbeidsgiver = generateAvlysningDTO(),
        behandler = generateAvlysningDTO(),
    )

fun generateAvlysDialogmoteDTONoBehandler() =
    AvlysDialogmoteDTO(
        arbeidstaker = generateAvlysningDTO(),
        arbeidsgiver = generateAvlysningDTO(),
        behandler = null
    )

fun generateNewDialogmoteTidStedDTO(
    sted: String,
    dato: LocalDateTime,
) = NewDialogmoteTidStedDTO(
    sted = sted,
    tid = dato,
    videoLink = "https://meet.google.com/xyz"
)

fun generateNewDialogmoteTidStedDTONoVideoLink() = NewDialogmoteTidStedDTO(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = LocalDateTime.now().plusDays(30),
    videoLink = null
)

fun generateEndreDialogmoteTidStedDTO(
    tid: LocalDateTime = LocalDateTime.now().plusDays(30),
) = EndreTidStedDialogmoteDTO(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = tid,
    videoLink = "https://meet.google.com/xyz",
    arbeidstaker = EndreTidStedBegrunnelseDTO(
        begrunnelse = "",
        endringsdokument = emptyList(),
    ),
    arbeidsgiver = EndreTidStedBegrunnelseDTO(
        begrunnelse = "",
        endringsdokument = emptyList(),
    ),
    behandler = null,
)

fun generateEndreDialogmoteTidStedDTOWithBehandler() = EndreTidStedDialogmoteDTO(
    sted = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    tid = LocalDateTime.now().plusDays(30),
    videoLink = "https://meet.google.com/xyz",
    arbeidstaker = EndreTidStedBegrunnelseDTO(
        begrunnelse = "",
        endringsdokument = emptyList(),
    ),
    arbeidsgiver = EndreTidStedBegrunnelseDTO(
        begrunnelse = "",
        endringsdokument = emptyList(),
    ),
    behandler = EndreTidStedBegrunnelseDTO(
        begrunnelse = "",
        endringsdokument = emptyList(),
    ),
)

fun generateMotedeltakerArbeidstakerDTO(
    personIdent: PersonIdent,
) = NewDialogmotedeltakerArbeidstakerDTO(
    personIdent = personIdent.value,
    fritekstInnkalling = "Ipsum lorum arbeidstaker",
    innkalling = generateDocumentComponentList(),
)

fun generateDocumentComponentList(): List<DocumentComponentDTO> {
    return listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = "Tittel innkalling",
            texts = emptyList(),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = "Møtetid:",
            texts = listOf("5. mai 2021"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = null,
            texts = listOf("Brødtekst"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.LINK,
            title = null,
            texts = listOf("https://nav.no/"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = null,
            texts = listOf("Vennlig hilsen", "NAV Staden", "Kari Saksbehandler"),
        ),
    )
}

fun generateMotedeltakerArbeidstakerDTOMissingValues(
    personIdent: PersonIdent,
) = NewDialogmotedeltakerArbeidstakerDTO(
    personIdent = personIdent.value,
    fritekstInnkalling = null,
    innkalling = emptyList(),
)

fun generateMotedeltakerArbeidsgiverDTO(
    virksomhetsnummer: String = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
) = NewDialogmotedeltakerArbeidsgiverDTO(
    virksomhetsnummer = virksomhetsnummer,
    fritekstInnkalling = "Ipsum lorum arbeidsgiver",
    innkalling = emptyList(),
)

fun generateMotedeltakerBehandlerDTO() = NewDialogmotedeltakerBehandlerDTO(
    personIdent = BEHANDLER_FNR.value,
    behandlerRef = BEHANDLER_REF,
    behandlerNavn = BEHANDLER_NAVN,
    behandlerKontor = BEHANDLER_KONTOR,
    fritekstInnkalling = "Ipsum lorum behandler",
    innkalling = emptyList(),
)

fun generateMotedeltakerArbeidsgiverDTOMissingValues() = NewDialogmotedeltakerArbeidsgiverDTO(
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
    fritekstInnkalling = null,
    innkalling = emptyList(),
)

fun generateNewDialogmoteDTO(
    personIdent: PersonIdent,
    sted: String = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    dato: LocalDateTime = LocalDateTime.now().plusDays(30),
    virksomhetsnummer: String = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
): NewDialogmoteDTO {
    return NewDialogmoteDTO(
        arbeidstaker = generateMotedeltakerArbeidstakerDTO(personIdent),
        arbeidsgiver = generateMotedeltakerArbeidsgiverDTO(virksomhetsnummer = virksomhetsnummer),
        tidSted = generateNewDialogmoteTidStedDTO(sted, dato)
    )
}

fun generateNewDialogmoteDTOWithMissingValues(
    personIdent: PersonIdent
): NewDialogmoteDTO {
    return NewDialogmoteDTO(
        arbeidstaker = generateMotedeltakerArbeidstakerDTOMissingValues(personIdent),
        arbeidsgiver = generateMotedeltakerArbeidsgiverDTOMissingValues(),
        tidSted = generateNewDialogmoteTidStedDTONoVideoLink()
    )
}

fun generateNewDialogmoteDTOWithBehandler(
    personIdent: PersonIdent
): NewDialogmoteDTO {
    return NewDialogmoteDTO(
        arbeidstaker = generateMotedeltakerArbeidstakerDTO(personIdent),
        arbeidsgiver = generateMotedeltakerArbeidsgiverDTO(),
        behandler = generateMotedeltakerBehandlerDTO(),
        tidSted = generateNewDialogmoteTidStedDTONoVideoLink(),
    )
}

fun generateNewReferatDTO(
    behandlerOppgave: String? = null,
    behandlerDeltatt: Boolean? = null,
    behandlerMottarReferat: Boolean? = null,
    begrunnelseEndring: String? = null,
) =
    NewReferatDTO(
        begrunnelseEndring = begrunnelseEndring,
        situasjon = "Dette er en beskrivelse av situasjonen",
        konklusjon = "Dette er en beskrivelse av konklusjon",
        arbeidstakerOppgave = "Dette er en beskrivelse av arbeidstakerOppgave",
        arbeidsgiverOppgave = "Dette er en beskrivelse av arbeidsgiverOppgave",
        veilederOppgave = "Dette er en beskrivelse av veilederOppgave",
        behandlerOppgave = behandlerOppgave,
        narmesteLederNavn = "Grønn Bamse",
        document = generateReferatComponentList(),
        andreDeltakere = listOf(
            NewDialogmotedeltakerAnnenDTO(
                funksjon = "Verneombud",
                navn = "Tøff Pyjamas"
            )
        ),
        behandlerDeltatt = behandlerDeltatt,
        behandlerMottarReferat = behandlerMottarReferat,
    )

fun generateModfisertReferatDTO(
    behandlerOppgave: String? = null,
) =
    NewReferatDTO(
        situasjon = "Dette er en beskrivelse av situasjonen",
        konklusjon = "Dette er en beskrivelse av konklusjon modifisert",
        arbeidstakerOppgave = "Dette er en beskrivelse av arbeidstakerOppgave",
        arbeidsgiverOppgave = "Dette er en beskrivelse av arbeidsgiverOppgave",
        veilederOppgave = "Dette er en beskrivelse av veilederOppgave",
        behandlerOppgave = behandlerOppgave,
        narmesteLederNavn = "Grønn Bamse",
        document = generateReferatComponentList(),
        andreDeltakere = listOf(
            NewDialogmotedeltakerAnnenDTO(
                funksjon = "Verneombud",
                navn = "Tøffere Pyjamas"
            )
        ),
        behandlerMottarReferat = true,
        behandlerDeltatt = true,
    )

fun generateReferatComponentList(): List<DocumentComponentDTO> {
    return listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.HEADER_H1,
            title = null,
            texts = listOf("Tittel referat"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            title = null,
            texts = listOf("Brødtekst"),
        ),
        DocumentComponentDTO(
            type = DocumentComponentType.PARAGRAPH,
            key = "Standardtekst",
            title = null,
            texts = listOf("Dette er en standardtekst"),
        ),
    )
}
