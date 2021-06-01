package no.nav.syfo.dialogmote.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.database.toList
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.DocumentComponentDTO
import no.nav.syfo.dialogmote.domain.NewReferat
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.*
import java.time.Instant
import java.util.UUID

private val mapper = configuredJacksonMapper()

const val queryGetReferatForMoteUUID =
    """
        SELECT MOTE_REFERAT.*
        FROM MOTE INNER JOIN MOTE_REFERAT on (MOTE.id = MOTE_REFERAT.mote_id)
        WHERE MOTE.uuid = ?
    """

fun DatabaseInterface.getReferat(moteUUID: UUID): List<PReferat> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetReferatForMoteUUID).use {
            it.setString(1, moteUUID.toString())
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
        situasjon = getString("situasjon"),
        konklusjon = getString("konklusjon"),
        arbeidstakerOppgave = getString("arbeidstaker_oppgave"),
        arbeidsgiverOppgave = getString("arbeidsgiver_oppgave"),
        veilederOppgave = getString("veileder_oppgave"),
        document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
        pdf = getBytes("pdf"),
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
        situasjon,
        konklusjon,
        arbeidstaker_oppgave,
        arbeidsgiver_oppgave,
        veileder_oppgave,
        document,
        pdf
    ) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) RETURNING id
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
): Pair<Int, UUID> {
    val referatUuid = UUID.randomUUID()
    val now = Timestamp.from(Instant.now())

    val referatIdList = this.prepareStatement(queryCreateReferat).use {
        it.setString(1, referatUuid.toString())
        it.setTimestamp(2, now)
        it.setTimestamp(3, now)
        it.setInt(4, newReferat.moteId)
        it.setString(5, newReferat.situasjon)
        it.setString(6, newReferat.konklusjon)
        it.setString(7, newReferat.arbeidstakerOppgave)
        it.setString(8, newReferat.arbeidsgiverOppgave)
        it.setString(9, newReferat.veilederOppgave)
        it.setObject(10, mapper.writeValueAsString(newReferat.document))
        it.setBytes(11, pdf)
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
