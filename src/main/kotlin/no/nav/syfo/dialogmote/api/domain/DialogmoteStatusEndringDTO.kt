package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import java.time.LocalDateTime

data class DialogmoteStatusEndringDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val dialogmoteId: Int,
    val dialogmoteOpprettetAv: String,
    val status: DialogmoteStatus,
    val statusEndringOpprettetAv: String,
)
