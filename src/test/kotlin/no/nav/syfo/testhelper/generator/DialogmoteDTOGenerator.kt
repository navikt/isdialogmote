package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.DocumentComponentType
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.testhelper.UserConstants
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

fun generateEndreDialogmoteTidStedDTO() = EndreTidStedDialogmoteDTO(
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
    personIdentNumber: PersonIdentNumber,
) = NewDialogmotedeltakerArbeidstakerDTO(
    personIdent = personIdentNumber.value,
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
    personIdentNumber: PersonIdentNumber,
) = NewDialogmotedeltakerArbeidstakerDTO(
    personIdent = personIdentNumber.value,
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
    personIdentNumber: PersonIdentNumber,
    sted: String = "This is a very lang text that has a lot of characters and describes where the meeting will take place.",
    dato: LocalDateTime = LocalDateTime.now().plusDays(30),
    virksomhetsnummer: String = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
): NewDialogmoteDTO {
    return NewDialogmoteDTO(
        tildeltEnhet = UserConstants.ENHET_NR.value,
        arbeidstaker = generateMotedeltakerArbeidstakerDTO(personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiverDTO(virksomhetsnummer = virksomhetsnummer),
        tidSted = generateNewDialogmoteTidStedDTO(sted, dato)
    )
}

fun generateNewDialogmoteDTOWithMissingValues(
    personIdentNumber: PersonIdentNumber
): NewDialogmoteDTO {
    return NewDialogmoteDTO(
        tildeltEnhet = UserConstants.ENHET_NR.value,
        arbeidstaker = generateMotedeltakerArbeidstakerDTOMissingValues(personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiverDTOMissingValues(),
        tidSted = generateNewDialogmoteTidStedDTONoVideoLink()
    )
}

fun generateNewDialogmoteDTOWithBehandler(
    personIdentNumber: PersonIdentNumber
): NewDialogmoteDTO {
    return NewDialogmoteDTO(
        tildeltEnhet = UserConstants.ENHET_NR.value,
        arbeidstaker = generateMotedeltakerArbeidstakerDTO(personIdentNumber),
        arbeidsgiver = generateMotedeltakerArbeidsgiverDTO(),
        behandler = generateMotedeltakerBehandlerDTO(),
        tidSted = generateNewDialogmoteTidStedDTONoVideoLink(),
    )
}

fun generateNewReferatDTO(behandlerOppgave: String? = null) =
    NewReferatDTO(
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
    )

fun generateReferatComponentList(): List<DocumentComponentDTO> {
    return listOf(
        DocumentComponentDTO(
            type = DocumentComponentType.HEADER,
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
