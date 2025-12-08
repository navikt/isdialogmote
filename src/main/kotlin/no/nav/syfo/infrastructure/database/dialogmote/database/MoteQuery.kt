package no.nav.syfo.infrastructure.database.dialogmote.database

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.NewDialogmote
import no.nav.syfo.domain.dialogmote.TidStedDTO
import no.nav.syfo.infrastructure.cronjob.statusendring.toInstantOslo
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.PDialogmote
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.PMotedeltakerBehandlerVarsel
import no.nav.syfo.infrastructure.database.dialogmote.database.repository.toPDialogmote
import no.nav.syfo.infrastructure.database.toList
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

const val queryGetDialogmoteForId =
    """
        SELECT *
        FROM MOTE
        WHERE id = ?
    """

fun DatabaseInterface.getDialogmote(id: Int): List<PDialogmote> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteForId).use {
            it.setInt(1, id)
            it.executeQuery().toList { toPDialogmote() }
        }
    }
}

const val queryGetDialogmoteForUUID =
    """
        SELECT *
        FROM MOTE
        WHERE uuid = ?
    """

fun DatabaseInterface.getDialogmote(moteUUID: UUID): List<PDialogmote> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteForUUID).use {
            it.setString(1, moteUUID.toString())
            it.executeQuery().toList { toPDialogmote() }
        }
    }
}

const val queryGetDialogmoteListForPersonIdent =
    """
        SELECT *
        FROM MOTE
        INNER JOIN MOTEDELTAKER_ARBEIDSTAKER on MOTEDELTAKER_ARBEIDSTAKER.mote_id = MOTE.id
        WHERE personident = ?
        ORDER BY MOTE.created_at DESC
    """

fun DatabaseInterface.getDialogmoteList(personIdent: PersonIdent): List<PDialogmote> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteListForPersonIdent).use {
            it.setString(1, personIdent.value)
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
        status,
        opprettet_av,
        tildelt_veileder_ident,
        tildelt_enhet) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

data class CreatedDialogmoteIdentifiers(
    val dialogmoteIdPair: Pair<Int, UUID>,
    val motedeltakerArbeidstakerIdPair: Pair<Int, UUID>,
    val motedeltakerArbeidsgiverIdPair: Pair<Int, UUID>,
    val motedeltakerBehandlerIdPair: Pair<Int, UUID>?,
)

fun Connection.createNewDialogmoteWithReferences(
    newDialogmote: NewDialogmote,
    commit: Boolean = true,
): CreatedDialogmoteIdentifiers {
    val moteIdList = this.createDialogmote(
        newDialogmote = newDialogmote,
        commit = false,
    )

    val moteId = moteIdList.first

    this.createTidSted(
        commit = false,
        moteId = moteId,
        newDialogmoteTidSted = newDialogmote.tidSted
    )
    val motedeltakerArbeidstakerIdPair = this.createMotedeltakerArbeidstaker(
        commit = false,
        moteId = moteId,
        personIdent = newDialogmote.arbeidstaker.personIdent,
    )
    val motedeltakerArbeidsgiverIdPair = this.createMotedeltakerArbeidsgiver(
        commit = false,
        moteId = moteId,
        newDialogmotedeltakerArbeidsgiver = newDialogmote.arbeidsgiver,
    )
    val motedeltakerBehandlerIdPair = newDialogmote.behandler?.let {
        this.createMotedeltakerBehandler(
            commit = false,
            moteId = moteId,
            newDialogmotedeltakerBehandler = it,
        )
    }

    if (commit) {
        this.commit()
    }

    return CreatedDialogmoteIdentifiers(
        dialogmoteIdPair = moteIdList,
        motedeltakerArbeidstakerIdPair = motedeltakerArbeidstakerIdPair,
        motedeltakerArbeidsgiverIdPair = motedeltakerArbeidsgiverIdPair,
        motedeltakerBehandlerIdPair = motedeltakerBehandlerIdPair,
    )
}

fun Connection.createDialogmote(
    newDialogmote: NewDialogmote,
    commit: Boolean = true,
): Pair<Int, UUID> {
    val moteUuid = UUID.randomUUID()
    val now = Timestamp.from(Instant.now())

    val moteIdList = this.prepareStatement(queryCreateDialogmote).use {
        it.setString(1, moteUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setString(4, newDialogmote.status.name)
        it.setString(5, newDialogmote.opprettetAv)
        it.setString(6, newDialogmote.tildeltVeilederIdent)
        it.setString(7, newDialogmote.tildeltEnhet)
        it.executeQuery().toList { getInt("id") }
    }
    if (moteIdList.size != 1) {
        throw SQLException("Creating Dialogmote failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }

    return Pair(moteIdList.first(), moteUuid)
}

const val queryUpdateMoteTildeltVeileder =
    """
    UPDATE MOTE
    SET tildelt_veileder_ident = ?, updated_at = ?
    WHERE id = ?
    """

fun Connection.updateMoteTildeltVeileder(
    commit: Boolean = true,
    moteId: Int,
    veilederId: String,
) {
    val now = Timestamp.from(Instant.now())
    this.prepareStatement(queryUpdateMoteTildeltVeileder).use {
        it.setString(1, veilederId)
        it.setTimestamp(2, now)
        it.setInt(3, moteId)
        it.execute()
    }
    if (commit) {
        this.commit()
    }
}

const val queryUpdateMoteStatus =
    """
    UPDATE MOTE
    SET status = ?, updated_at = ?
    WHERE id = ?
    """

fun Connection.updateMoteStatus(
    commit: Boolean = true,
    moteId: Int,
    moteStatus: Dialogmote.Status,
) {
    val now = Timestamp.from(Instant.now())
    this.prepareStatement(queryUpdateMoteStatus).use {
        it.setString(1, moteStatus.name)
        it.setTimestamp(2, now)
        it.setInt(3, moteId)
        it.execute()
    }
    if (commit) {
        this.commit()
    }
}

const val queryFindOutdatedMoter =
    """
        SELECT m.* from MOTE m 
            INNER JOIN MOTE_STATUS_ENDRET s1 ON (m.id = s1.mote_id) 
            INNER JOIN TID_STED t1 ON (m.id = t1.mote_id)
        WHERE s1.created_at = (SELECT max(s2.created_at) FROM MOTE_STATUS_ENDRET s2 WHERE s2.mote_id = m.id)
            AND t1.created_at = (SELECT max(t2.created_at) FROM TID_STED t2 WHERE t2.mote_id = m.id)
            AND s1.status in ('INNKALT','NYTT_TID_STED')
            AND t1.tid < ?
        LIMIT 100
    """

fun DatabaseInterface.findOutdatedMoter(
    cutoff: LocalDateTime,
) = this.connection.use { connection ->
    connection.prepareStatement(queryFindOutdatedMoter).use {
        it.setTimestamp(1, Timestamp.from(cutoff.toInstantOslo()))
        it.executeQuery().toList { toPDialogmote() }
    }
}

const val queryGetMoteByBehandlerVarselUuid =
    """
        SELECT m.*
        FROM mote m
            INNER JOIN motedeltaker_behandler mb on m.id = mb.mote_id
            INNER JOIN motedeltaker_behandler_varsel mbv on mb.id = mbv.motedeltaker_behandler_id
        WHERE mbv.uuid = ?;
    """

fun DatabaseInterface.getMote(
    behandlerVarsel: PMotedeltakerBehandlerVarsel,
) = this.connection.use { connection ->
    connection.prepareStatement(queryGetMoteByBehandlerVarselUuid).use {
        it.setString(1, behandlerVarsel.uuid.toString())
        it.executeQuery().toList { toPDialogmote() }.firstOrNull()
    }
}

fun Connection.updateMoteTidSted(
    commit: Boolean = true,
    moteId: Int,
    newDialogmoteTidSted: TidStedDTO,
) {
    this.createTidSted(
        commit = false,
        moteId = moteId,
        newDialogmoteTidSted = newDialogmoteTidSted,
    )
    if (commit) {
        this.commit()
    }
}
