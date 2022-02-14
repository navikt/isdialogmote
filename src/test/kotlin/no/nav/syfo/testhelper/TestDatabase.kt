package no.nav.syfo.testhelper

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.domain.DialogmoteStatus
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.util.UUID

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = try {
            EmbeddedPostgres.start()
        } catch (e: Exception) {
            EmbeddedPostgres.builder().start()
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
    newMoteStatus: DialogmoteStatus,
) {
    val moteId = getDialogmote(moteUUID).first().id
    this.connection.use {
        it.updateMoteStatus(true, moteId, newMoteStatus)
    }
}

fun DatabaseInterface.setReferatBrevBestilt(
    referatUuid: String,
    bestillingsId: String,
) {
    val referatId = this.getReferat(UUID.fromString(referatUuid)).first().id
    this.updateReferatBrevBestillingsId(
        referatId,
        bestillingsId
    )
}

fun DatabaseInterface.setMotedeltakerArbeidstakerVarselBrevBestilt(
    varselUuid: String,
    bestillingsId: String,
) {
    val varselId = this.getMotedeltakerArbeidstakerVarsel(UUID.fromString(varselUuid)).first().id
    this.updateMotedeltakerArbeidstakerBrevBestillingsId(
        varselId,
        bestillingsId
    )
}

fun DatabaseInterface.setReferatJournalfort(
    referatUuid: String,
    journalpostId: Int,
) {
    val referatId = this.getReferat(UUID.fromString(referatUuid)).first().id
    this.updateReferatJournalpostIdArbeidstaker(
        referatId,
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
