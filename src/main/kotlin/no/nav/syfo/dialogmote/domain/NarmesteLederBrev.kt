package no.nav.syfo.dialogmote.domain

import java.time.LocalDateTime
import java.util.UUID

interface NarmesteLederBrev {
    val uuid: UUID
    val motedeltakerArbeidsgiverId: Int
    val document: List<DocumentComponentDTO>
    val pdf: ByteArray
    val lestDatoArbeidsgiver: LocalDateTime?
}