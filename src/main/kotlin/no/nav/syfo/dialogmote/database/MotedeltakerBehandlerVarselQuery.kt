package no.nav.syfo.dialogmote.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerBehandlerVarsel
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

const val queryCreateMotedeltakerVarselBehandler =
    """
    INSERT INTO MOTEDELTAKER_BEHANDLER_VARSEL (
        id,
        uuid,
        created_at,
        updated_at,
        motedeltaker_behandler_id,
        varseltype,
        pdf,
        status, 
        document) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
    """

private val mapper = configuredJacksonMapper()

fun Connection.createMotedeltakerVarselBehandler(
    commit: Boolean = true,
    motedeltakerBehandlerId: Int,
    status: String,
    varselType: MotedeltakerVarselType,
    pdf: ByteArray,
    document: List<DocumentComponentDTO>,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())

    val motedeltakerBehandlerVarselUuid = UUID.randomUUID()
    val motedeltakerBehandlerVarselIdList = this.prepareStatement(queryCreateMotedeltakerVarselBehandler).use {
        it.setString(1, motedeltakerBehandlerVarselUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, motedeltakerBehandlerId)
        it.setString(5, varselType.name)
        it.setBytes(6, pdf)
        it.setString(7, status)
        it.setObject(8, mapper.writeValueAsString(document))
        it.executeQuery().toList { getInt("id") }
    }

    if (motedeltakerBehandlerVarselIdList.size != 1) {
        throw SQLException("Creating MotedeltakerVarselBehandler failed, no rows affected.")
    }

    if (commit) {
        this.commit()
    }

    return Pair(motedeltakerBehandlerVarselIdList.first(), motedeltakerBehandlerVarselUuid)
}

const val queryGetMotedeltakerBehandlerVarselForMotedeltaker =
    """
        SELECT *
        FROM MOTEDELTAKER_BEHANDLER_VARSEL
        WHERE motedeltaker_behandler_id = ?
        ORDER BY created_at DESC
    """

fun DatabaseInterface.getMotedeltakerBehandlerVarsel(
    motedeltakerBehandlerId: Int
): List<PMotedeltakerBehandlerVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerVarselForMotedeltaker).use {
            it.setInt(1, motedeltakerBehandlerId)
            it.executeQuery().toList { toPMotedeltakerBehandlerVarsel() }
        }
    }
}

fun ResultSet.toPMotedeltakerBehandlerVarsel(): PMotedeltakerBehandlerVarsel =
    PMotedeltakerBehandlerVarsel(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        motedeltakerBehandlerId = getInt("motedeltaker_behandler_id"),
        varselType = MotedeltakerVarselType.valueOf(getString("varseltype")),
        pdf = getBytes("pdf"),
        status = getString("status"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
    )