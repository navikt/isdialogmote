package no.nav.syfo.testhelper

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.infrastructure.database.model.PDialogmote
import no.nav.syfo.infrastructure.database.getDialogmote
import no.nav.syfo.infrastructure.database.getMotedeltakerArbeidstakerVarsel
import no.nav.syfo.infrastructure.database.repository.MoteRepository
import no.nav.syfo.infrastructure.database.repository.toPDialogmote
import no.nav.syfo.infrastructure.database.updateMoteStatus
import no.nav.syfo.infrastructure.database.updateMotedeltakerArbeidstakerBrevBestilt
import no.nav.syfo.infrastructure.database.updateMotedeltakerArbeidstakerVarselJournalpostId
import no.nav.syfo.infrastructure.database.updateReferatBrevBestilt
import no.nav.syfo.infrastructure.database.updateReferatJournalpostIdArbeidstaker
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = try {
            EmbeddedPostgres.start()
        } catch (e: Exception) {
            EmbeddedPostgres.builder().setLocaleConfig("locale", "en_US").start()
        }

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}

fun DatabaseInterface.dropData() {
    val queryList = listOf(
        """
        DELETE FROM MOTE
        """.trimIndent(),
        """
        DELETE FROM TID_STED
        """.trimIndent(),
        """
        DELETE FROM MOTEDELTAKER_ARBEIDSTAKER
        """.trimIndent(),
        """
        DELETE FROM MOTEDELTAKER_ARBEIDSTAKER_VARSEL
        """.trimIndent(),
        """
        DELETE FROM MOTEDELTAKER_ARBEIDSGIVER
        """.trimIndent(),
        """
        DELETE FROM MOTEDELTAKER_ARBEIDSGIVER_VARSEL
        """.trimIndent(),
        """
        DELETE FROM MOTE_STATUS_ENDRET
        """.trimIndent(),
        """
        DELETE FROM MOTE_REFERAT
        """.trimIndent(),
        """
        DELETE FROM MOTEDELTAKER_ANNEN
        """.trimIndent(),
        """
        DELETE FROM MOTEDELTAKER_BEHANDLER
        """.trimIndent(),
        """
        DELETE FROM MOTEDELTAKER_BEHANDLER_VARSEL
        """.trimIndent(),
        """
        DELETE FROM MOTEDELTAKER_BEHANDLER_VARSEL_SVAR
        """.trimIndent(),
    )
    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.addDummyDeltakere() {
    val queryList = listOf(
        """
        INSERT INTO MOTEDELTAKER_ARBEIDSTAKER(uuid,created_at,updated_at,mote_id,personident)
        VALUES('test-xxxyuz',now(), now(), null, 'xyz');
        """.trimIndent(),
        """
        INSERT INTO MOTEDELTAKER_ARBEIDSGIVER(uuid,created_at,updated_at,mote_id,virksomhetsnummer)
            VALUES('test-xxxyuzww',now(), now(), null, 'xyz');
        """.trimIndent(),
    )
    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.updateMoteStatus(
    moteUUID: UUID,
    newMoteStatus: Dialogmote.Status,
) {
    val moteId = getDialogmote(moteUUID).first().id
    this.connection.use {
        it.updateMoteStatus(true, moteId, newMoteStatus)
    }
}

fun DatabaseInterface.setReferatBrevBestilt(
    referatUuid: String,
) {
    val moteRepository = MoteRepository(this)
    val referatId = moteRepository.getReferat(UUID.fromString(referatUuid))?.id
    this.updateReferatBrevBestilt(referatId!!)
}

fun DatabaseInterface.setMotedeltakerArbeidstakerVarselBrevBestilt(
    varselUuid: String,
) {
    val varselId = this.getMotedeltakerArbeidstakerVarsel(UUID.fromString(varselUuid)).first().id
    this.updateMotedeltakerArbeidstakerBrevBestilt(
        varselId,
    )
}

fun DatabaseInterface.setReferatJournalfort(
    referatUuid: String,
    journalpostId: Int,
) {
    val moteRepository = MoteRepository(this)
    val referatId = moteRepository.getReferat(UUID.fromString(referatUuid))?.id
    this.updateReferatJournalpostIdArbeidstaker(
        referatId!!,
        journalpostId
    )
}

fun DatabaseInterface.setMotedeltakerArbeidstakerVarselJournalfort(
    varselUuid: String,
    journalpostId: Int,
) {
    val varselId = this.getMotedeltakerArbeidstakerVarsel(UUID.fromString(varselUuid)).first().id
    this.updateMotedeltakerArbeidstakerVarselJournalpostId(
        varselId,
        journalpostId
    )
}

const val createArbeidstakerVarselQuery =
    """
    INSERT INTO MOTEDELTAKER_ARBEIDSTAKER_VARSEL(uuid, created_at, updated_at, motedeltaker_arbeidstaker_id, varseltype, digitalt, status, fritekst, pdf_id, svar_tidspunkt, svar_type)
    VALUES (?, now(), now(), ?, ?, true, 'OK', 'fritekst', null, ?, ?)
"""

fun Connection.createArbeidstakerVarsel(
    varselUuid: UUID,
    varselType: MotedeltakerVarselType,
    motedeltakerArbeidstakerId: Int? = null,
    harSvart: Boolean = false,
) {
    val tidspunkt = if (harSvart) Timestamp.from(Instant.now()) else null
    val svarType = if (harSvart) DialogmoteSvarType.KOMMER_IKKE.name else null
    prepareStatement(createArbeidstakerVarselQuery).use {
        it.setString(1, varselUuid.toString())
        it.setObject(2, motedeltakerArbeidstakerId)
        it.setString(3, varselType.name)
        it.setObject(4, tidspunkt)
        it.setObject(5, svarType)
        it.execute()
    }
    commit()
}

const val createArbeidsgiverVarselQuery =
    """
    INSERT INTO MOTEDELTAKER_ARBEIDSGIVER_VARSEL(uuid, created_at, updated_at, motedeltaker_arbeidsgiver_id, varseltype, status, fritekst, pdf_id, svar_tidspunkt, svar_type)
    VALUES (?, now(), now(), ?, ?, 'OK', 'fritekst', null, ?, ?)
"""

fun Connection.createArbeidsgiverVarsel(
    varselUuid: UUID,
    varselType: MotedeltakerVarselType,
    motedeltakerArbeidsgiverId: Int? = null,
    harSvart: Boolean = false,
) {
    val tidspunkt = if (harSvart) Timestamp.from(Instant.now()) else null
    val svarType = if (harSvart) DialogmoteSvarType.KOMMER_IKKE.name else null
    prepareStatement(createArbeidsgiverVarselQuery).use {
        it.setString(1, varselUuid.toString())
        it.setObject(2, motedeltakerArbeidsgiverId)
        it.setString(3, varselType.name)
        it.setObject(4, tidspunkt)
        it.setObject(5, svarType)
        it.execute()
    }
    commit()
}

const val createBehandlerVarselQuery =
    """
    INSERT INTO MOTEDELTAKER_BEHANDLER_VARSEL(uuid, created_at, updated_at, motedeltaker_behandler_id, varseltype, status, fritekst, pdf_id)
    VALUES (?, now(), now(), ?, ?, 'OK', 'fritekst', null) RETURNING id
"""

fun Connection.createBehandlerVarsel(
    varseluuid: UUID,
    varselType: MotedeltakerVarselType,
    motedeltakerBehandlerId: Int?,
): Int {
    val varselId = prepareStatement(createBehandlerVarselQuery).use {
        it.setString(1, varseluuid.toString())
        it.setObject(2, motedeltakerBehandlerId)
        it.setString(3, varselType.name)
        it.executeQuery().toList { getInt("id") }.firstOrNull()
    }
    commit()
    return varselId!!
}

const val createBehandlerVarselSvarQuery =
    """
    INSERT INTO MOTEDELTAKER_BEHANDLER_VARSEL_SVAR(uuid, created_at, motedeltaker_behandler_varsel_id, svar_type, svar_tekst, msg_id)
    VALUES (?, now(), ?, ?, '', '')
"""

fun Connection.createBehandlerVarselSvar(
    svarUuid: UUID,
    varselId: Int,
    svarType: DialogmoteSvarType,
) {
    prepareStatement(createBehandlerVarselSvarQuery).use {
        it.setString(1, svarUuid.toString())
        it.setInt(2, varselId)
        it.setString(3, svarType.name)
        it.execute()
    }
    commit()
}

const val queryGetDialogmoteListForEnhetNr =
    """
        SELECT *
        FROM MOTE
        WHERE tildelt_enhet = ?
        ORDER BY MOTE.created_at DESC
    """

fun DatabaseInterface.getDialogmoteList(enhetNr: EnhetNr): List<PDialogmote> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetDialogmoteListForEnhetNr).use {
            it.setString(1, enhetNr.value)
            it.executeQuery().toList { toPDialogmote() }
        }
    }
}
