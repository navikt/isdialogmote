package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.domain.TidStedDTO
import no.nav.syfo.dialogmote.database.domain.PDialogmote
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.*
import java.time.Instant
import java.util.UUID

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

fun DatabaseInterface.getDialogmoteList(personIdentNumber: PersonIdentNumber): List<PDialogmote> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteListForPersonIdent).use {
            it.setString(1, personIdentNumber.value)
            it.executeQuery().toList { toPDialogmote() }
        }
    }
}

const val queryGetDialogmoteListForEnhetNr =
    """
        SELECT *
        FROM MOTE
        INNER JOIN MOTEDELTAKER_ARBEIDSTAKER on MOTEDELTAKER_ARBEIDSTAKER.mote_id = MOTE.id
        WHERE tildelt_enhet = ?
        ORDER BY MOTE.created_at DESC
    """

fun DatabaseInterface.getDialogmoteList(enhetNr: EnhetNr): List<PDialogmote> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteListForEnhetNr).use {
            it.setString(1, enhetNr.value)
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
    val motedeltakerArbeidstakerIdList: Pair<Int, UUID>,
    val motedeltakerArbeidsgiverIdList: Pair<Int, UUID>,
)

fun Connection.createNewDialogmoteWithReferences(
    commit: Boolean = true,
    newDialogmote: NewDialogmote,
): CreatedDialogmoteIdentifiers {
    val moteIdList = this.createDialogmote(
        commit = false,
        newDialogmote = newDialogmote
    )

    val moteId = moteIdList.first

    this.createTidSted(
        commit = false,
        moteId = moteId,
        newDialogmoteTidSted = newDialogmote.tidSted
    )
    val motedeltakerArbeidstakerIdList = this.createMotedeltakerArbeidstaker(
        commit = false,
        moteId = moteId,
        personIdentNumber = newDialogmote.arbeidstaker.personIdent,
    )
    val motedeltakerArbeidsgiverIdList = this.createMotedeltakerArbeidsgiver(
        commit = false,
        moteId = moteId,
        newDialogmotedeltakerArbeidsgiver = newDialogmote.arbeidsgiver,
    )

    if (commit) {
        this.commit()
    }

    return CreatedDialogmoteIdentifiers(
        dialogmoteIdPair = moteIdList,
        motedeltakerArbeidstakerIdList = motedeltakerArbeidstakerIdList,
        motedeltakerArbeidsgiverIdList = motedeltakerArbeidsgiverIdList,
    )
}

fun Connection.createDialogmote(
    commit: Boolean = true,
    newDialogmote: NewDialogmote
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
    moteStatus: DialogmoteStatus,
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

fun ResultSet.toPDialogmote(): PDialogmote =
    PDialogmote(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        status = getString("status"),
        opprettetAv = getString("opprettet_av"),
        tildeltVeilederIdent = getString("tildelt_veileder_ident"),
        tildeltEnhet = getString("tildelt_enhet")
    )
