package no.nav.syfo.application

import no.nav.syfo.infrastructure.database.model.PPdf
import java.sql.Connection

interface IPdfRepository {

    suspend fun getPdf(id: Int): PPdf

    suspend fun createPdf(
        connection: Connection, // To be removed
        commit: Boolean = true,
        pdf: ByteArray,
    ): Pair<Int, java.util.UUID>
}
