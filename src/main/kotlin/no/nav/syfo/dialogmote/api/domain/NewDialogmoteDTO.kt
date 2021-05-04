package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.*
import java.time.LocalDateTime

data class NewDialogmoteDTO(
    val tildeltEnhet: String,
    val arbeidstaker: NewDialogmotedeltakerArbeidstakerDTO,
    val arbeidsgiver: NewDialogmotedeltakerArbeidsgiverDTO,
    val tidSted: NewDialogmoteTidStedDTO,
)

data class NewDialogmotedeltakerArbeidstakerDTO(
    val personIdent: String,
    val fritekstInnkalling: String?,
    val innkalling: List<DocumentComponentDTO>,
)

data class NewDialogmotedeltakerArbeidsgiverDTO(
    val virksomhetsnummer: String,
    val fritekstInnkalling: String?,
    val innkalling: List<DocumentComponentDTO>,
)

data class DocumentComponentDTO(
    val type: DocumentComponentType,
    val data: String,
)

enum class DocumentComponentType {
    DOCUMENT_COMPONENT_TITLE,
    DOCUMENT_COMPONENT_HEADER,
    DOCUMENT_COMPONENT_SUBHEADER,
    DOCUMENT_COMPONENT_TIME_TITLE,
    DOCUMENT_COMPONENT_PLACE_TITLE,
    DOCUMENT_COMPONENT_PARAGRAPH,
    DOCUMENT_COMPONENT_LINK,
    DOCUMENT_COMPONENT_VIDEOLINK,
}

data class NewDialogmoteTidStedDTO(
    val sted: String,
    val tid: LocalDateTime,
    val videoLink: String?,
)

fun NewDialogmoteDTO.toNewDialogmote(
    requestByNAVIdent: String,
    narmesteLeder: NarmesteLederDTO,
    navEnhet: EnhetNr,
): NewDialogmote {
    return NewDialogmote(
        status = DialogmoteStatus.INNKALT,
        tildeltVeilederIdent = requestByNAVIdent,
        tildeltEnhet = navEnhet.value,
        opprettetAv = requestByNAVIdent,
        arbeidstaker = NewDialogmotedeltakerArbeidstaker(
            personIdent = PersonIdentNumber(this.arbeidstaker.personIdent),
            fritekstInnkalling = this.arbeidstaker.fritekstInnkalling,
        ),
        arbeidsgiver = NewDialogmotedeltakerArbeidsgiver(
            virksomhetsnummer = Virksomhetsnummer(this.arbeidsgiver.virksomhetsnummer),
            fritekstInnkalling = this.arbeidsgiver.fritekstInnkalling,
            lederNavn = narmesteLeder.navn,
            lederEpost = narmesteLeder.epost,
        ),
        tidSted = NewDialogmoteTidSted(
            sted = tidSted.sted,
            tid = tidSted.tid,
            videoLink = tidSted.videoLink,
        )
    )
}
