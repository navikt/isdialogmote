package no.nav.syfo.infrastructure.database

import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.Dialogmotesvar
import no.nav.syfo.infrastructure.cronjob.dialogmotesvar.SenderType
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.*
import java.time.*
import java.util.*

const val queryGetUnpublishedArbeidstakersvar =
    """
        SELECT m.uuid as mote_uuid, ma.personident, v.svar_type, v.svar_tekst, v.uuid as varsel_uuid, v.created_at as brev_sent_at, v.svar_tidspunkt as svar_received_at
        FROM motedeltaker_arbeidstaker_varsel v
        INNER JOIN motedeltaker_arbeidstaker ma ON v.motedeltaker_arbeidstaker_id = ma.id
        INNER JOIN mote m ON ma.mote_id = m.id
        WHERE v.svar_tidspunkt IS NOT NULL
        AND v.svar_published_to_kafka_at IS NULL
        LIMIT 100;
    """

fun Connection.getUnpublishedArbeidstakersvar(): List<Dialogmotesvar> =
    this.prepareStatement(queryGetUnpublishedArbeidstakersvar).use {
        it.executeQuery().toList { toDialogmotesvarArbeidstaker() }
    }

const val queryUpdateArbeidstakerVarselPublishedAt =
    """
        UPDATE motedeltaker_arbeidstaker_varsel
        SET svar_published_to_kafka_at = ?
        WHERE uuid = ?
    """

fun DatabaseInterface.updateArbeidstakerVarselPublishedAt(
    varseluuid: UUID,
) {
    val now = Timestamp.from(Instant.now())
    connection.use { connection ->
        val rowCount = connection.prepareStatement(queryUpdateArbeidstakerVarselPublishedAt).use {
            it.setTimestamp(1, now)
            it.setString(2, varseluuid.toString())
            it.executeUpdate()
        }
        if (rowCount != 1) {
            throw SQLException("Failed to save published date for arbeidstakers motesvar with varsel uuid: $varseluuid ")
        }
        connection.commit()
    }
}

fun ResultSet.toDialogmotesvarArbeidstaker(): Dialogmotesvar = Dialogmotesvar(
    moteuuid = UUID.fromString(getString("mote_uuid")),
    ident = PersonIdent(getString("personident")),
    svarType = DialogmoteSvarType.valueOf(getString("svar_type")),
    dbRef = UUID.fromString(getString("varsel_uuid")),
    brevSentAt = getTimestamp("brev_sent_at").toLocalDateTime().toOffsetDateTimeUTC(),
    svarReceivedAt = getTimestamp("svar_received_at").toLocalDateTime().toOffsetDateTimeUTC(),
    svarTekst = getString("svar_tekst"),
    senderType = SenderType.ARBEIDSTAKER,
)
