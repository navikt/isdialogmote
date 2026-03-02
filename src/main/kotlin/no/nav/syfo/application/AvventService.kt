package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent

class AvventService(
    private val avventRepository: IAvventRepository,
) {
    fun persist(avvent: Avvent) {
        // TODO: wrap in transaction
        avventRepository.getActiveAvvent(avvent.personident)?.let {
            avventRepository.setLukket(it.uuid)
        }
        avventRepository.persist(avvent)
    }

    fun getAvventForIdenter(personidenter: List<PersonIdent>): List<Avvent> {
        return personidenter.mapNotNull { personident ->
            avventRepository.getActiveAvvent(personident)
        }
    }
}
