package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerArbeidsgiver
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerArbeidstaker
import no.nav.syfo.dialogmote.domain.NewDialogmotedeltakerArbeidsgiver
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.lang.RuntimeException
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

    motedeltakerArbeidstakerIdList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "Creating MotedeltakerArbeidstaker failed, no rows affected.",
        errorMessageIfMoreThanOne = "Creating MotedeltakerArbeidstaker failed, more than one row affected.",
    )

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

fun DatabaseInterface.getMoteDeltakerArbeidstaker(moteId: Int): PMotedeltakerArbeidstaker {
    val pMotedeltakerArbeidstakerList = this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerForMote).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
        }
    }
    pMotedeltakerArbeidstakerList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "No motedeltakerArbeidstaker found for mote with id $moteId",
        errorMessageIfMoreThanOne = "More than one motedeltakerArbeidstaker found for mote with id $moteId",
    )
    return pMotedeltakerArbeidstakerList.first()
}

const val queryGetMotedeltakerArbeidstakerById =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER
        WHERE id = ?
    """

fun DatabaseInterface.getMotedeltakerArbeidstakerById(motedeltakerId: Int): PMotedeltakerArbeidstaker {
    val pMotedeltakerArbeidstakerList = this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerById).use {
            it.setInt(1, motedeltakerId)
            it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
        }
    }
    pMotedeltakerArbeidstakerList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "No motedeltakerArbeidstaker found for motedeltakerId with id $motedeltakerId",
        errorMessageIfMoreThanOne = "More than one motedeltakerArbeidstaker found for motedeltakerId with id $motedeltakerId",
    )
    return pMotedeltakerArbeidstakerList.first()
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

    motedeltakerArbeidsgiverIdList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "Creating MotedeltakerArbeidsgiver failed, no rows affected.",
        errorMessageIfMoreThanOne = "Creating MotedeltakerArbeidsgiver failed, more than one row affected.",
    )

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

fun DatabaseInterface.getMoteDeltakerArbeidsgiver(moteId: Int): PMotedeltakerArbeidsgiver {
    val pMotedeltakerArbeidsgiverList = this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidsgiverForMote).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerArbeidsgiver() }
        }
    }
    pMotedeltakerArbeidsgiverList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "No motedeltakerArbeidsgiver found for moteId with id $moteId",
        errorMessageIfMoreThanOne = "More than one motedeltakerArbeidsgiver found for motedeltakerId with id $moteId",
    )
    return pMotedeltakerArbeidsgiverList.first()
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

private fun List<Any>.assertThatExactlyOneElement(
    errorMessageIfEmpty: String,
    errorMessageIfMoreThanOne: String,
) {
    if (isEmpty()) {
        throw RuntimeException(errorMessageIfEmpty)
    }
    if (this.size > 1) {
        throw RuntimeException(errorMessageIfMoreThanOne)
    }
}
