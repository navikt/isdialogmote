package no.nav.syfo.dialogmote.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerAnnen
import no.nav.syfo.dialogmote.database.domain.PReferat
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.NewReferat
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

private val mapper = configuredJacksonMapper()

const val queryGetReferatForMoteUUID =
    """
        SELECT MOTE_REFERAT.*
        FROM MOTE INNER JOIN MOTE_REFERAT on (MOTE.id = MOTE_REFERAT.mote_id)
        WHERE MOTE.uuid = ?
    """

fun DatabaseInterface.getReferatForMote(moteUUID: UUID): List<PReferat> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetReferatForMoteUUID).use {
            it.setString(1, moteUUID.toString())
            it.executeQuery().toList { toPReferat() }
        }
    }
}

const val queryGetReferat =
    """
        SELECT *
        FROM MOTE_REFERAT
        WHERE uuid = ?
    """

fun DatabaseInterface.getReferat(referatUUID: UUID): List<PReferat> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetReferat).use {
            it.setString(1, referatUUID.toString())
            it.executeQuery().toList { toPReferat() }
        }
    }
}

fun ResultSet.toPReferat(): PReferat =
    PReferat(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteId = getInt("mote_id"),
        digitalt = getBoolean("digitalt"),
        situasjon = getString("situasjon"),
        konklusjon = getString("konklusjon"),
        arbeidstakerOppgave = getString("arbeidstaker_oppgave"),
        arbeidsgiverOppgave = getString("arbeidsgiver_oppgave"),
        veilederOppgave = getString("veileder_oppgave"),
        narmesteLederNavn = getString("narmeste_leder_navn"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
        pdf = getBytes("pdf"),
        journalpostId = getString("journalpost_id"),
        lestDatoArbeidstaker = getTimestamp("lest_dato_arbeidstaker")?.toLocalDateTime(),
        lestDatoArbeidsgiver = getTimestamp("lest_dato_arbeidsgiver")?.toLocalDateTime(),
        brevBestillingId = getString("brev_bestilling_id"),
        brevBestiltTidspunkt = getTimestamp("brev_bestilt_tidspunkt")?.toLocalDateTime(),
    )

const val queryGetDialogmotedeltakerAnnenForReferatID =
    """
        SELECT *
        FROM MOTEDELTAKER_ANNEN
        WHERE mote_referat_id = ?
    """

fun DatabaseInterface.getAndreDeltakereForReferatID(referatId: Int): List<PMotedeltakerAnnen> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmotedeltakerAnnenForReferatID).use {
            it.setInt(1, referatId)
            it.executeQuery().toList { toPMotedeltakerAnnen() }
        }
    }
}

fun ResultSet.toPMotedeltakerAnnen(): PMotedeltakerAnnen =
    PMotedeltakerAnnen(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        moteReferatId = getInt("mote_referat_id"),
        funksjon = getString("funksjon"),
        navn = getString("navn"),
    )

const val queryCreateReferat =
    """
    INSERT INTO MOTE_REFERAT (
        id,
        uuid,
        created_at,
        updated_at,
        mote_id,
        digitalt,
        situasjon,
        konklusjon,
        arbeidstaker_oppgave,
        arbeidsgiver_oppgave,
        veileder_oppgave,
        narmeste_leder_navn,
        document,
        pdf
    ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) RETURNING id
    """

const val queryCreateMotedeltakerAnnen =
    """
    INSERT INTO MOTEDELTAKER_ANNEN (
        id,
        uuid,
        created_at,
        updated_at,
        mote_referat_id,
	    funksjon,
	    navn
    ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun Connection.createNewReferat(
    commit: Boolean = true,
    newReferat: NewReferat,
    pdf: ByteArray,
    digitalt: Boolean,
): Pair<Int, UUID> {
    val referatUuid = UUID.randomUUID()
    val now = Timestamp.from(Instant.now())

    val referatIdList = this.prepareStatement(queryCreateReferat).use {
        it.setString(1, referatUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, newReferat.moteId)
        it.setBoolean(5, digitalt)
        it.setString(6, newReferat.situasjon)
        it.setString(7, newReferat.konklusjon)
        it.setString(8, newReferat.arbeidstakerOppgave)
        it.setString(9, newReferat.arbeidsgiverOppgave)
        it.setString(10, newReferat.veilederOppgave)
        it.setString(11, newReferat.narmesteLederNavn)
        it.setObject(12, mapper.writeValueAsString(newReferat.document))
        it.setBytes(13, pdf)
        it.executeQuery().toList { getInt("id") }
    }
    if (referatIdList.size != 1) {
        throw SQLException("Creating Referat failed, no rows affected.")
    }
    val referatId = referatIdList.first()

    newReferat.andreDeltakere.forEach { deltaker ->
        this.prepareStatement(queryCreateMotedeltakerAnnen).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setTimestamp(2, now)
            it.setTimestamp(3, now)
            it.setInt(4, referatId)
            it.setString(5, deltaker.funksjon)
            it.setString(6, deltaker.navn)
            it.executeQuery()
        }
    }

    if (commit) {
        this.commit()
    }
    return Pair(referatId, referatUuid)
}

const val queryGetReferatWithoutJournalpost =
    """
        SELECT MOTEDELTAKER_ARBEIDSTAKER.PERSONIDENT, MOTE_REFERAT.*
        FROM MOTE INNER JOIN MOTE_REFERAT ON (MOTE.ID = MOTE_REFERAT.MOTE_ID)
                  INNER JOIN MOTEDELTAKER_ARBEIDSTAKER ON (MOTE.ID = MOTEDELTAKER_ARBEIDSTAKER.MOTE_ID) 
        WHERE MOTE_REFERAT.journalpost_id IS NULL
    """

fun DatabaseInterface.getReferatWithoutJournalpostList(): List<Pair<PersonIdentNumber, PReferat>> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetReferatWithoutJournalpost).use {
            it.executeQuery().toList {
                Pair(PersonIdentNumber(getString(1)), toPReferat())
            }
        }
    }
}

const val queryUpdateReferatJournalpostId =
    """
        UPDATE MOTE_REFERAT
        SET journalpost_id = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateReferatJournalpostId(
    referatId: Int,
    journalpostId: Int,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateReferatJournalpostId).use {
            it.setInt(1, journalpostId)
            it.setInt(2, referatId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryUpdateReferatBrevBestillingId =
    """
        UPDATE MOTE_REFERAT
        SET brev_bestilling_id = ?, brev_bestilt_tidspunkt = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateReferatBrevBestillingId(
    referatId: Int,
    brevBestillingId: String,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateReferatBrevBestillingId).use {
            it.setString(1, brevBestillingId)
            it.setTimestamp(2, Timestamp.from(Instant.now()))
            it.setInt(3, referatId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryUpdateReferatLestDatoArbeidstaker =
    """
    UPDATE MOTE_REFERAT 
    SET lest_dato_arbeidstaker = ?
    WHERE uuid = ?
    """

fun Connection.updateReferatLestDatoArbeidstaker(
    referatUUID: UUID,
) {
    val now = LocalDateTime.now()
    this.prepareStatement(queryUpdateReferatLestDatoArbeidstaker).use {
        it.setTimestamp(1, Timestamp.valueOf(now))
        it.setString(2, referatUUID.toString())
        it.execute()
    }
}

const val queryUpdateReferatLestDatoArbeidsgiver =
    """
    UPDATE MOTE_REFERAT 
    SET lest_dato_arbeidsgiver = ?
    WHERE uuid = ?
    """

fun Connection.updateReferatLestDatoArbeidsgiver(
    referatUUID: UUID,
) {
    val now = LocalDateTime.now()
    this.prepareStatement(queryUpdateReferatLestDatoArbeidsgiver).use {
        it.setTimestamp(1, Timestamp.valueOf(now))
        it.setString(2, referatUUID.toString())
        it.execute()
    }
}
