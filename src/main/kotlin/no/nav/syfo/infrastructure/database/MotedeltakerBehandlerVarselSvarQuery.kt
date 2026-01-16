package no.nav.syfo.infrastructure.database

import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandlerVarselSvar
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.*
import java.time.Instant
import java.util.*

const val queryCreateMotedeltakerBehandlerVarselSvar =
    """
        INSERT INTO MOTEDELTAKER_BEHANDLER_VARSEL_SVAR (
            id,                              
            uuid,                            
            created_at,                      
            motedeltaker_behandler_varsel_id,
            svar_type,                       
            svar_tekst,
            msg_id
        ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun DatabaseInterface.createMotedeltakerBehandlerVarselSvar(
    motedeltakerBehandlerVarselId: Int,
    type: DialogmoteSvarType,
    tekst: String?,
    msgId: String,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())
    val svarUUID = UUID.randomUUID()

    this.connection.use { connection ->
        val svarIdList = connection.prepareStatement(queryCreateMotedeltakerBehandlerVarselSvar).use {
            it.setString(1, svarUUID.toString())
            it.setTimestamp(2, now)
            it.setInt(3, motedeltakerBehandlerVarselId)
            it.setString(4, type.name)
            it.setString(5, tekst.orEmpty())
            it.setString(6, msgId)
            it.executeQuery().toList { getInt("id") }
        }

        if (svarIdList.size != 1) {
            throw SQLException("Creating MotedeltakerBehandlerVarselSvar failed, no rows affected.")
        }

        connection.commit()

        return Pair(svarIdList.first(), svarUUID)
    }
}

const val queryGetMotedeltakerBehandlerVarselSvarForVarsel =
    """
        SELECT *
        FROM MOTEDELTAKER_BEHANDLER_VARSEL_SVAR
        WHERE motedeltaker_behandler_varsel_id = ?
        ORDER BY created_at DESC
    """

fun DatabaseInterface.getMotedeltakerBehandlerVarselSvar(
    motedeltakerBehandlerVarselId: Int,
): List<PMotedeltakerBehandlerVarselSvar> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerVarselSvarForVarsel).use {
            it.setInt(1, motedeltakerBehandlerVarselId)
            it.executeQuery().toList { toPMotedeltakerBehandlerVarselSvar() }
        }
    }
}

fun ResultSet.toPMotedeltakerBehandlerVarselSvar(): PMotedeltakerBehandlerVarselSvar =
    PMotedeltakerBehandlerVarselSvar(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        motedeltakerBehandlerVarselId = getInt("motedeltaker_behandler_varsel_id"),
        svarType = getString("svar_type"),
        svarTekst = getString("svar_tekst"),
        msgId = getString("msg_id"),
        svarPublishedToKafkaAt = getTimestamp("svar_published_to_kafka_at")?.toLocalDateTime()?.toOffsetDateTimeUTC(),
    )
