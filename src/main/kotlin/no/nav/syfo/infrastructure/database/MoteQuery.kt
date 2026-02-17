package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.NewDialogmote
import no.nav.syfo.domain.dialogmote.TidStedDTO
import no.nav.syfo.infrastructure.cronjob.statusendring.toInstantOslo
import no.nav.syfo.infrastructure.database.model.PDialogmote
import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandlerVarsel
import no.nav.syfo.infrastructure.database.repository.toPDialogmote
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

fun UnitOfWork.createNewDialogmoteWithReferences(
    newDialogmote: NewDialogmote,
): CreatedDialogmoteIdentifiers {
    val moteIdList = createDialogmote(
        newDialogmote = newDialogmote,
    )

    val moteId = moteIdList.first

    createTidSted(
        moteId = moteId,
        newDialogmoteTidSted = newDialogmote.tidSted
    )
    val motedeltakerArbeidstakerIdPair = createMotedeltakerArbeidstaker(
        moteId = moteId,
        personIdent = newDialogmote.arbeidstaker.personIdent,
    )
    val motedeltakerArbeidsgiverIdPair = createMotedeltakerArbeidsgiver(
        moteId = moteId,
        newDialogmotedeltakerArbeidsgiver = newDialogmote.arbeidsgiver,
    )
    val motedeltakerBehandlerIdPair = newDialogmote.behandler?.let {
        createMotedeltakerBehandler(
            moteId = moteId,
            newDialogmotedeltakerBehandler = it,
        )
    }

    return CreatedDialogmoteIdentifiers(
        dialogmoteIdPair = moteIdList,
        motedeltakerArbeidstakerIdPair = motedeltakerArbeidstakerIdPair,
        motedeltakerArbeidsgiverIdPair = motedeltakerArbeidsgiverIdPair,
        motedeltakerBehandlerIdPair = motedeltakerBehandlerIdPair,
    )
}

fun UnitOfWork.createDialogmote(
    newDialogmote: NewDialogmote,
): Pair<Int, UUID> {
    val moteUuid = UUID.randomUUID()
    val now = Timestamp.from(Instant.now())

    val moteIdList = connection.prepareStatement(queryCreateDialogmote).use {
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

    return Pair(moteIdList.first(), moteUuid)
}

const val queryUpdateMoteTildeltVeileder =
    """
    UPDATE MOTE
    SET tildelt_veileder_ident = ?, updated_at = ?
    WHERE id = ?
    """

fun UnitOfWork.updateMoteTildeltVeileder(
    moteId: Int,
    veilederId: String,
) {
    val now = Timestamp.from(Instant.now())
    connection.prepareStatement(queryUpdateMoteTildeltVeileder).use {
        it.setString(1, veilederId)
        it.setTimestamp(2, now)
        it.setInt(3, moteId)
        it.execute()
    }
}

const val queryUpdateMoteStatus =
    """
    UPDATE MOTE
    SET status = ?, updated_at = ?
    WHERE id = ?
    """

fun UnitOfWork.updateMoteStatus(
    moteId: Int,
    moteStatus: Dialogmote.Status,
) {
    val now = Timestamp.from(Instant.now())
    connection.prepareStatement(queryUpdateMoteStatus).use {
        it.setString(1, moteStatus.name)
        it.setTimestamp(2, now)
        it.setInt(3, moteId)
        it.execute()
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

fun UnitOfWork.updateMoteTidSted(
    moteId: Int,
    newDialogmoteTidSted: TidStedDTO,
) {
    createTidSted(
        moteId = moteId,
        newDialogmoteTidSted = newDialogmoteTidSted,
    )
}
