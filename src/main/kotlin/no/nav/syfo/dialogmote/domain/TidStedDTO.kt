package no.nav.syfo.dialogmote.domain

import java.time.LocalDateTime

abstract class TidStedDTO {
    abstract val sted: String
    abstract val tid: LocalDateTime
    abstract val videoLink: String?
}
