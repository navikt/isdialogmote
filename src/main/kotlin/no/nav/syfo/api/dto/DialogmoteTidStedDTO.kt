package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.TidStedDTO
import java.time.LocalDateTime

data class DialogmoteTidStedDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    override val sted: String,
    override val tid: LocalDateTime,
    override val videoLink: String?,
) : TidStedDTO()
