package no.nav.syfo.dialogmote.domain

import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.createJournalpostRequest
import no.nav.syfo.dialogmote.api.domain.DialogmotedeltakerAnnenDTO
import no.nav.syfo.dialogmote.api.domain.ReferatDTO
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.domain.ArbeidstakerVarselDTO
import java.time.LocalDateTime
import java.util.UUID

data class Referat(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moteId: Int,
    val digitalt: Boolean,
    val situasjon: String,
    val konklusjon: String,
    val arbeidstakerOppgave: String,
    val arbeidsgiverOppgave: String,
    val veilederOppgave: String?,
    val document: List<DocumentComponentDTO>,
    val pdf: ByteArray,
    val journalpostId: String?,
    val lestDatoArbeidstaker: LocalDateTime?,
    val lestDatoArbeidsgiver: LocalDateTime?,
    val andreDeltakere: List<DialogmotedeltakerAnnen>,
)

data class DialogmotedeltakerAnnen(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val funksjon: String,
    val navn: String,
)

fun Referat.toReferatDTO(): ReferatDTO {
    return ReferatDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        digitalt = this.digitalt,
        situasjon = this.situasjon,
        konklusjon = this.konklusjon,
        arbeidstakerOppgave = this.arbeidstakerOppgave,
        arbeidsgiverOppgave = this.arbeidsgiverOppgave,
        veilederOppgave = this.veilederOppgave,
        document = this.document,
        pdf = this.pdf,
        lestDatoArbeidstaker = this.lestDatoArbeidstaker,
        lestDatoArbeidsgiver = this.lestDatoArbeidsgiver,
        andreDeltakere = this.andreDeltakere.map {
            it.toDialogmotedeltakerAnnenDTO()
        }
    )
}

fun DialogmotedeltakerAnnen.toDialogmotedeltakerAnnenDTO(): DialogmotedeltakerAnnenDTO {
    return DialogmotedeltakerAnnenDTO(
        uuid = this.uuid.toString(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        funksjon = this.funksjon,
        navn = this.navn,
    )
}

fun Referat.toJournalforingRequest(
    personIdent: PersonIdentNumber,
) = createJournalpostRequest(
    personIdent = personIdent,
    digitalt = this.digitalt,
    dokumentName = "Referat fra dialogmøte",
    brevkodeType = BrevkodeType.DIALOGMOTE_REFERAT,
    dokumentPdf = this.pdf
)

fun Referat.toArbeidstakerVarselDTO(
    dialogmoteTidSted: DialogmoteTidSted,
    deltakerUuid: UUID,
    virksomhetsnummer: Virksomhetsnummer,
) = ArbeidstakerVarselDTO(
    uuid = this.uuid.toString(),
    deltakerUuid = deltakerUuid.toString(),
    createdAt = this.createdAt,
    varselType = MotedeltakerVarselType.REFERAT.name,
    digitalt = this.digitalt,
    lestDato = this.lestDatoArbeidstaker,
    fritekst = konklusjon ?: "",
    sted = dialogmoteTidSted.sted,
    tid = dialogmoteTidSted.tid,
    videoLink = dialogmoteTidSted.videoLink,
    virksomhetsnummer = virksomhetsnummer.value,
    document = this.document,
)
