package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.dialogmote.MoteEndringType
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

const val queryCreateMoteEndring =
    """
    INSERT INTO MOTE_ENDRING (
        uuid,
        created_at,
        mote_status_endret_id,
        type) VALUES (?, ?, ?, ?)
    """

fun Connection.createMoteEndringer(
    moteStatusEndretId: Int,
    types: Set<MoteEndringType>,
) {
    if (types.isEmpty()) return
    val now = Timestamp.from(Instant.now())
    prepareStatement(queryCreateMoteEndring).use { ps ->
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

const val queryGetMoteEndringTyper =
    """
    SELECT type
    FROM MOTE_ENDRING
    WHERE mote_status_endret_id = ?
    ORDER BY id
    """

fun DatabaseInterface.getMoteEndringTyper(moteStatusEndretId: Int): List<MoteEndringType> =
    connection.use { connection ->
        connection.prepareStatement(queryGetMoteEndringTyper).use { ps ->
            ps.setInt(1, moteStatusEndretId)
            ps.executeQuery().toList { MoteEndringType.valueOf(getString("type")) }
        }
    }
