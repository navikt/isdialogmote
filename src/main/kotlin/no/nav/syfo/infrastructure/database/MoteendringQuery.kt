package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.dialogmote.MoteendringType
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

const val queryCreateMoteendring =
    """
    INSERT INTO MOTEENDRING (
        uuid,
        created_at,
        mote_status_endret_id,
        type) VALUES (?, ?, ?, ?)
    """

fun Connection.createMoteendringer(
    moteStatusEndretId: Int,
    types: Set<MoteendringType>,
) {
    if (types.isEmpty()) return
    val now = Timestamp.from(Instant.now())
    prepareStatement(queryCreateMoteendring).use { ps ->
        types.forEach { type ->
            ps.setString(1, UUID.randomUUID().toString())
            ps.setTimestamp(2, now)
            ps.setInt(3, moteStatusEndretId)
            ps.setString(4, type.name)
            ps.addBatch()
        }
        ps.executeBatch()
    }
}

const val queryGetMoteendringTyper =
    """
    SELECT type
    FROM MOTEENDRING
    WHERE mote_status_endret_id = ?
    ORDER BY id
    """

fun DatabaseInterface.getMoteendringTyper(moteStatusEndretId: Int): List<MoteendringType> =
    connection.use { connection ->
        connection.prepareStatement(queryGetMoteendringTyper).use { ps ->
            ps.setInt(1, moteStatusEndretId)
            ps.executeQuery().toList { MoteendringType.valueOf(getString("type")) }
        }
    }
