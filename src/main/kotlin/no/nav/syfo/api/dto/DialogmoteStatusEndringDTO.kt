package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.Dialogmote
import java.time.LocalDateTime

data class DialogmoteStatusEndringDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val dialogmoteId: Int,
    val dialogmoteOpprettetAv: String,
    val status: Dialogmote.Status,
    val statusEndringOpprettetAv: String,
)
