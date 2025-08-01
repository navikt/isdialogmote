package no.nav.syfo.infrastructure.database

import io.ktor.server.application.*
import no.nav.syfo.Environment
import no.nav.syfo.isDev
import no.nav.syfo.isProd

lateinit var applicationDatabase: DatabaseInterface
fun Application.databaseModule(
    environment: Environment
) {
    isDev {
        applicationDatabase = Database(
            DatabaseConfig(
                jdbcUrl = "jdbc:postgresql://localhost:5432/isdialogmote_dev",
                password = "password",
                username = "username"
            )
        )
    }

    isProd {
        applicationDatabase = Database(
            DatabaseConfig(
                jdbcUrl = environment.jdbcUrl(),
                username = environment.isdialogmoteDbUsername,
                password = environment.isdialogmoteDbPassword
            )
        )
    }
}
