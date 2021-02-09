package no.nav.syfo.testhelper

import no.nav.syfo.application.database.*
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

class TestDatabase : DatabaseInterface {
    private val container = PostgreSQLContainer<Nothing>("postgres:11.1").apply {
        withDatabaseName("db_test")
        withUsername("username")
        withPassword("password")
    }

    private var db: DatabaseInterface
    override val connection: Connection
        get() = db.connection.apply {
            autoCommit = false
        }

    init {
        container.start()
        db = Database(
            DatabaseConfig(
                jdbcUrl = container.jdbcUrl,
                username = "username",
                password = "password"
            )
        )
    }

    fun stop() {
        container.stop()
    }
}
