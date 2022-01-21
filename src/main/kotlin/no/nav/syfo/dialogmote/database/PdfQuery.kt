package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PPdf
import java.sql.*
import java.time.Instant
import java.util.UUID

const val queryCreatePdf =
    """
    INSERT INTO PDF (
        id,
        uuid,
        created_at,
        updated_at,
        pdf) VALUES (DEFAULT, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createPdf(
    commit: Boolean = true,
    pdf: ByteArray,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())
    val pdfUuid = UUID.randomUUID()
    val pdfIdList = this.prepareStatement(queryCreatePdf).use {
        it.setString(1, pdfUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setBytes(4, pdf)
        it.executeQuery().toList { getInt("id") }
    }
    if (commit) {
        this.commit()
    }

    return Pair(pdfIdList.first(), pdfUuid)
}

const val queryGetPdf =
    """
        SELECT *
        FROM PDF
        WHERE id = ?
    """

fun DatabaseInterface.getPdf(id: Int): PPdf {
    val pPdfList = this.connection.use { connection ->
        connection.prepareStatement(queryGetPdf).use {
            it.setInt(1, id)
            it.executeQuery().toList { toPPdf() }
        }
    }
    pPdfList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "No pdf found for mote with id $id",
        errorMessageIfMoreThanOne = "More than one pdf found for mote with id $id",
    )
    return pPdfList.first()
}

fun ResultSet.toPPdf(): PPdf =
    PPdf(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        pdf = getBytes("pdf"),
    )
