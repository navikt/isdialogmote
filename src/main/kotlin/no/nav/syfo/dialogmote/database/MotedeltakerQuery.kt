package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdent
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
    personIdent: PersonIdent,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())
    val motedeltakerUuid = UUID.randomUUID()
    val motedeltakerArbeidstakerIdList = this.prepareStatement(queryCreateMotedeltakerArbeidstaker).use {
        it.setString(1, motedeltakerUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, moteId)
        it.setString(5, personIdent.value)
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
        errorMessageIfEmpty = "No motedeltakerArbeidstaker found for mote with id",
        errorMessageIfMoreThanOne = "More than one motedeltakerArbeidstaker found for mote with id",
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
        errorMessageIfEmpty = "No motedeltakerArbeidstaker found for motedeltakerId with id",
        errorMessageIfMoreThanOne = "More than one motedeltakerArbeidstaker found for motedeltakerId with id",
    )
    return pMotedeltakerArbeidstakerList.first()
}

const val queryGetMotedeltakerArbeidstakerByIdent =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER
        WHERE personident = ?
    """

fun DatabaseInterface.getMotedeltakerArbeidstakerByIdent(personident: PersonIdent): List<PMotedeltakerArbeidstaker> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerByIdent).use {
            it.setString(1, personident.value)
            it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
        }
    }
}

const val queryUpdateMotedeltakerArbeidstakerPersonident =
    """
        UPDATE MOTEDELTAKER_ARBEIDSTAKER
        SET personident = ?
        WHERE personident = ?
    """

fun DatabaseInterface.updateMotedeltakerArbeidstakerPersonident(nyPersonident: PersonIdent, gammelPersonident: PersonIdent): Int {
    var updatedRows: Int
    this.connection.use { connection ->
        updatedRows = connection.prepareStatement(queryUpdateMotedeltakerArbeidstakerPersonident).use {
            it.setString(1, nyPersonident.value)
            it.setString(2, gammelPersonident.value)
            it.executeUpdate()
        }.also {
            connection.commit()
        }
    }
    return updatedRows
}

fun ResultSet.toPMotedeltakerArbeidstaker(): PMotedeltakerArbeidstaker =
    PMotedeltakerArbeidstaker(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteId = getInt("mote_id"),
        personIdent = PersonIdent(getString("personident")),
    )

const val queryCreateMotedeltakerBehandler =
    """
    INSERT INTO MOTEDELTAKER_BEHANDLER (
        id,
        uuid,
        created_at,
        updated_at,
        mote_id,
        behandler_ref,
        behandler_navn,
        behandler_kontor,
        behandler_type,
        personident
        ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createMotedeltakerBehandler(
    commit: Boolean = true,
    moteId: Int,
    newDialogmotedeltakerBehandler: NewDialogmotedeltakerBehandler,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    val motedeltakerUuid = UUID.randomUUID()
    val motedeltakerBehandlerIdList = this.prepareStatement(queryCreateMotedeltakerBehandler).use {
        it.setString(1, motedeltakerUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, moteId)
        it.setString(5, newDialogmotedeltakerBehandler.behandlerRef)
        it.setString(6, newDialogmotedeltakerBehandler.behandlerNavn)
        it.setString(7, newDialogmotedeltakerBehandler.behandlerKontor)
        it.setString(8, BehandlerType.FASTLEGE.name)
        it.setString(9, newDialogmotedeltakerBehandler.personIdent?.value)
        it.executeQuery().toList { getInt("id") }
    }

    motedeltakerBehandlerIdList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "Creating MotedeltakerBehandler failed, no rows affected.",
        errorMessageIfMoreThanOne = "Creating MotedeltakerBehandler failed, more than one row affected.",
    )

    if (commit) {
        this.commit()
    }

    return Pair(motedeltakerBehandlerIdList.first(), motedeltakerUuid)
}

const val queryUpdateMotedeltakerBehandler =
    """
        UPDATE MOTEDELTAKER_BEHANDLER
        SET deltatt = ?, mottar_referat = ?
        WHERE id = ?
    """

fun Connection.updateMotedeltakerBehandler(
    deltakerId: Int,
    deltatt: Boolean,
    mottarReferat: Boolean,
) {
    val rowCount = this.prepareStatement(queryUpdateMotedeltakerBehandler).use {
        it.setBoolean(1, deltatt)
        it.setBoolean(2, mottarReferat)
        it.setInt(3, deltakerId)
        it.executeUpdate()
    }

    if (rowCount != 1) {
        throw SQLException("Update MotedeltakerBehandler failed, no rows affected.")
    }
}

const val queryGetMotedeltakerBehandlerForMote =
    """
        SELECT *
        FROM MOTEDELTAKER_BEHANDLER
        WHERE mote_id = ?
    """

fun DatabaseInterface.getMoteDeltakerBehandler(moteId: Int): PMotedeltakerBehandler? {
    val pMotedeltakerBehandlerList = this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerForMote).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerBehandler() }
        }
    }
    return pMotedeltakerBehandlerList.firstOrNull()
}

const val queryGetMotedeltakerBehandlerById =
    """
        SELECT *
        FROM MOTEDELTAKER_BEHANDLER
        WHERE id = ?
    """

fun DatabaseInterface.getMotedeltakerBehandlerById(motedeltakerId: Int): PMotedeltakerBehandler {
    val pMotedeltakerBehandlerList = this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerById).use {
            it.setInt(1, motedeltakerId)
            it.executeQuery().toList { toPMotedeltakerBehandler() }
        }
    }
    pMotedeltakerBehandlerList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "No motedeltakerBehandler found for motedeltakerId with id",
        errorMessageIfMoreThanOne = "More than one motedeltakerBehandler found for motedeltakerId with id",
    )
    return pMotedeltakerBehandlerList.first()
}

fun ResultSet.toPMotedeltakerBehandler(): PMotedeltakerBehandler =
    PMotedeltakerBehandler(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteId = getInt("mote_id"),
        personIdent = getString("personident")?.let { PersonIdent(it) },
        behandlerRef = getString("behandler_ref"),
        behandlerNavn = getString("behandler_navn"),
        behandlerKontor = getString("behandler_kontor"),
        behandlerType = getString("behandler_type"),
        mottarReferat = getBoolean("mottar_referat"),
        deltatt = getBoolean("deltatt"),
    )

const val queryCreateMotedeltakerArbeidsgiver =
    """
    INSERT INTO MOTEDELTAKER_ARBEIDSGIVER (
        id,
        uuid,
        created_at,
        updated_at,
        mote_id,
        virksomhetsnummer
        ) VALUES (DEFAULT, ?, ?, ?, ?, ?) RETURNING id
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
        errorMessageIfEmpty = "No motedeltakerArbeidsgiver found for moteId with id",
        errorMessageIfMoreThanOne = "More than one motedeltakerArbeidsgiver found for motedeltakerId with id",
    )
    return pMotedeltakerArbeidsgiverList.first()
}

const val queryGetMotedeltakerArbeidsgiverForMoteById =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSGIVER
        WHERE id = ?
    """

fun DatabaseInterface.getMoteDeltakerArbeidsgiverById(moteDeltakerArbeidsgiverId: Int): PMotedeltakerArbeidsgiver {
    val pMotedeltakerArbeidsgiverList = this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidsgiverForMoteById).use {
            it.setInt(1, moteDeltakerArbeidsgiverId)
            it.executeQuery().toList { toPMotedeltakerArbeidsgiver() }
        }
    }
    pMotedeltakerArbeidsgiverList.assertThatExactlyOneElement(
        errorMessageIfEmpty = "No motedeltakerArbeidsgiver found for id",
        errorMessageIfMoreThanOne = "More than one motedeltakerArbeidsgiver found for motedeltakerId",
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
    )

fun List<Any>.assertThatExactlyOneElement(
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
