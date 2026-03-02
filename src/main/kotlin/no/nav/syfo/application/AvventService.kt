package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import java.util.UUID

class AvventService(
    private val avventRepository: IAvventRepository,
    private val transactionManager: ITransactionManager,
) {
    suspend fun persist(avvent: Avvent) {
        transactionManager.run { transaction ->
            avventRepository.getActiveAvvent(avvent.personident, transaction)?.let {
                avventRepository.setLukket(it.uuid, transaction)
            }
            avventRepository.persist(avvent, transaction)
        }
    }

    fun getAvventForIdenter(personidenter: List<PersonIdent>): List<Avvent> {
        return avventRepository.getActiveAvventForPersonidenter(personidenter)
    }

    fun getAvvent(uuid: UUID): Avvent? = avventRepository.getAvvent(uuid)

    fun lukk(uuid: UUID) {
        avventRepository.setLukket(uuid)
    }
}
