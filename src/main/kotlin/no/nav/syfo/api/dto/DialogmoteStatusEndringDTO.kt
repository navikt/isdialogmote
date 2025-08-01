package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import java.time.LocalDateTime

data class DialogmoteStatusEndringDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val dialogmoteId: Int,
    val dialogmoteOpprettetAv: String,
    val status: DialogmoteStatus,
    val statusEndringOpprettetAv: String,
)
