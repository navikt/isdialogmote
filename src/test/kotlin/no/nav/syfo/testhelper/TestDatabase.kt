package no.nav.syfo.testhelper

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.application.database.DatabaseInterface
import org.flywaydb.core.Flyway
import java.sql.Connection

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
        DELETE FROM MOTEDELTAKER_ARBEIDSGIVER
        """.trimIndent(),
        """
        DELETE FROM MOTE_STATUS_ENDRET
        """.trimIndent(),
    )
    queryList.forEach { query ->
        this.connection.prepareStatement(query)
    }
}