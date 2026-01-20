package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.application.IPdfRepository
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.assertThatExactlyOneElement
import no.nav.syfo.infrastructure.database.model.PPdf
import no.nav.syfo.infrastructure.database.toList
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class PdfRepository(private val database: DatabaseInterface) : IPdfRepository {

    override suspend fun getPdf(id: Int): PPdf {
        val pPdfList = database.connection.use { connection ->
            connection.prepareStatement(GET_PDF_QUERY).use {
                it.setInt(1, id)
                it.executeQuery().toList { toPPdf() }
            }
        }
        pPdfList.assertThatExactlyOneElement(
            errorMessageIfEmpty = "No pdf found for mote with id",
            errorMessageIfMoreThanOne = "More than one pdf found for mote with id",
        )
        return pPdfList.first()
    }

    override suspend fun createPdf(
        connection: Connection,
        commit: Boolean,
        pdf: ByteArray,
    ): Pair<Int, UUID> {
        val now = Timestamp.from(Instant.now())
        val pdfUuid = UUID.randomUUID()
        val pdfIdList = connection.prepareStatement(CREATE_PDF_QUERY).use {
            it.setString(1, pdfUuid.toString())
            it.setTimestamp(2, now)
            it.setTimestamp(3, now)
            it.setBytes(4, pdf)
            it.executeQuery().toList { getInt("id") }
        }
        if (commit) {
            connection.commit()
        }
        return Pair(pdfIdList.first(), pdfUuid)
    }

    private fun ResultSet.toPPdf(): PPdf =
        PPdf(
            id = getInt("id"),
            uuid = UUID.fromString(getString("uuid")),
            createdAt = getTimestamp("created_at").toLocalDateTime(),
            updatedAt = getTimestamp("updated_at").toLocalDateTime(),
            pdf = getBytes("pdf"),
        )

    companion object {
        private const val GET_PDF_QUERY =
            """
                SELECT *
                FROM PDF
                WHERE id = ?
            """

        private const val CREATE_PDF_QUERY =
            """
                INSERT INTO PDF (
                    id,
                    uuid,
                    created_at,
                    updated_at,
                    pdf
                ) VALUES (DEFAULT, ?, ?, ?, ?) 
                RETURNING id
            """
    }
}
