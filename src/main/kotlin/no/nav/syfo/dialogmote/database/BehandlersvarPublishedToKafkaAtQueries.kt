package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.*
import java.time.*
import java.util.*

const val queryGetUnpublishedBehandlersvar =
    """
        SELECT m.uuid as mote_uuid, ma.personident, s.svar_type, s.uuid as svar_uuid, mbv.created_at as brev_sent_at, s.created_at as svar_received_at
        FROM motedeltaker_behandler_varsel_svar s
        INNER JOIN motedeltaker_behandler_varsel mbv ON s.motedeltaker_behandler_varsel_id = mbv.id
        INNER JOIN motedeltaker_behandler mb ON mbv.motedeltaker_behandler_id = mb.id
        INNER JOIN mote m ON mb.mote_id = m.id
        INNER JOIN motedeltaker_arbeidstaker ma ON m.id = ma.mote_id
        WHERE s.svar_published_to_kafka_at IS NULL
        LIMIT 100;
    """

fun Connection.getUnpublishedBehandlersvar(): List<Behandlersvar> {
    return this.prepareStatement(queryGetUnpublishedBehandlersvar).use {
        it.executeQuery().toList { behandlersvar() }
    }
}

const val queryUpdateBehandlersvarPublishedAt =
    """
        UPDATE motedeltaker_behandler_varsel_svar
        SET svar_published_to_kafka_at = ?
        WHERE uuid = ?
    """

fun Connection.updateBehandlersvarPublishedAt(
    svaruuid: UUID,
) {
    val now = Timestamp.from(Instant.now())
    val rowCount = this.prepareStatement(queryUpdateBehandlersvarPublishedAt).use {
        it.setTimestamp(1, now)
        it.setString(2, svaruuid.toString())
        it.executeUpdate()
    }
    if (rowCount != 1) {
        throw SQLException("Failed to save published date for behandlers motesvar with svar uuid: $svaruuid ")
    }
    this.commit()
}

fun ResultSet.behandlersvar(): Behandlersvar = Behandlersvar(
    moteuuid = UUID.fromString(getString("mote_uuid")),
    ident = PersonIdentNumber(getString("personident")),
    svarType = DialogmoteSvarType.valueOf(getString("svar_type")),
    svaruuid = UUID.fromString(getString("svar_uuid")),
    brevSentAt = getTimestamp("brev_sent_at").toLocalDateTime().toOffsetDateTimeUTC(),
    svarReceivedAt = getTimestamp("svar_received_at").toLocalDateTime().toOffsetDateTimeUTC(),
)
