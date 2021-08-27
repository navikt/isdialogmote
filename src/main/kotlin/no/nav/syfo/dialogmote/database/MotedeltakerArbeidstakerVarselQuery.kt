package no.nav.syfo.dialogmote.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerArbeidstakerVarsel
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import java.sql.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

const val queryCreateMotedeltakerVarselArbeidstaker =
    """
    INSERT INTO MOTEDELTAKER_ARBEIDSTAKER_VARSEL (
        id,
        uuid,
        created_at,
        updated_at,
        motedeltaker_arbeidstaker_id,
        varseltype,
        digitalt,
        pdf,
        status, 
        fritekst,
        document) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
    """

private val mapper = configuredJacksonMapper()

fun Connection.createMotedeltakerVarselArbeidstaker(
    commit: Boolean = true,
    motedeltakerArbeidstakerId: Int,
    status: String,
    varselType: MotedeltakerVarselType,
    digitalt: Boolean,
    pdf: ByteArray,
    fritekst: String,
    document: List<DocumentComponentDTO>,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    val motedeltakerArbeidstakerVarselUuid = UUID.randomUUID()
    val motedeltakerArbeidstakerVarselIdList = this.prepareStatement(queryCreateMotedeltakerVarselArbeidstaker).use {
        it.setString(1, motedeltakerArbeidstakerVarselUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, motedeltakerArbeidstakerId)
        it.setString(5, varselType.name)
        it.setBoolean(6, digitalt)
        it.setBytes(7, pdf)
        it.setString(8, status)
        it.setString(9, fritekst)
        it.setObject(10, mapper.writeValueAsString(document))
        it.executeQuery().toList { getInt("id") }
    }

    if (motedeltakerArbeidstakerVarselIdList.size != 1) {
        throw SQLException("Creating MotedeltakerVarselArbeidstaker failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }

    return Pair(motedeltakerArbeidstakerVarselIdList.first(), motedeltakerArbeidstakerVarselUuid)
}

const val queryGetMotedeltakerArbeidstakerVarselForMotedeltaker =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        WHERE motedeltaker_arbeidstaker_id = ?
        ORDER BY created_at DESC
    """

fun DatabaseInterface.getMotedeltakerArbeidstakerVarsel(
    motedeltakerArbeidstakerId: Int
): List<PMotedeltakerArbeidstakerVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerVarselForMotedeltaker).use {
            it.setInt(1, motedeltakerArbeidstakerId)
            it.executeQuery().toList { toPMotedeltakerArbeidstakerVarsel() }
        }
    }
}

const val queryGetMotedeltakerArbeidstakerVarselForMotedeltakerByUUID =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        WHERE uuid = ?
    """

fun DatabaseInterface.getMotedeltakerArbeidstakerVarsel(
    uuid: UUID
): List<PMotedeltakerArbeidstakerVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerVarselForMotedeltakerByUUID).use {
            it.setString(1, uuid.toString())
            it.executeQuery().toList { toPMotedeltakerArbeidstakerVarsel() }
        }
    }
}

const val queryGetMotedeltakerArbeidstakerVarselWithoutJournalpost =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        WHERE journalpost_id IS NULL
    """

fun DatabaseInterface.getMotedeltakerArbeidstakerVarselWithoutJournalpost(): List<PMotedeltakerArbeidstakerVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerVarselWithoutJournalpost).use {
            it.executeQuery().toList { toPMotedeltakerArbeidstakerVarsel() }
        }
    }
}

const val queryUpdateMotedeltakerArbeidstakerVarselJournalpostId =
    """
        UPDATE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        SET journalpost_id = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateMotedeltakerArbeidstakerVarselJournalpostId(
    motedeltakerArbeidstakerVarselId: Int,
    journalpostId: Int,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateMotedeltakerArbeidstakerVarselJournalpostId).use {
            it.setInt(1, journalpostId)
            it.setInt(2, motedeltakerArbeidstakerVarselId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryGetMotedeltakerArbeidstakerVarselForFysiskBrevUtsending =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        WHERE digitalt IS FALSE 
              AND journalpost_id IS NOT NULL
              AND brev_bestilling_id IS NULL
    """

fun DatabaseInterface.getMotedeltakerArbeidstakerVarselForFysiskBrevUtsending(): List<PMotedeltakerArbeidstakerVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerVarselForFysiskBrevUtsending).use {
            it.executeQuery().toList { toPMotedeltakerArbeidstakerVarsel() }
        }
    }
}

const val queryUpdateMotedeltakerArbeidstakerBrevBestillingsId =
    """
        UPDATE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        SET brev_bestilling_id = ?, brev_bestilt_tidspunkt = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateMotedeltakerArbeidstakerBrevBestillingsId(
    motedeltakerArbeidstakerVarselId: Int,
    brevBestillingsId: String,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateMotedeltakerArbeidstakerBrevBestillingsId).use {
            it.setString(1, brevBestillingsId)
            it.setTimestamp(2, Timestamp.from(Instant.now()))
            it.setInt(3, motedeltakerArbeidstakerVarselId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryUpdateMotedeltakerArbeidstakerVarselLestDato =
    """
        UPDATE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        SET lest_dato = ?
        WHERE uuid = ?
    """

fun Connection.updateMotedeltakerArbeidstakerVarselLestDato(
    motedeltakerArbeidstakerVarselUuid: UUID
) {
    val now = LocalDateTime.now()
    this.prepareStatement(queryUpdateMotedeltakerArbeidstakerVarselLestDato).use {
        it.setTimestamp(1, Timestamp.valueOf(now))
        it.setString(2, motedeltakerArbeidstakerVarselUuid.toString())
        it.execute()
    }
}

fun ResultSet.toPMotedeltakerArbeidstakerVarsel(): PMotedeltakerArbeidstakerVarsel =
    PMotedeltakerArbeidstakerVarsel(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        motedeltakerArbeidstakerId = getInt("motedeltaker_arbeidstaker_id"),
        varselType = MotedeltakerVarselType.valueOf(getString("varseltype")),
        digitalt = getObject("digitalt") as Boolean,
        pdf = getBytes("pdf"),
        status = getString("status"),
        lestDato = getTimestamp("lest_dato")?.toLocalDateTime(),
        fritekst = getString("fritekst"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
        journalpostId = getString("journalpost_id"),
        brevBestillingsId = getString("brev_bestilling_id"),
        brevBestiltTidspunkt = getTimestamp("brev_bestilt_tidspunkt")?.toLocalDateTime(),
    )
