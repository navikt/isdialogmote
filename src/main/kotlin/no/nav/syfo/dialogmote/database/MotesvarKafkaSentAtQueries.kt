package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.api.domain.Dialogmotesvar
import no.nav.syfo.dialogmote.api.domain.SenderType
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.domain.PersonIdentNumber
import java.sql.Connection
import java.sql.ResultSet
import java.time.*
import java.util.*

const val queryGetArbeidstakeresUnpublishedMotesvar =
    """
        SELECT m.uuid as mote_uuid, ma.personident, v.svar_type,  v.uuid as varsel_uuid, v.created_at as brev_sent_at, v.svar_tidspunkt as svar_received_at
        FROM motedeltaker_arbeidstaker_varsel v
        INNER JOIN motedeltaker_arbeidstaker ma ON v.motedeltaker_arbeidstaker_id = ma.id
        INNER JOIN mote m ON ma.mote_id = m.id
        WHERE v.svar_tidspunkt IS NOT NULL
        AND v.svar_published_to_kafka_at IS NULL
        LIMIT 100;
    """

fun Connection.getArbeidstakeresUnpublishedMotesvar(): List<Dialogmotesvar> {
    return this.prepareStatement(queryGetArbeidstakeresUnpublishedMotesvar).use {
        it.executeQuery().toList { toDialogmotesvar(SenderType.ARBEIDSTAKER) }
    }
}

const val queryGetArbeidsgiveresUnpublishedMotesvar =
    """
        SELECT m.uuid as mote_uuid, a.personident, v.svar_type,  v.uuid as varsel_uuid, v.created_at as brev_sent_at, v.svar_tidspunkt as svar_received_at
        FROM motedeltaker_arbeidsgiver_varsel v
        INNER JOIN motedeltaker_arbeidsgiver ma ON v.motedeltaker_arbeidsgiver_id = ma.id
        INNER JOIN mote m ON ma.mote_id = m.id
        INNER JOIN motedeltaker_arbeidstaker a ON m.id = a.mote_id
        where v.svar_tidspunkt IS NOT NULL
        AND v.svar_published_to_kafka_at IS NULL
        LIMIT 100;
    """

fun Connection.getArbeidsgiveresUnpublishedMotesvar(): List<Dialogmotesvar> {
    return this.prepareStatement(queryGetArbeidsgiveresUnpublishedMotesvar).use {
        it.executeQuery().toList { toDialogmotesvar(SenderType.ARBEIDSGIVER) }
    }
}

const val queryGetBehandleresUnpublishedMotesvar =
    """
        SELECT m.uuid as mote_uuid, ma.personident, s.svar_type, s.uuid as varsel_uuid, mbv.created_at as brev_sent_at, s.created_at as svar_received_at
        FROM motedeltaker_behandler_varsel_svar s
        INNER JOIN motedeltaker_behandler_varsel mbv ON s.motedeltaker_behandler_varsel_id = mbv.id
        INNER JOIN motedeltaker_behandler mb ON mbv.motedeltaker_behandler_id = mb.id
        INNER JOIN mote m ON mb.mote_id = m.id
        INNER JOIN motedeltaker_arbeidstaker ma ON m.id = ma.mote_id
        WHERE s.svar_published_to_kafka_at IS NULL
        LIMIT 100;
    """

fun Connection.getBehandleresUnpublishedMotesvar(): List<Dialogmotesvar> {
    return this.prepareStatement(queryGetBehandleresUnpublishedMotesvar).use {
        it.executeQuery().toList { behandlersDialogmotesvar() }
    }
}

fun ResultSet.toDialogmotesvar(senderType: SenderType): Dialogmotesvar = Dialogmotesvar(
    moteuuid = UUID.fromString(getString("mote_uuid")),
    ident = PersonIdentNumber(getString("personident")),
    svarType = DialogmoteSvarType.valueOf(getString("svar_type")),
    varseluuid = UUID.fromString(getString("varsel_uuid")),
    senderType = senderType,
    brevSentAt = getTimestamp("brev_sent_at").toLocalDateTime().toOffsetDateTimeUTC(),
    svarReceivedAt = getTimestamp("svar_received_at").toLocalDateTime().toOffsetDateTimeUTC(),
)

fun ResultSet.behandlersDialogmotesvar(): Dialogmotesvar = Dialogmotesvar(
    moteuuid = UUID.fromString(getString("mote_uuid")),
    ident = PersonIdentNumber(getString("personident")),
    svarType = DialogmoteSvarType.valueOf(getString("svar_type")),
    varseluuid = UUID.fromString(getString("varsel_uuid")),
    senderType = SenderType.BEHANDLER,
    brevSentAt = getTimestamp("brev_sent_at").toLocalDateTime().toOffsetDateTimeUTC(),
    svarReceivedAt = getTimestamp("svar_received_at").toLocalDateTime().toOffsetDateTimeUTC(),
)

fun LocalDateTime.toOffsetDateTimeUTC() =
    this.atZone(ZoneId.of("Europe/Oslo")).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
