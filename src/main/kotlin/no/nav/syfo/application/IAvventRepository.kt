package no.nav.syfo.application

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.dialogmote.Avvent
import java.util.UUID

interface IAvventRepository {
    fun persist(avvent: Avvent, transaction: ITransaction? = null): Avvent
    fun getAvvent(uuid: UUID): Avvent?
    fun getActiveAvvent(personident: Personident, transaction: ITransaction? = null): Avvent?
    fun getActiveAvventForPersonidenter(personidenter: List<Personident>): List<Avvent>
    fun setLukket(uuid: UUID, transaction: ITransaction? = null)
}
