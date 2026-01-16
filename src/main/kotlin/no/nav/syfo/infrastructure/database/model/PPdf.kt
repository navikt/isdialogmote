package no.nav.syfo.infrastructure.database.model

import java.time.LocalDateTime
import java.util.UUID

data class PPdf(
    val id: Int,
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val pdf: ByteArray,
)
