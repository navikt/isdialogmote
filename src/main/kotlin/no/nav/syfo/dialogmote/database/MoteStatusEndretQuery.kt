package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMoteStatusEndret
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import java.sql.*
import java.time.*
import java.util.*

const val queryCreateMoteStatusEndring =
    """
    INSERT INTO MOTE_STATUS_ENDRET (
        id,
        uuid,
        created_at,
        updated_at,
        mote_id,
        status,
        opprettet_av,
        tilfelle_start,
        motedeltaker_behandler
        ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createMoteStatusEndring(
    commit: Boolean = true,
    moteId: Int,
    opprettetAv: String,
    status: DialogmoteStatus,
    tilfelleStart: LocalDate?,
    isBehandlerMotedeltaker: Boolean,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())
    val startDate = tilfelleStart ?: LocalDate.EPOCH

    val moteStatusEndringUuid = UUID.randomUUID()

    val moteStatusEndringIdList = this.prepareStatement(queryCreateMoteStatusEndring).use {
        it.setString(1, moteStatusEndringUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, moteId)
        it.setString(5, status.name)
        it.setString(6, opprettetAv)
        it.setTimestamp(7, Timestamp.valueOf(startDate.atStartOfDay()))
        it.setBoolean(8, isBehandlerMotedeltaker)
        it.executeQuery().toList { getInt("id") }
    }

    if (moteStatusEndringIdList.size != 1) {
        throw SQLException("Creating MoteStatusEndring failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }

    return Pair(moteStatusEndringIdList.first(), moteStatusEndringUuid)
}

const val queryMoteStatusEndretNotPublished =
    """
        SELECT *
        FROM MOTE_STATUS_ENDRET
        WHERE published_at IS NULL
        ORDER BY created_at ASC LIMIT 100
    """

fun DatabaseInterface.getMoteStatusEndretNotPublished(): List<PMoteStatusEndret> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryMoteStatusEndretNotPublished).use {
            it.executeQuery().toList { toPMoteStatusEndret() }
        }
    }
}

const val queryUpdateMoteStatusEndretPublishedAt =
    """
        UPDATE MOTE_STATUS_ENDRET
        SET published_at = ?, updated_at = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateMoteStatusEndretPublishedAt(
    moteStatusEndretId: Int,
) {
    val now = Timestamp.from(Instant.now())
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateMoteStatusEndretPublishedAt).use {
            it.setTimestamp(1, now)
            it.setTimestamp(2, now)
            it.setInt(3, moteStatusEndretId)
            it.execute()
        }
        connection.commit()
    }
}

fun ResultSet.toPMoteStatusEndret(): PMoteStatusEndret =
    PMoteStatusEndret(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteId = getInt("mote_id"),
        motedeltakerBehandler = getBoolean("motedeltaker_behandler"),
        status = DialogmoteStatus.valueOf(getString("status")),
        opprettetAv = getString("opprettet_av"),
        tilfelleStart = getTimestamp("tilfelle_start").toLocalDateTime().toLocalDate(),
        publishedAt = getTimestamp("published_at")?.toLocalDateTime(),
    )
