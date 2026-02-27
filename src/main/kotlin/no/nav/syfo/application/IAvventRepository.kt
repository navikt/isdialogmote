package no.nav.syfo.application

import no.nav.syfo.domain.dialogmote.Avvent

interface IAvventRepository {
    fun persist(avvent: Avvent)
}
