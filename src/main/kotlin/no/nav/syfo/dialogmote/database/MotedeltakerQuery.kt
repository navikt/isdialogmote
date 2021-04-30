package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerArbeidsgiver
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerArbeidstaker
import no.nav.syfo.dialogmote.domain.NewDialogmotedeltakerArbeidsgiver
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.sql.*
import java.time.Instant
import java.util.*

const val queryCreateMotedeltakerArbeidstaker =
    """
    INSERT INTO MOTEDELTAKER_ARBEIDSTAKER (
        id,
        uuid,
        created_at,
        updated_at,
        mote_id,
        personident) VALUES (DEFAULT, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createMotedeltakerArbeidstaker(
    commit: Boolean = true,
    moteId: Int,
    personIdentNumber: PersonIdentNumber,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    val motedeltakerUuid = UUID.randomUUID()
    val motedeltakerArbeidstakerIdList = this.prepareStatement(queryCreateMotedeltakerArbeidstaker).use {
        it.setString(1, motedeltakerUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, moteId)
        it.setString(5, personIdentNumber.value)
        it.executeQuery().toList { getInt("id") }
    }

    if (motedeltakerArbeidstakerIdList.size != 1) {
        throw SQLException("Creating MotedeltakerArbeidstaker failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }

    return Pair(motedeltakerArbeidstakerIdList.first(), motedeltakerUuid)
}

const val queryGetMotedeltakerArbeidstakerForMote =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER
        WHERE mote_id = ?
    """

fun DatabaseInterface.getMoteDeltakerArbeidstaker(moteId: Int): List<PMotedeltakerArbeidstaker> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerForMote).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
        }
    }
}

const val queryGetMotedeltakerArbeidstakerById =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER
        WHERE id = ?
    """

fun DatabaseInterface.getMotedeltakerArbeidstakerById(motedeltakerId: Int): List<PMotedeltakerArbeidstaker> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerById).use {
            it.setInt(1, motedeltakerId)
            it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
        }
    }
}

const val queryGetMotedeltakerArbeidstakerForPerson =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER
        WHERE personident = ?
    """

fun DatabaseInterface.getMoteDeltakerArbeidstaker(personIdentNumber: PersonIdentNumber): List<PMotedeltakerArbeidstaker> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerForPerson).use {
            it.setString(1, personIdentNumber.value)
            it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
        }
    }
}

fun ResultSet.toPMotedeltakerArbeidstaker(): PMotedeltakerArbeidstaker =
    PMotedeltakerArbeidstaker(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteId = getInt("mote_id"),
        personIdent = PersonIdentNumber(getString("personident")),
    )

const val queryCreateMotedeltakerArbeidsgiver =
    """
    INSERT INTO MOTEDELTAKER_ARBEIDSGIVER (
        id,
        uuid,
        created_at,
        updated_at,
        mote_id,
        virksomhetsnummer,
        leder_navn,
        leder_epost) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createMotedeltakerArbeidsgiver(
    commit: Boolean = true,
    moteId: Int,
    newDialogmotedeltakerArbeidsgiver: NewDialogmotedeltakerArbeidsgiver
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    val motedeltakerUuid = UUID.randomUUID()
    val motedeltakerArbeidsgiverIdList = this.prepareStatement(queryCreateMotedeltakerArbeidsgiver).use {
        it.setString(1, motedeltakerUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, moteId)
        it.setString(5, newDialogmotedeltakerArbeidsgiver.virksomhetsnummer.value)
        it.setString(6, newDialogmotedeltakerArbeidsgiver.lederNavn)
        it.setString(7, newDialogmotedeltakerArbeidsgiver.lederEpost)
        it.executeQuery().toList { getInt("id") }
    }

    if (motedeltakerArbeidsgiverIdList.size != 1) {
        throw SQLException("Creating MotedeltakerArbeidsgiver failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }

    return Pair(motedeltakerArbeidsgiverIdList.first(), motedeltakerUuid)
}

const val queryGetMotedeltakerArbeidsgiverForMote =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSGIVER
        WHERE mote_id = ?
    """

fun DatabaseInterface.getMoteDeltakerArbeidsgiver(moteId: Int): List<PMotedeltakerArbeidsgiver> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidsgiverForMote).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerArbeidsgiver() }
        }
    }
}

fun ResultSet.toPMotedeltakerArbeidsgiver(): PMotedeltakerArbeidsgiver =
    PMotedeltakerArbeidsgiver(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteId = getInt("mote_id"),
        virksomhetsnummer = Virksomhetsnummer(getString("virksomhetsnummer")),
        lederNavn = getString("leder_navn"),
        lederEpost = getString("leder_epost"),
    )
