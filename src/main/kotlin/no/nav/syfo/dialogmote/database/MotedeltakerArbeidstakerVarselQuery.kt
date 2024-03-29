package no.nav.syfo.dialogmote.database

import com.fasterxml.jackson.core.type.TypeReference
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerArbeidstakerVarsel
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.toOffsetDateTimeUTC

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
        pdf_id,
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
    pdfId: Int,
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
        it.setInt(7, pdfId)
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
        ORDER BY created_at ASC
        LIMIT 20
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
              AND brev_bestilt_tidspunkt IS NULL
    """

fun DatabaseInterface.getMotedeltakerArbeidstakerVarselForFysiskBrevUtsending(): List<PMotedeltakerArbeidstakerVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidstakerVarselForFysiskBrevUtsending).use {
            it.executeQuery().toList { toPMotedeltakerArbeidstakerVarsel() }
        }
    }
}

const val queryUpdateMotedeltakerArbeidstakerBrevBestilt =
    """
        UPDATE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        SET brev_bestilt_tidspunkt = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateMotedeltakerArbeidstakerBrevBestilt(
    motedeltakerArbeidstakerVarselId: Int,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateMotedeltakerArbeidstakerBrevBestilt).use {
            it.setTimestamp(1, Timestamp.from(Instant.now()))
            it.setInt(2, motedeltakerArbeidstakerVarselId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryUpdateMotedeltakerArbeidstakerVarselLestDato =
    """
        UPDATE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        SET lest_dato = ?
        WHERE uuid = ? AND lest_dato IS NULL
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

const val queryUpdateMotedeltakerArbeidstakerVarselRespons =
    """
        UPDATE MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        SET svar_type = ?, svar_tekst=?, svar_tidspunkt=?
        WHERE uuid = ? AND svar_type IS NULL
    """

fun Connection.updateMotedeltakerArbeidstakerVarselRespons(
    motedeltakerArbeidstakerVarselUuid: UUID,
    svarType: DialogmoteSvarType,
    svarTekst: String?,
): Int {
    return this.prepareStatement(queryUpdateMotedeltakerArbeidstakerVarselRespons).use {
        it.setString(1, svarType.name)
        it.setString(2, svarTekst)
        it.setTimestamp(3, Timestamp.from(Instant.now()))
        it.setString(4, motedeltakerArbeidstakerVarselUuid.toString())
        it.executeUpdate()
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
        pdfId = getInt("pdf_id"),
        status = getString("status"),
        lestDato = getTimestamp("lest_dato")?.toLocalDateTime(),
        fritekst = getString("fritekst"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
        journalpostId = getString("journalpost_id"),
        brevBestillingsId = getString("brev_bestilling_id"),
        brevBestiltTidspunkt = getTimestamp("brev_bestilt_tidspunkt")?.toLocalDateTime(),
        svarType = getString("svar_type"),
        svarTekst = getString("svar_tekst"),
        svarTidspunkt = getTimestamp("svar_tidspunkt")?.toLocalDateTime(),
        svarPublishedToKafkaAt = getTimestamp("svar_published_to_kafka_at")?.toLocalDateTime()?.toOffsetDateTimeUTC()
    )
