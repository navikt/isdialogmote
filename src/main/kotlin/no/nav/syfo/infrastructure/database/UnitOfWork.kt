package no.nav.syfo.infrastructure.database

import java.sql.Connection

class UnitOfWork(val connection: Connection)

fun <T> DatabaseInterface.transaction(block: UnitOfWork.() -> T): T {
    return connection.use { conn ->
        val uow = UnitOfWork(conn)
        val result = uow.block()
        conn.commit()
        result
    }
}
