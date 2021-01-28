package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.domain.Dialogmote
import no.nav.syfo.domain.PersonIdentNumber
import org.postgresql.util.PSQLException
import java.sql.*
import java.time.Instant
import java.util.*

const val queryGetDialogmoteListForPersonIdent =
    """
        SELECT *
        FROM MOTE
        left join MOTEDELTAKER_ARBEIDSTAKER on MOTEDELTAKER_ARBEIDSTAKER.mote_id = MOTE.id
        WHERE personident = ?
    """

fun DatabaseInterface.getDialogmoteList(personIdentNumber: PersonIdentNumber): List<PDialogmote> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteListForPersonIdent).use {
            it.setString(1, personIdentNumber.value)
            it.executeQuery().toList { toPDialogmote() }
        }
    }
}

const val queryCreateDialogmote =
    """
    INSERT INTO MOTE (
        id,
        uuid,
        created_at,
        updated_at,
        planlagtmote_uuid,
        status,
        opprettet_av,
        tildelt_veileder_ident,
        tildelt_enhet) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun DatabaseInterface.createDialogmote(
    dialogmote: Dialogmote
): Pair<Int, UUID> {
    val uuid = UUID.randomUUID().toString()
    val now = Timestamp.from(Instant.now())

    this.connection.use { connection ->
        val moteIdList = connection.prepareStatement(queryCreateDialogmote).use {
            it.setString(1, uuid)
            it.setTimestamp(2, now)
            it.setTimestamp(3, now)
            it.setString(4, dialogmote.planlagtMoteUuid.toString())
            it.setString(5, dialogmote.status.name)
            it.setString(6, dialogmote.opprettetAv)
            it.setString(7, dialogmote.tildeltVeilederIdent)
            it.setString(8, dialogmote.tildeltEnhet)
            it.executeQuery().toList { getInt("id") }
        }
        if (moteIdList.size != 1) {
            throw SQLException("Creating Dialogmote failed, no rows affected.")
        }
        connection.commit()

        val moteId = moteIdList.first()
        try {
            this.createMotedeltakerArbeidstaker(
                moteId,
                dialogmote.arbeidstaker.personIdent,
            )
            this.createMotedeltakerArbeidsgiver(
                moteId,
                dialogmote.arbeidsgiver,
            )
            this.createMoteStatusEndring(
                moteId = moteId,
                opprettetAv = dialogmote.opprettetAv,
                status = dialogmote.status,
            )
        } catch (e: Exception) {
            when (e) {
                is SQLException, is PSQLException -> connection.rollback()
                else -> throw e
            }
        }

        return Pair(moteId, UUID.fromString(uuid))
    }
}

fun ResultSet.toPDialogmote(): PDialogmote =
    PDialogmote(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        planlagtMoteUuid = UUID.fromString(getString("planlagtmote_uuid")),
        status = getString("status"),
        opprettetAv = getString("opprettet_av"),
        tildeltVeilederIdent = getString("tildelt_veileder_ident"),
        tildeltEnhet = getString("tildelt_enhet")
    )
