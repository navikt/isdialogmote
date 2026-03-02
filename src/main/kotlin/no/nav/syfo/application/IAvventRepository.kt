package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import java.util.UUID

interface IAvventRepository {
    fun persist(avvent: Avvent)
    fun getAvvent(uuid: UUID): Avvent?
    fun getActiveAvvent(personident: PersonIdent): Avvent?
    fun setLukket(uuid: UUID)
}
