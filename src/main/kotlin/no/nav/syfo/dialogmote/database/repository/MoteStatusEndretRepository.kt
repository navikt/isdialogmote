package no.nav.syfo.dialogmote.database.repository

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMoteStatusEndret
import no.nav.syfo.dialogmote.database.domain.toDialogmoteStatusEndret
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import no.nav.syfo.dialogmote.domain.DialogmoteStatusEndret
import no.nav.syfo.domain.PersonIdent
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.*

class MoteStatusEndretRepository(private val database: DatabaseInterface) {

    fun getMoteStatusEndringer(personident: PersonIdent): List<Triple<DialogmoteStatusEndret, String, Pair<LocalDateTime, String>>> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_MOTE_STATUS_ENDRINGER).use { ps ->
                ps.setString(1, personident.value)
                ps.executeQuery()
                    .toList {
                        Triple(
                            first = toPMoteStatusEndret(),
                            second = getString("mote_opprettet_av"),
                            third = Pair(
                                getTimestamp("tid").toLocalDateTime(),
                                getString("sted")
                            )
                        )
                    }
                    .map {
                        Triple(
                            first = it.first.toDialogmoteStatusEndret(),
                            second = it.second,
                            third = it.third,
                        )
                    }
            }
        }

    companion object {
        private const val GET_MOTE_STATUS_ENDRINGER =
            """
            SELECT
                mse.*,
                m.opprettet_av as mote_opprettet_av,
                ts.tid,
                ts.sted
            FROM mote_status_endret mse
            INNER JOIN mote m on mse.mote_id = m.id
            INNER JOIN tid_sted ts on m.id = ts.mote_id
            INNER JOIN motedeltaker_arbeidstaker mda on m.id = mda.mote_id
            WHERE mda.personident = ?
            ORDER BY mse.created_at DESC
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
        status = DialogmoteStatus.valueOf(getString("status")),
        opprettetAv = getString("opprettet_av"),
        tilfelleStart = getTimestamp("tilfelle_start").toLocalDateTime().toLocalDate(),
        publishedAt = getTimestamp("published_at")?.toLocalDateTime(),
    )
