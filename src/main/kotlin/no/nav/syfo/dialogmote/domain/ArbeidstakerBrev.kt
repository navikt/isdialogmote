package no.nav.syfo.dialogmote.domain

import java.time.LocalDateTime
import java.util.UUID

interface ArbeidstakerBrev {
    val uuid: UUID
    val motedeltakerArbeidstakerId: Int
    val document: List<DocumentComponentDTO>
    val pdfId: Int?
    val lestDatoArbeidstaker: LocalDateTime?
}
