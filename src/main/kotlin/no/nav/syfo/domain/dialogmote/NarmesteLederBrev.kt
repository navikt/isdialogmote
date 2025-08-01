package no.nav.syfo.domain.dialogmote

import java.time.LocalDateTime
import java.util.*

interface NarmesteLederBrev {
    val uuid: UUID
    val motedeltakerArbeidsgiverId: Int
    val document: List<DocumentComponentDTO>
    val pdfId: Int?
    val lestDatoArbeidsgiver: LocalDateTime?
    val createdAt: LocalDateTime
}
