package no.nav.syfo.dialogmote.domain

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
