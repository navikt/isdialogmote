package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import java.sql.*
import java.time.Instant
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
        opprettet_av) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createMoteStatusEndring(
    commit: Boolean = true,
    moteId: Int,
    opprettetAv: String,
    status: DialogmoteStatus
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    val moteStatusEndringUuid = UUID.randomUUID()

    val moteStatusEndringIdList = this.prepareStatement(queryCreateMoteStatusEndring).use {
        it.setString(1, moteStatusEndringUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, moteId)
        it.setString(5, status.name)
        it.setString(6, opprettetAv)
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
