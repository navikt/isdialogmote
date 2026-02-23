package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.dialogmote.TidStedDTO
import no.nav.syfo.infrastructure.database.model.PTidSted
import java.sql.*
import java.time.Instant
import java.util.UUID

const val queryCreateTidSted =
    """
    INSERT INTO TID_STED (
        id,
        uuid,
        created_at,
        updated_at,
        mote_id,
        sted,
        tid,
        videolink) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createTidSted(
    commit: Boolean = true,
    moteId: Int,
    newDialogmoteTidSted: TidStedDTO,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    val moteTidStedUuid = UUID.randomUUID()
    val moteTidStedIdList = this.prepareStatement(queryCreateTidSted).use {
        it.setString(1, moteTidStedUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, moteId)
        it.setString(5, newDialogmoteTidSted.sted)
        it.setTimestamp(6, Timestamp.valueOf(newDialogmoteTidSted.tid))
        it.setString(7, newDialogmoteTidSted.videoLink.orEmpty())
        it.executeQuery().toList { getInt("id") }
    }

    if (moteTidStedIdList.size != 1) {
        throw SQLException("Creating MoteStatusEndring failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }

    return Pair(moteTidStedIdList.first(), moteTidStedUuid)
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
        videoLink = getString("videolink")
    )
