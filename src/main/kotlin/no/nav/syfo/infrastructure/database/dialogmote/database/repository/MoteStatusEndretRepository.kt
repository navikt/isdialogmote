package no.nav.syfo.infrastructure.database.dialogmote.database.repository

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.api.dto.DialogmoteStatusEndringDTO
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.PMoteStatusEndret
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Dialogmote
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.*

class MoteStatusEndretRepository(private val database: DatabaseInterface) {

    fun getMoteStatusEndringer(personident: PersonIdent): List<DialogmoteStatusEndringDTO> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_MOTE_STATUS_ENDRINGER).use { ps ->
                ps.setString(1, personident.value)
                ps.executeQuery()
                    .toList {
                        val pDialogmoteStatusEndret = toPMoteStatusEndret()
                        DialogmoteStatusEndringDTO(
                            uuid = pDialogmoteStatusEndret.uuid.toString(),
                            createdAt = pDialogmoteStatusEndret.createdAt,
                            dialogmoteId = pDialogmoteStatusEndret.moteId,
                            dialogmoteOpprettetAv = getString("mote_opprettet_av"),
                            status = pDialogmoteStatusEndret.status,
                            statusEndringOpprettetAv = pDialogmoteStatusEndret.opprettetAv,
                        )
                    }
            }
        }

    fun getMoteStatusEndretNotPublished(): List<PMoteStatusEndret> {
        return database.connection.use { connection ->
            connection.prepareStatement(GET_MOTE_STATUS_ENDRET_NOT_PUBLISHED).use {
                it.executeQuery().toList { toPMoteStatusEndret() }
            }
        }
    }

    fun updateMoteStatusEndretPublishedAt(moteStatusEndretId: Int) {
        val now = Timestamp.from(Instant.now())
        database.connection.use { connection ->
            connection.prepareStatement(UPDATE_MOTE_STATUS_ENDRET_PUBLISHED_AT).use {
                it.setTimestamp(1, now)
                it.setTimestamp(2, now)
                it.setInt(3, moteStatusEndretId)
                it.execute()
            }
            connection.commit()
        }
    }

    fun createMoteStatusEndring(
        connection: Connection,
        commit: Boolean = true,
        moteId: Int,
        opprettetAv: String,
        status: Dialogmote.Status,
        tilfelleStart: LocalDate?,
        isBehandlerMotedeltaker: Boolean,
    ): Pair<Int, UUID> {
        val now = Timestamp.from(Instant.now())
        val startDate = tilfelleStart ?: LocalDate.EPOCH

        val moteStatusEndringUuid = UUID.randomUUID()

        val moteStatusEndringIdList = connection.prepareStatement(CREATE_MOTE_STATUS_ENDRING).use {
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
            connection.commit()
        }

        return Pair(moteStatusEndringIdList.first(), moteStatusEndringUuid)
    }

    companion object {
        private const val GET_MOTE_STATUS_ENDRINGER =
            """
            SELECT
                mse.*,
                m.opprettet_av as mote_opprettet_av
            FROM mote_status_endret mse
            INNER JOIN mote m on mse.mote_id = m.id
            INNER JOIN motedeltaker_arbeidstaker mda on m.id = mda.mote_id
            WHERE mda.personident = ?
            ORDER BY mse.created_at DESC
            """

        private const val CREATE_MOTE_STATUS_ENDRING =
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

        private const val GET_MOTE_STATUS_ENDRET_NOT_PUBLISHED =
            """
            SELECT *
            FROM MOTE_STATUS_ENDRET
            WHERE published_at IS NULL
            ORDER BY created_at ASC LIMIT 100
            """

        private const val UPDATE_MOTE_STATUS_ENDRET_PUBLISHED_AT =
            """
            UPDATE MOTE_STATUS_ENDRET
            SET published_at = ?, updated_at = ?
            WHERE id = ?
            """
    }
}

internal fun ResultSet.toPMoteStatusEndret(): PMoteStatusEndret =
    PMoteStatusEndret(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteId = getInt("mote_id"),
        motedeltakerBehandler = getBoolean("motedeltaker_behandler"),
        status = Dialogmote.Status.valueOf(getString("status")),
        opprettetAv = getString("opprettet_av"),
        tilfelleStart = getTimestamp("tilfelle_start").toLocalDateTime().toLocalDate(),
        publishedAt = getTimestamp("published_at")?.toLocalDateTime(),
    )
