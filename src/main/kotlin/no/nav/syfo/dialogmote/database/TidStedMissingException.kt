package no.nav.syfo.dialogmote.database

class TidStedMissingException(
    message: String = "Cannot create Dialogmote: TidSted is missing"
) : RuntimeException(message)
