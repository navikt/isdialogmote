package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PDialogmote
import no.nav.syfo.dialogmote.domain.NewDialogmote
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
    newDialogmote: NewDialogmote
): Pair<Int, UUID> {
    val uuid = UUID.randomUUID().toString()
    val now = Timestamp.from(Instant.now())

    this.connection.use { connection ->
        val moteIdList = connection.prepareStatement(queryCreateDialogmote).use {
            it.setString(1, uuid)
            it.setTimestamp(2, now)
            it.setTimestamp(3, now)
            it.setString(4, newDialogmote.planlagtMoteUuid.toString())
            it.setString(5, newDialogmote.status.name)
            it.setString(6, newDialogmote.opprettetAv)
            it.setString(7, newDialogmote.tildeltVeilederIdent)
            it.setString(8, newDialogmote.tildeltEnhet)
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
                newDialogmote.arbeidstaker.personIdent,
            )
            this.createMotedeltakerArbeidsgiver(
                moteId,
                newDialogmote.arbeidsgiver,
            )
            this.createTidSted(
                moteId = moteId,
                newDialogmoteTidSted = newDialogmote.tidSted
            )
            this.createMoteStatusEndring(
                moteId = moteId,
                opprettetAv = newDialogmote.opprettetAv,
                status = newDialogmote.status,
            )
        } catch (e: Exception) {
            when (e) {
                is SQLException, is PSQLException, is TidStedMissingException -> connection.rollback()
                else -> throw e
            }
        }

        return Pair(moteId, UUID.fromString(uuid))
    }
}

const val queryUpdateMotePlanlagtMoteBekreftet =
    """
    UPDATE MOTE
    SET planlagtmote_bekreftet_tidspunkt = ?
    WHERE id = ?
    """

fun DatabaseInterface.updateMotePlanlagtMoteBekreftet(
    moteId: Int,
) {
    val now = Timestamp.from(Instant.now())
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateMotePlanlagtMoteBekreftet).use {
            it.setTimestamp(1, now)
            it.setInt(2, moteId)
            it.execute()
        }
        connection.commit()
    }
}

fun ResultSet.toPDialogmote(): PDialogmote =
    PDialogmote(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        planlagtMoteUuid = UUID.fromString(getString("planlagtmote_uuid")),
        planlagtMoteBekreftetTidspunkt = getTimestamp("planlagtmote_bekreftet_tidspunkt")?.toLocalDateTime(),
        status = getString("status"),
        opprettetAv = getString("opprettet_av"),
        tildeltVeilederIdent = getString("tildelt_veileder_ident"),
        tildeltEnhet = getString("tildelt_enhet")
    )
