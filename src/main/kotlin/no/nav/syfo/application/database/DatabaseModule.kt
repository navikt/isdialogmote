package no.nav.syfo.application.database

import io.ktor.application.*
import no.nav.syfo.application.*

lateinit var database: DatabaseInterface
fun Application.databaseModule(
    environment: Environment
) {
    isDev {
        database = Database(
            DatabaseConfig(
                jdbcUrl = "jdbc:postgresql://localhost:5432/isdialogmote_dev",
                password = "password",
                username = "username"
            )
        )
    }

    isProd {
        database = Database(
            DatabaseConfig(
                jdbcUrl = environment.jdbcUrl(),
                username = environment.isdialogmoteDbUsername,
                password = environment.isdialogmoteDbPassword
            )
        )
    }
}
