package no.nav.syfo.dialogmote.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerArbeidsgiverVarsel
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.util.*
import java.sql.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

const val queryCreateMotedeltakerVarselArbeidsgiver =
    """
    INSERT INTO MOTEDELTAKER_ARBEIDSGIVER_VARSEL (
        id,
        uuid,
        created_at,
        updated_at,
        motedeltaker_arbeidsgiver_id,
        varseltype,
        pdf_id,
        status, 
        fritekst,
        altinn_sent_at,
        document) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
    """

private val mapper = configuredJacksonMapper()

// TODO: Fjern sendAltinn boolean (altinn_sent_at skal alltid settes til now)
fun Connection.createMotedeltakerVarselArbeidsgiver(
    commit: Boolean = true,
    motedeltakerArbeidsgiverId: Int,
    status: String,
    varselType: MotedeltakerVarselType,
    pdfId: Int,
    fritekst: String,
    sendAltinn: Boolean,
    document: List<DocumentComponentDTO>,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())
    val nowUTC = nowUTC()

    val motedeltakerArbeidsgiverVarselUuid = UUID.randomUUID()
    val motedeltakerArbeidsgiverVarselIdList = this.prepareStatement(queryCreateMotedeltakerVarselArbeidsgiver).use {
        it.setString(1, motedeltakerArbeidsgiverVarselUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, motedeltakerArbeidsgiverId)
        it.setString(5, varselType.name)
        it.setInt(6, pdfId)
        it.setString(7, status)
        it.setString(8, fritekst)
        if (sendAltinn) {
            it.setObject(9, nowUTC)
        } else {
            it.setNull(9, Types.TIMESTAMP)
        }
        it.setObject(10, mapper.writeValueAsString(document))
        it.executeQuery().toList { getInt("id") }
    }

    if (motedeltakerArbeidsgiverVarselIdList.size != 1) {
        throw SQLException("Creating MotedeltakerVarselArbeidsgiver failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }

    return Pair(motedeltakerArbeidsgiverVarselIdList.first(), motedeltakerArbeidsgiverVarselUuid)
}

const val queryGetMotedeltakerArbeidsgiverVarselForMotedeltaker =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSGIVER_VARSEL
        WHERE motedeltaker_arbeidsgiver_id = ?
        ORDER BY created_at DESC
    """

fun DatabaseInterface.getMotedeltakerArbeidsgiverVarsel(
    motedeltakerArbeidsgiverId: Int
): List<PMotedeltakerArbeidsgiverVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidsgiverVarselForMotedeltaker).use {
            it.setInt(1, motedeltakerArbeidsgiverId)
            it.executeQuery().toList { toPMotedeltakerArbeidsgiverVarsel() }
        }
    }
}

const val queryGetMotedeltakerArbeidsgiverVarselForMotedeltakerFromUuid =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSGIVER_VARSEL
        WHERE uuid = ?
        ORDER BY created_at DESC
    """

fun DatabaseInterface.getMotedeltakerArbeidsgiverVarsel(
    uuid: UUID
): List<PMotedeltakerArbeidsgiverVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidsgiverVarselForMotedeltakerFromUuid).use {
            it.setString(1, uuid.toString())
            it.executeQuery().toList { toPMotedeltakerArbeidsgiverVarsel() }
        }
    }
}

const val queryUpdateMotedeltakerArbeidsgiverVarselLestDato =
    """
        UPDATE MOTEDELTAKER_ARBEIDSGIVER_VARSEL
        SET lest_dato = ?
        WHERE uuid = ? AND lest_dato IS NULL
    """

fun Connection.updateMotedeltakerArbeidsgiverVarselLestDato(
    motedeltakerArbeidsgiverVarselUuid: UUID
) {
    val now = LocalDateTime.now()
    this.prepareStatement(queryUpdateMotedeltakerArbeidsgiverVarselLestDato).use {
        it.setTimestamp(1, Timestamp.valueOf(now))
        it.setString(2, motedeltakerArbeidsgiverVarselUuid.toString())
        it.execute()
    }
}

const val queryGetMotedeltakerArbeidsgiverVarselWithoutJournalpost =
    """
        SELECT *
        FROM MOTEDELTAKER_ARBEIDSGIVER_VARSEL
        WHERE journalpost_id IS NULL
        ORDER BY created_at ASC
        LIMIT 20
    """

fun DatabaseInterface.getMotedeltakerArbeidsgiverVarselWithoutJournalpost(): List<PMotedeltakerArbeidsgiverVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidsgiverVarselWithoutJournalpost).use {
            it.executeQuery().toList { toPMotedeltakerArbeidsgiverVarsel() }
        }
    }
}

const val queryUpdateMotedeltakerArbeidsgiverVarselJournalpostId =
    """
        UPDATE MOTEDELTAKER_ARBEIDSGIVER_VARSEL
        SET journalpost_id = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateMotedeltakerArbeidsgiverVarselJournalpostId(
    motedeltakerArbeidsgiverVarselId: Int,
    journalpostId: Int,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateMotedeltakerArbeidsgiverVarselJournalpostId).use {
            it.setInt(1, journalpostId)
            it.setInt(2, motedeltakerArbeidsgiverVarselId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryUpdateMotedeltakerArbeidsgiverVarselRespons =
    """
        UPDATE MOTEDELTAKER_ARBEIDSGIVER_VARSEL
        SET svar_type = ?, svar_tekst=?, svar_tidspunkt=?
        WHERE uuid = ? AND svar_type IS NULL
    """

fun Connection.updateMotedeltakerArbeidsgiverVarselRespons(
    motedeltakerArbeidsgiverVarselUuid: UUID,
    svarType: DialogmoteSvarType,
    svarTekst: String?,
): Int {
    return this.prepareStatement(queryUpdateMotedeltakerArbeidsgiverVarselRespons).use {
        it.setString(1, svarType.name)
        it.setString(2, svarTekst)
        it.setTimestamp(3, Timestamp.from(Instant.now()))
        it.setString(4, motedeltakerArbeidsgiverVarselUuid.toString())
        it.executeUpdate()
    }
}

fun ResultSet.toPMotedeltakerArbeidsgiverVarsel(): PMotedeltakerArbeidsgiverVarsel =
    PMotedeltakerArbeidsgiverVarsel(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        motedeltakerArbeidsgiverId = getInt("motedeltaker_arbeidsgiver_id"),
        varselType = MotedeltakerVarselType.valueOf(getString("varseltype")),
        pdfId = getInt("pdf_id"),
        status = getString("status"),
        lestDato = getTimestamp("lest_dato")?.toLocalDateTime(),
        fritekst = getString("fritekst"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
        svarType = getString("svar_type"),
        svarTekst = getString("svar_tekst"),
        svarTidspunkt = getTimestamp("svar_tidspunkt")?.toLocalDateTime(),
        svarPublishedToKafkaAt = getTimestamp("svar_published_to_kafka_at")?.toLocalDateTime()?.toOffsetDateTimeUTC(),
    )
