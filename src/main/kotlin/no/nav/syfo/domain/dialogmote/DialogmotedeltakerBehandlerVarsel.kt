package no.nav.syfo.domain.dialogmote

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.dokarkiv.domain.DialogmoteDeltakerType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostKanal
import no.nav.syfo.infrastructure.client.dokarkiv.domain.createJournalpostRequest
import java.time.LocalDateTime
import java.util.*

data class DialogmotedeltakerBehandlerVarsel(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val motedeltakerBehandlerId: Int,
    val varselType: MotedeltakerVarselType,
    val pdfId: Int,
    val status: String,
    val fritekst: String,
    val document: List<DocumentComponentDTO>,
    val svar: List<DialogmotedeltakerBehandlerVarselSvar>,
)

fun DialogmotedeltakerBehandlerVarsel.toJournalpostRequest(
    brukerPersonIdent: PersonIdent,
    behandlerPersonIdent: PersonIdent?,
    behandlerHprId: Int?,
    behandlerNavn: String,
    pdf: ByteArray,
) = createJournalpostRequest(
    brukerPersonIdent = brukerPersonIdent,
    mottakerPersonIdent = behandlerPersonIdent,
    mottakerHprId = behandlerHprId,
    mottakerNavn = behandlerNavn,
    digitalt = true,
    dokumentName = this.varselType.toJournalpostTittel(),
    brevkodeType = this.varselType.toBrevkodeType(DialogmoteDeltakerType.BEHANDLER),
    dokumentPdf = pdf,
    kanal = JournalpostKanal.HELSENETTET,
    varselUuid = this.uuid,
)
