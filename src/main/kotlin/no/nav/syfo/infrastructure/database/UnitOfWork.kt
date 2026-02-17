package no.nav.syfo.infrastructure.database

import java.sql.Connection

class UnitOfWork(val connection: Connection)

fun <T> DatabaseInterface.transaction(block: UnitOfWork.() -> T): T {
    return connection.use { conn ->
        val unitOfWork = UnitOfWork(conn)
        val result = unitOfWork.block()
        conn.commit()
        result
    }
}
