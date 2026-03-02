package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.application.IAvventRepository
import no.nav.syfo.application.ITransaction
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import no.nav.syfo.infrastructure.database.DatabaseInterface
import java.sql.ResultSet
import java.util.UUID

class AvventRepository(
    private val database: DatabaseInterface,
) : IAvventRepository {
    override fun persist(
        avvent: Avvent,
        transaction: ITransaction?,
    ) {
        val connection = transaction?.connection ?: database.connection
        connection
            .prepareStatement(
                """
                INSERT INTO avvent (uuid, created_at, frist, created_by, personident, beskrivelse, is_lukket)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setObject(1, avvent.uuid)
                preparedStatement.setObject(2, avvent.createdAt)
                preparedStatement.setObject(3, avvent.frist)
                preparedStatement.setString(4, avvent.createdBy)
                preparedStatement.setString(5, avvent.personident.value)
                preparedStatement.setString(6, avvent.beskrivelse)
                preparedStatement.setBoolean(7, avvent.isLukket)
                preparedStatement.executeUpdate()
            }
        if (transaction == null) {
            connection.commit()
            connection.close()
        }
    }

    override fun getAvvent(uuid: UUID): Avvent? {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT * FROM avvent WHERE uuid = ?
                    """.trimIndent(),
                ).use { preparedStatement ->
                    preparedStatement.setObject(1, uuid)
                    preparedStatement.executeQuery().use { resultSet ->
                        return if (resultSet.next()) resultSet.toAvvent() else null
                    }
                }
        }
    }

    override fun getActiveAvvent(personident: PersonIdent): Avvent? {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT * FROM avvent WHERE personident = ? AND is_lukket = false
                    """.trimIndent(),
                ).use { preparedStatement ->
                    preparedStatement.setString(1, personident.value)
                    preparedStatement.executeQuery().use { resultSet ->
                        return if (resultSet.next()) resultSet.toAvvent() else null
                    }
                }
        }
    }

    override fun setLukket(
        uuid: UUID,
        transaction: ITransaction?,
    ) {
        val connection = transaction?.connection ?: database.connection
        connection
            .prepareStatement(
                """
                UPDATE avvent SET is_lukket = true WHERE uuid = ?
                """.trimIndent(),
            ).use { preparedStatement ->
                preparedStatement.setObject(1, uuid)
                preparedStatement.executeUpdate()
            }
        if (transaction == null) {
            connection.commit()
            connection.close()
        }
    }
}

private fun ResultSet.toAvvent() =
    Avvent(
        uuid = this.getObject("uuid", UUID::class.java),
        createdAt = this.getObject("created_at", java.time.OffsetDateTime::class.java),
        frist = this.getObject("frist", java.time.LocalDate::class.java),
        createdBy = this.getString("created_by"),
        personident = PersonIdent(this.getString("personident")),
        beskrivelse = this.getString("beskrivelse"),
        isLukket = this.getBoolean("is_lukket"),
    )
