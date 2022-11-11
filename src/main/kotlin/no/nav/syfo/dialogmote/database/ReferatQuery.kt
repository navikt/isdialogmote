package no.nav.syfo.dialogmote.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.PMotedeltakerAnnen
import no.nav.syfo.dialogmote.database.domain.PReferat
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

const val queryGetReferatForMoteUUID =
    """
        SELECT MOTE_REFERAT.*
        FROM MOTE INNER JOIN MOTE_REFERAT on (MOTE.id = MOTE_REFERAT.mote_id)
        WHERE MOTE.uuid = ?
        ORDER BY MOTE_REFERAT.created_at DESC
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
        begrunnelseEndring = getString("begrunnelse_endring"),
        situasjon = getString("situasjon"),
        konklusjon = getString("konklusjon"),
        arbeidstakerOppgave = getString("arbeidstaker_oppgave"),
        arbeidsgiverOppgave = getString("arbeidsgiver_oppgave"),
        veilederOppgave = getString("veileder_oppgave"),
        behandlerOppgave = getString("behandler_oppgave"),
        narmesteLederNavn = getString("narmeste_leder_navn"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
        pdfId = getInt("pdf_id"),
        journalpostIdArbeidstaker = getString("journalpost_id"),
        lestDatoArbeidstaker = getTimestamp("lest_dato_arbeidstaker")?.toLocalDateTime(),
        lestDatoArbeidsgiver = getTimestamp("lest_dato_arbeidsgiver")?.toLocalDateTime(),
        brevBestillingsId = getString("brev_bestilling_id"),
        brevBestiltTidspunkt = getTimestamp("brev_bestilt_tidspunkt")?.toLocalDateTime(),
        ferdigstilt = getBoolean("ferdigstilt"),
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
        begrunnelse_endring,
        situasjon,
        konklusjon,
        arbeidstaker_oppgave,
        arbeidsgiver_oppgave,
        veileder_oppgave,
        behandler_oppgave,
        narmeste_leder_navn,
        document,
        pdf_id,
        ferdigstilt,
        altinn_sent_at
    ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?) RETURNING id
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

const val queryDeleteMotedeltakerAnnen =
    """
    DELETE FROM MOTEDELTAKER_ANNEN WHERE mote_referat_id=?
    """

fun Connection.createNewReferat(
    commit: Boolean = true,
    newReferat: NewReferat,
    pdfId: Int?,
    digitalt: Boolean,
    sendAltinn: Boolean,
): Pair<Int, UUID> {
    val referatUuid = UUID.randomUUID()
    val now = Timestamp.from(Instant.now())
    val nowUTC = nowUTC()

    val referatIdList = this.prepareStatement(queryCreateReferat).use {
        it.setString(1, referatUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, newReferat.moteId)
        it.setBoolean(5, digitalt)
        it.setString(6, newReferat.begrunnelseEndring)
        it.setString(7, newReferat.situasjon)
        it.setString(8, newReferat.konklusjon)
        it.setString(9, newReferat.arbeidstakerOppgave)
        it.setString(10, newReferat.arbeidsgiverOppgave)
        it.setString(11, newReferat.veilederOppgave)
        it.setString(12, newReferat.behandlerOppgave)
        it.setString(13, newReferat.narmesteLederNavn)
        it.setObject(14, mapper.writeValueAsString(newReferat.document))
        if (pdfId != null) {
            it.setInt(15, pdfId)
        } else {
            it.setNull(15, Types.INTEGER)
        }
        it.setBoolean(16, newReferat.ferdigstilt)
        if (sendAltinn) {
            it.setObject(17, nowUTC)
        } else {
            it.setNull(17, Types.TIMESTAMP)
        }
        it.executeQuery().toList { getInt("id") }
    }
    if (referatIdList.size != 1) {
        throw SQLException("Creating Referat failed, no rows affected.")
    }
    val referatId = referatIdList.first()
    updateAndreDeltakereForReferat(referatId, newReferat)
    if (commit) {
        this.commit()
    }
    return Pair(referatId, referatUuid)
}

const val queryUpdateReferat =
    """
        UPDATE MOTE_REFERAT
        SET updated_at = ?,
            digitalt = ?,
            begrunnelse_endring = ?,
            situasjon = ?,
            konklusjon = ?,
            arbeidstaker_oppgave = ?,
            arbeidsgiver_oppgave = ?,
            veileder_oppgave = ?,
            behandler_oppgave = ?,
            narmeste_leder_navn = ?,
            document = ?::jsonb,
            pdf_id = ?,
            ferdigstilt = ?,       
            altinn_sent_at = ?       
        WHERE id = ?
    """

fun Connection.updateReferat(
    commit: Boolean = true,
    referat: Referat,
    newReferat: NewReferat,
    pdfId: Int?,
    digitalt: Boolean,
    sendAltinn: Boolean,
): Pair<Int, UUID> {
    val now = Timestamp.from(Instant.now())
    val nowUTC = nowUTC()

    val rowCount = this.prepareStatement(queryUpdateReferat).use {
        it.setTimestamp(1, now)
        it.setBoolean(2, digitalt)
        it.setString(3, newReferat.begrunnelseEndring)
        it.setString(4, newReferat.situasjon)
        it.setString(5, newReferat.konklusjon)
        it.setString(6, newReferat.arbeidstakerOppgave)
        it.setString(7, newReferat.arbeidsgiverOppgave)
        it.setString(8, newReferat.veilederOppgave)
        it.setString(9, newReferat.behandlerOppgave)
        it.setString(10, newReferat.narmesteLederNavn)
        it.setObject(11, mapper.writeValueAsString(newReferat.document))
        if (pdfId != null) {
            it.setInt(12, pdfId)
        } else {
            it.setNull(12, Types.INTEGER)
        }
        it.setBoolean(13, newReferat.ferdigstilt)
        if (sendAltinn) {
            it.setObject(14, nowUTC)
        } else {
            it.setNull(14, Types.TIMESTAMP)
        }
        it.setInt(15, referat.id)
        it.executeUpdate()
    }
    if (rowCount != 1) {
        throw SQLException("Update Referat failed, no rows affected.")
    }

    updateAndreDeltakereForReferat(referat.id, newReferat)

    if (commit) {
        this.commit()
    }
    return Pair(referat.id, referat.uuid)
}

private fun Connection.updateAndreDeltakereForReferat(
    referatId: Int,
    newReferat: NewReferat,
) {
    this.prepareStatement(queryDeleteMotedeltakerAnnen).use {
        it.setInt(1, referatId)
        it.executeUpdate()
    }
    val now = Timestamp.from(Instant.now())
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
}

const val queryGetFerdigstilteReferatWithoutJournalpostArbeidstaker =
    """
        SELECT MOTEDELTAKER_ARBEIDSTAKER.PERSONIDENT, MOTE_REFERAT.*
        FROM MOTE INNER JOIN MOTE_REFERAT ON (MOTE.ID = MOTE_REFERAT.MOTE_ID)
                  INNER JOIN MOTEDELTAKER_ARBEIDSTAKER ON (MOTE.ID = MOTEDELTAKER_ARBEIDSTAKER.MOTE_ID) 
        WHERE MOTE_REFERAT.journalpost_id IS NULL AND MOTE_REFERAT.ferdigstilt = true
        ORDER BY MOTE_REFERAT.created_at ASC
        LIMIT 20
    """

fun DatabaseInterface.getFerdigstilteReferatWithoutJournalpostArbeidstakerList(): List<Pair<PersonIdent, PReferat>> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetFerdigstilteReferatWithoutJournalpostArbeidstaker).use {
            it.executeQuery().toList {
                Pair(PersonIdent(getString(1)), toPReferat())
            }
        }
    }
}

const val queryGetFerdigstilteReferatWithoutJournalpostArbeidsgiver =
    """
        SELECT MOTEDELTAKER_ARBEIDSGIVER.VIRKSOMHETSNUMMER, MOTE_REFERAT.*
        FROM MOTE INNER JOIN MOTE_REFERAT ON (MOTE.ID = MOTE_REFERAT.MOTE_ID)
                  INNER JOIN MOTEDELTAKER_ARBEIDSGIVER ON (MOTE.ID = MOTEDELTAKER_ARBEIDSGIVER.MOTE_ID) 
        WHERE MOTE_REFERAT.journalpost_ag_id IS NULL AND MOTE_REFERAT.ferdigstilt = true
        ORDER BY MOTE_REFERAT.created_at ASC
        LIMIT 20
    """

fun DatabaseInterface.getFerdigstilteReferatWithoutJournalpostArbeidsgiverList(): List<Pair<Virksomhetsnummer, PReferat>> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetFerdigstilteReferatWithoutJournalpostArbeidsgiver).use {
            it.executeQuery().toList {
                Pair(Virksomhetsnummer(getString(1)), toPReferat())
            }
        }
    }
}

const val queryGetFerdigstilteReferatWithoutJournalpostBehandler =
    """
        SELECT MOTE_REFERAT.*
        FROM MOTE INNER JOIN MOTE_REFERAT ON (MOTE.ID = MOTE_REFERAT.MOTE_ID)
                  INNER JOIN MOTEDELTAKER_BEHANDLER ON (MOTE.ID = MOTEDELTAKER_BEHANDLER.MOTE_ID) 
        WHERE MOTE_REFERAT.journalpost_beh_id IS NULL AND MOTE_REFERAT.ferdigstilt = true AND MOTEDELTAKER_BEHANDLER.mottar_referat
        ORDER BY MOTE_REFERAT.created_at ASC
        LIMIT 20
    """

fun DatabaseInterface.getFerdigstilteReferatWithoutJournalpostBehandlerList(): List<PReferat> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetFerdigstilteReferatWithoutJournalpostBehandler).use {
            it.executeQuery().toList {
                toPReferat()
            }
        }
    }
}

const val queryUpdateReferatJournalpostIdArbeidstaker =
    """
        UPDATE MOTE_REFERAT
        SET journalpost_id = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateReferatJournalpostIdArbeidstaker(
    referatId: Int,
    journalpostId: Int,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateReferatJournalpostIdArbeidstaker).use {
            it.setInt(1, journalpostId)
            it.setInt(2, referatId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryUpdateReferatJournalpostIdArbeidsgiver =
    """
        UPDATE MOTE_REFERAT
        SET journalpost_ag_id = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateReferatJournalpostIdArbeidsgiver(
    referatId: Int,
    journalpostId: Int,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateReferatJournalpostIdArbeidsgiver).use {
            it.setInt(1, journalpostId)
            it.setInt(2, referatId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryUpdateReferatJournalpostIdBehandler =
    """
        UPDATE MOTE_REFERAT
        SET journalpost_beh_id = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateReferatJournalpostIdBehandler(
    referatId: Int,
    journalpostId: Int,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateReferatJournalpostIdBehandler).use {
            it.setInt(1, journalpostId)
            it.setInt(2, referatId)
            it.execute()
        }
        connection.commit()
    }
}

const val queryGetReferatForFysiskBrevUtsending =
    """
        SELECT *
        FROM MOTE_REFERAT 
        WHERE digitalt IS FALSE
              AND journalpost_id IS NOT NULL
              AND brev_bestilt_tidspunkt IS NULL
    """

fun DatabaseInterface.getReferatForFysiskBrevUtsending(): List<PReferat> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetReferatForFysiskBrevUtsending).use {
            it.executeQuery().toList { toPReferat() }
        }
    }
}

const val queryUpdateReferatBrevBestillingsId =
    """
        UPDATE MOTE_REFERAT
        SET brev_bestilling_id = ?, brev_bestilt_tidspunkt = ?
        WHERE id = ?
    """

fun DatabaseInterface.updateReferatBrevBestillingsId(
    referatId: Int,
    brevBestillingsId: String?,
) {
    this.connection.use { connection ->
        connection.prepareStatement(queryUpdateReferatBrevBestillingsId).use {
            it.setString(1, brevBestillingsId)
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
    WHERE uuid = ? AND lest_dato_arbeidstaker IS NULL
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
    WHERE uuid = ? AND lest_dato_arbeidsgiver IS NULL
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
