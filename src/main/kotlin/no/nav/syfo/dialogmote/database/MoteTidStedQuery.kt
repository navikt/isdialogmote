package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PTidSted
import no.nav.syfo.dialogmote.domain.NewDialogmoteTidSted
import java.sql.*
import java.time.Instant
import java.util.*

const val queryCreateTidSted =
    """
    INSERT INTO TID_STED (
        id,
        uuid,
        created_at,
        updated_at,
        mote_id,
        sted,
        tid) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun DatabaseInterface.createTidSted(
    moteId: Int,
    newDialogmoteTidSted: NewDialogmoteTidSted,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    this.connection.use { connection ->
        val moteTidStedUuid = UUID.randomUUID()
        val moteTidStedIdList = connection.prepareStatement(queryCreateTidSted).use {
            it.setString(1, moteTidStedUuid.toString())
            it.setTimestamp(2, now)
            it.setTimestamp(3, now)
            it.setInt(4, moteId)
            it.setString(5, newDialogmoteTidSted.sted)
            it.setTimestamp(6, Timestamp.valueOf(newDialogmoteTidSted.tid))
            it.executeQuery().toList { getInt("id") }
        }

        if (moteTidStedIdList.size != 1) {
            throw SQLException("Creating MoteStatusEndring failed, no rows affected.")
        }

        connection.commit()

        return Pair(moteTidStedIdList.first(), moteTidStedUuid)
    }
}

const val queryGetTidStedForMote =
    """
        SELECT *
        FROM TID_STED
        WHERE mote_id = ?
    """

fun DatabaseInterface.getTidSted(moteId: Int): List<PTidSted> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetTidStedForMote).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPTidSted() }
        }
    }
}

fun ResultSet.toPTidSted(): PTidSted =
    PTidSted(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteId = getInt("mote_id"),
        sted = getString("sted"),
        tid = getTimestamp("tid").toLocalDateTime(),
    )
