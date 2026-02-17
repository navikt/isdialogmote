package no.nav.syfo.application

import no.nav.syfo.infrastructure.database.UnitOfWork
import no.nav.syfo.infrastructure.database.model.PPdf

interface IPdfRepository {

    fun getPdf(id: Int): PPdf

    fun createPdf(
        uow: UnitOfWork,
        pdf: ByteArray,
    ): Pair<Int, java.util.UUID>
}
