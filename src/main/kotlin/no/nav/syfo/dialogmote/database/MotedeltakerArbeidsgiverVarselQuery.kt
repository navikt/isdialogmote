package no.nav.syfo.dialogmote.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerArbeidsgiverVarsel
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
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
        pdf,
        status, 
        fritekst,
        document) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
    """

private val mapper = configuredJacksonMapper()

fun Connection.createMotedeltakerVarselArbeidsgiver(
    commit: Boolean = true,
    motedeltakerArbeidsgiverId: Int,
    status: String,
    varselType: MotedeltakerVarselType,
    pdf: ByteArray,
    fritekst: String,
    document: List<DocumentComponentDTO>,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    val motedeltakerArbeidsgiverVarselUuid = UUID.randomUUID()
    val motedeltakerArbeidsgiverVarselIdList = this.prepareStatement(queryCreateMotedeltakerVarselArbeidsgiver).use {
        it.setString(1, motedeltakerArbeidsgiverVarselUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, motedeltakerArbeidsgiverId)
        it.setString(5, varselType.name)
        it.setBytes(6, pdf)
        it.setString(7, status)
        it.setString(8, fritekst)
        it.setObject(9, mapper.writeValueAsString(document))
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

const val queryUpdateMotedeltakerArbeidsgiverVarselLestDato =
    """
        UPDATE MOTEDELTAKER_ARBEIDSGIVER_VARSEL
        SET lest_dato = ?
        WHERE uuid = ?
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

fun ResultSet.toPMotedeltakerArbeidsgiverVarsel(): PMotedeltakerArbeidsgiverVarsel =
    PMotedeltakerArbeidsgiverVarsel(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        motedeltakerArbeidsgiverId = getInt("motedeltaker_arbeidsgiver_id"),
        varselType = MotedeltakerVarselType.valueOf(getString("varseltype")),
        pdf = getBytes("pdf"),
        status = getString("status"),
        lestDato = getTimestamp("lest_dato")?.toLocalDateTime(),
        fritekst = getString("fritekst"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
    )
