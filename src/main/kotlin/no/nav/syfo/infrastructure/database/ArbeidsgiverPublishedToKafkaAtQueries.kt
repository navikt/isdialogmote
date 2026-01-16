package no.nav.syfo.infrastructure.database

import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.Dialogmotesvar
import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.SenderType
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.*
import java.time.*
import java.util.*

const val queryGetUnpublishedArbeidsgiversvar =
    """
        SELECT m.uuid as mote_uuid, a.personident, v.svar_type, v.svar_tekst, v.uuid as varsel_uuid, v.created_at as brev_sent_at, v.svar_tidspunkt as svar_received_at
        FROM motedeltaker_arbeidsgiver_varsel v
        INNER JOIN motedeltaker_arbeidsgiver ma ON v.motedeltaker_arbeidsgiver_id = ma.id
        INNER JOIN mote m ON ma.mote_id = m.id
        INNER JOIN motedeltaker_arbeidstaker a ON m.id = a.mote_id
        WHERE v.svar_tidspunkt IS NOT NULL
        AND v.svar_published_to_kafka_at IS NULL
        LIMIT 100;
    """

fun Connection.getUnpublishedArbeidsgiversvar(): List<Dialogmotesvar> {
    return this.prepareStatement(queryGetUnpublishedArbeidsgiversvar).use {
        it.executeQuery().toList { toDialogmotesvarArbeidsgiver() }
    }
}

const val queryUpdateArbeidsgiverVarselPublishedAt =
    """
        UPDATE motedeltaker_arbeidsgiver_varsel
        SET svar_published_to_kafka_at = ?
        WHERE uuid = ?
    """

fun DatabaseInterface.updateArbeidsgiverVarselPublishedAt(
    varseluuid: UUID,
) {
    val now = Timestamp.from(Instant.now())
    connection.use { connection ->
        val rowCount = connection.prepareStatement(queryUpdateArbeidsgiverVarselPublishedAt).use {
            it.setTimestamp(1, now)
            it.setString(2, varseluuid.toString())
            it.executeUpdate()
        }
        if (rowCount != 1) {
            throw SQLException("Failed to save published date for arbeidsgivers motesvar with varsel uuid: $varseluuid ")
        }
        connection.commit()
    }
}

fun ResultSet.toDialogmotesvarArbeidsgiver(): Dialogmotesvar = Dialogmotesvar(
    moteuuid = UUID.fromString(getString("mote_uuid")),
    ident = PersonIdent(getString("personident")),
    svarType = DialogmoteSvarType.valueOf(getString("svar_type")),
    dbRef = UUID.fromString(getString("varsel_uuid")),
    brevSentAt = getTimestamp("brev_sent_at").toLocalDateTime().toOffsetDateTimeUTC(),
    svarReceivedAt = getTimestamp("svar_received_at").toLocalDateTime().toOffsetDateTimeUTC(),
    svarTekst = getString("svar_tekst"),
    senderType = SenderType.ARBEIDSGIVER,
)
