package no.nav.syfo.domain.dialogmote

import java.time.LocalDateTime
import java.util.UUID

interface ArbeidstakerBrev {
    val uuid: UUID
    val motedeltakerArbeidstakerId: Int
    val document: List<DocumentComponentDTO>
    val pdfId: Int?
    val lestDatoArbeidstaker: LocalDateTime?
}
