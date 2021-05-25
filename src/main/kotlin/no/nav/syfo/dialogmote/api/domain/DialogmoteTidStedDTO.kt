package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.TidStedDTO
import java.time.LocalDateTime

data class DialogmoteTidStedDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    override val sted: String,
    override val tid: LocalDateTime,
    override val videoLink: String?,
) : TidStedDTO()
