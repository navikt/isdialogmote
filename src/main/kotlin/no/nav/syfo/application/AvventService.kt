package no.nav.syfo.application

import no.nav.syfo.domain.dialogmote.Avvent

class AvventService(
    val avventRepository: IAvventRepository,
) {
        fun persist(avvent: Avvent) {
            avventRepository.persist(avvent)
        }

}
