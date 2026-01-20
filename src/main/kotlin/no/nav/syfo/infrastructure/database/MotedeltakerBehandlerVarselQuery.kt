package no.nav.syfo.infrastructure.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandlerVarsel
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.api.authentication.configuredJacksonMapper
import java.sql.*
import java.time.Instant
import java.util.*

const val queryCreateMotedeltakerVarselBehandler =
    """
    INSERT INTO MOTEDELTAKER_BEHANDLER_VARSEL (
        id,
        uuid,
        created_at,
        updated_at,
        motedeltaker_behandler_id,
        varseltype,
        pdf_id,
        status, 
        fritekst,
        document) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
    """

private val mapper = configuredJacksonMapper()

fun Connection.createMotedeltakerVarselBehandler(
    commit: Boolean = true,
    motedeltakerBehandlerId: Int,
    status: String,
    varselType: MotedeltakerVarselType,
    pdfId: Int,
    fritekst: String,
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
        it.setInt(6, pdfId)
        it.setString(7, status)
        it.setString(8, fritekst)
        it.setObject(9, mapper.writeValueAsString(document))
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

fun DatabaseInterface.getMotedeltakerBehandlerVarselForMotedeltaker(
    motedeltakerBehandlerId: Int,
): List<PMotedeltakerBehandlerVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerVarselForMotedeltaker).use {
            it.setInt(1, motedeltakerBehandlerId)
            it.executeQuery().toList { toPMotedeltakerBehandlerVarsel() }
        }
    }
}

const val queryGetMotedeltakerBehandlerVarselWithoutJournalpost =
    """
        SELECT *
        FROM MOTEDELTAKER_BEHANDLER_VARSEL
        WHERE journalpost_id IS NULL
        ORDER BY created_at ASC
        LIMIT 20
    """

fun DatabaseInterface.getMotedeltakerBehandlerVarselWithoutJournalpost(): List<PMotedeltakerBehandlerVarsel> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerVarselWithoutJournalpost).use {
            it.executeQuery().toList { toPMotedeltakerBehandlerVarsel() }
        }
    }
}

const val queryUpdateMotedeltakerBehandlerVarselJournalpostId =
    """
        UPDATE MOTEDELTAKER_BEHANDLER_VARSEL
        SET journalpost_id = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateMotedeltakerBehandlerVarselJournalpostId(
    motedeltakerBehandlerVarselId: Int,
    journalpostId: Int,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateMotedeltakerBehandlerVarselJournalpostId).use {
            it.setInt(1, journalpostId)
            it.setInt(2, motedeltakerBehandlerVarselId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryGetMotedeltakerBehandlerVarselOfTypeByArbeidstakerAndUuid =
    """
        SELECT MOTEDELTAKER_BEHANDLER.mote_id, MOTEDELTAKER_BEHANDLER_VARSEL.*
        FROM MOTEDELTAKER_BEHANDLER_VARSEL 
        INNER JOIN MOTEDELTAKER_BEHANDLER ON (MOTEDELTAKER_BEHANDLER.id = MOTEDELTAKER_BEHANDLER_VARSEL.motedeltaker_behandler_id)
        INNER JOIN MOTE ON (MOTE.id = MOTEDELTAKER_BEHANDLER.mote_id)
        INNER JOIN MOTEDELTAKER_ARBEIDSTAKER ON (MOTE.id = MOTEDELTAKER_ARBEIDSTAKER.mote_id)
        WHERE MOTEDELTAKER_BEHANDLER_VARSEL.uuid = ?
        AND MOTEDELTAKER_ARBEIDSTAKER.personident = ?
        AND MOTEDELTAKER_BEHANDLER_VARSEL.varseltype = ?
    """

fun DatabaseInterface.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
    varselType: MotedeltakerVarselType,
    arbeidstakerPersonIdent: PersonIdent,
    uuid: String,
): Pair<Int, PMotedeltakerBehandlerVarsel>? {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerVarselOfTypeByArbeidstakerAndUuid).use {
            it.setString(1, uuid)
            it.setString(2, arbeidstakerPersonIdent.value)
            it.setString(3, varselType.name)
            it.executeQuery().toList { Pair(getInt(1), toPMotedeltakerBehandlerVarsel()) }
        }
    }.firstOrNull()
}

const val queryGetMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndBehandler =
    """
        SELECT MOTEDELTAKER_BEHANDLER_VARSEL.*
        FROM MOTEDELTAKER_BEHANDLER_VARSEL INNER JOIN MOTEDELTAKER_BEHANDLER ON (MOTEDELTAKER_BEHANDLER.id = MOTEDELTAKER_BEHANDLER_VARSEL.motedeltaker_behandler_id)
                                           INNER JOIN MOTE ON (MOTE.id = MOTEDELTAKER_BEHANDLER.mote_id AND MOTE.status IN ('INNKALT', 'NYTT_TID_STED'))        
                                           INNER JOIN MOTEDELTAKER_ARBEIDSTAKER ON (MOTE.id = MOTEDELTAKER_ARBEIDSTAKER.mote_id)
        WHERE MOTEDELTAKER_BEHANDLER_VARSEL.varseltype = ?
        AND MOTEDELTAKER_ARBEIDSTAKER.personident = ?
        AND MOTEDELTAKER_BEHANDLER.personident = ?
        ORDER BY MOTEDELTAKER_BEHANDLER_VARSEL.created_at DESC
    """

fun DatabaseInterface.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndBehandler(
    varselType: MotedeltakerVarselType,
    arbeidstakerPersonIdent: PersonIdent,
    behandlerPersonIdent: PersonIdent,
): PMotedeltakerBehandlerVarsel? {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndBehandler).use {
            it.setString(1, varselType.name)
            it.setString(2, arbeidstakerPersonIdent.value)
            it.setString(3, behandlerPersonIdent.value)
            it.executeQuery().toList { toPMotedeltakerBehandlerVarsel() }
        }
    }.firstOrNull()
}

const val queryGetMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndMoteId =
    """
        SELECT MOTEDELTAKER_BEHANDLER_VARSEL.*
        FROM MOTEDELTAKER_BEHANDLER_VARSEL INNER JOIN MOTEDELTAKER_BEHANDLER ON (MOTEDELTAKER_BEHANDLER.id = MOTEDELTAKER_BEHANDLER_VARSEL.motedeltaker_behandler_id)
                                           INNER JOIN MOTE ON (MOTE.id = MOTEDELTAKER_BEHANDLER.mote_id)        
                                           INNER JOIN MOTEDELTAKER_ARBEIDSTAKER ON (MOTE.id = MOTEDELTAKER_ARBEIDSTAKER.mote_id)
        WHERE MOTEDELTAKER_BEHANDLER_VARSEL.varseltype = ?
        AND MOTEDELTAKER_ARBEIDSTAKER.personident = ?
        AND MOTE.id = ?
        ORDER BY MOTEDELTAKER_BEHANDLER_VARSEL.created_at DESC
    """

fun DatabaseInterface.getLatestMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndMoteId(
    varselType: MotedeltakerVarselType,
    arbeidstakerPersonIdent: PersonIdent,
    moteId: Int,
): PMotedeltakerBehandlerVarsel? {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndMoteId).use {
            it.setString(1, varselType.name)
            it.setString(2, arbeidstakerPersonIdent.value)
            it.setInt(3, moteId)
            it.executeQuery().toList { toPMotedeltakerBehandlerVarsel() }
        }
    }.firstOrNull()
}

fun ResultSet.toPMotedeltakerBehandlerVarsel(): PMotedeltakerBehandlerVarsel =
    PMotedeltakerBehandlerVarsel(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        motedeltakerBehandlerId = getInt("motedeltaker_behandler_id"),
        varselType = MotedeltakerVarselType.valueOf(getString("varseltype")),
        pdfId = getInt("pdf_id"),
        status = getString("status"),
        fritekst = getString("fritekst"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
    )
