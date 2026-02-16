package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import java.sql.SQLException
import java.sql.Timestamp
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
