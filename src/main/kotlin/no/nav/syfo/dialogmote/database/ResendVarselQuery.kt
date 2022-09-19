package no.nav.syfo.dialogmote.database

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

const val queryGetMotedeltakerArbeidsgiverVarselStartEndDate =
    """
        SELECT mav.varseltype, at.personident, ag.virksomhetsnummer
        FROM motedeltaker_arbeidsgiver ag
        JOIN motedeltaker_arbeidsgiver_varsel mav on ag.id = mav.motedeltaker_arbeidsgiver_id
        JOIN motedeltaker_arbeidstaker at on ag.mote_id = at.mote_id
        WHERE mav.created_at > ? AND mav.created_at < ? AND mav.lest_dato IS NULL;
    """

fun DatabaseInterface.getMotedeltakerArbeidsgiverVarsel(
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
): List<Triple<String, String, String>> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidsgiverVarselStartEndDate).use {
            it.setTimestamp(1, Timestamp.valueOf(startDateTime))
            it.setTimestamp(2, Timestamp.valueOf(endDateTime))
            it.executeQuery().toList { toTriple() }
        }
    }
}

fun ResultSet.toTriple(): Triple<String, String, String> {
    val first = getString("varseltype")
    val second = getString("personident")
    val third = getString("virksomhetsnummer")

    return Triple(first, second, third)
}

const val queryGetMotedeltakerArbeidsgiverReferatVarselStartEndDate =
    """
        SELECT ag.virksomhetsnummer, at.personident
        FROM mote_referat mr
        JOIN motedeltaker_arbeidsgiver ag on mr.mote_id = ag.mote_id
        JOIN motedeltaker_arbeidstaker at on mr.mote_id = at.mote_id
        WHERE mr.updated_at > ? AND mr.updated_at < ? AND ferdigstilt IS TRUE AND mr.lest_dato_arbeidsgiver IS NULL;
    """

fun DatabaseInterface.getMotedeltakerArbeidsgiverReferatVarsel(
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
): List<Triple<String, String, String>> {
    return this.connection.use { connection ->
        connection.prepareStatement(queryGetMotedeltakerArbeidsgiverReferatVarselStartEndDate).use {
            it.setTimestamp(1, Timestamp.valueOf(startDateTime))
            it.setTimestamp(2, Timestamp.valueOf(endDateTime))
            it.executeQuery().toList { toReferatResult() }
        }
    }
}

fun ResultSet.toReferatResult(): Triple<String, String, String> {
    val first = MotedeltakerVarselType.REFERAT.name
    val second = getString("personident")
    val third = getString("virksomhetsnummer")

    return Triple(first, second, third)
}
