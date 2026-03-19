package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent

class AvventService(
    private val avventRepository: IAvventRepository,
    private val transactionManager: ITransactionManager,
) {
    suspend fun persist(avvent: Avvent): Avvent =
        transactionManager.run { transaction ->
            avventRepository.getActiveAvvent(avvent.personident, transaction)?.let {
                avventRepository.setLukket(it.uuid, transaction)
            }
            avventRepository.persist(avvent, transaction)
        }

    fun getAvventForIdenter(personidenter: List<PersonIdent>): List<Avvent> {
        return avventRepository.getActiveAvventForPersonidenter(personidenter)
    }

    suspend fun lukkAvvent(personident: PersonIdent) {
        transactionManager.run { transaction ->
            avventRepository.getActiveAvvent(personident, transaction)?.let {
                avventRepository.setLukket(it.uuid, transaction)
            }
        }
    }
}
