package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.application.IAvventRepository
import no.nav.syfo.application.ITransaction
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.Avvent
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class AvventRepository(
    private val database: DatabaseInterface,
) : IAvventRepository {
    override fun persist(
        avvent: Avvent,
        transaction: ITransaction?,
    ): Avvent =
        transaction?.connection?.insertAvvent(avvent)
            ?: database.connection.use { connection ->
                val persisted = connection.insertAvvent(avvent)
                connection.commit()
                persisted
            }

    override fun getAvvent(uuid: UUID): Avvent? =
        database.connection.use { connection ->
            connection.queryAvvent(uuid)
        }

    override fun getActiveAvvent(
        personident: PersonIdent,
        transaction: ITransaction?,
    ): Avvent? =
        if (transaction != null) {
            transaction.connection.queryActiveAvvent(personident)
        } else {
            database.connection.use { connection ->
                connection.queryActiveAvvent(personident)
            }
        }

    override fun getActiveAvventForPersonidenter(personidenter: List<PersonIdent>): List<Avvent> {
        if (personidenter.isEmpty()) return emptyList()
        return database.connection.use { connection ->
            connection.queryActiveAvventForPersonidenter(personidenter)
        }
    }

    override fun setLukket(
        uuid: UUID,
        transaction: ITransaction?,
    ) {
        transaction?.connection?.updateLukket(uuid)
            ?: database.connection.use { connection ->
                connection.updateLukket(uuid)
                connection.commit()
            }
    }

    private fun Connection.insertAvvent(avvent: Avvent): Avvent =
        this
            .prepareStatement(INSERT_AVVENT)
            .use { preparedStatement ->
                preparedStatement.setObject(1, avvent.uuid)
                preparedStatement.setObject(2, avvent.createdAt)
                preparedStatement.setObject(3, avvent.frist)
                preparedStatement.setString(4, avvent.createdBy)
                preparedStatement.setString(5, avvent.personident.value)
                preparedStatement.setString(6, avvent.beskrivelse)
                preparedStatement.setBoolean(7, avvent.isLukket)
                preparedStatement.executeQuery().toList { toAvvent() }.single()
            }

    private fun Connection.queryAvvent(uuid: UUID): Avvent? =
        this
            .prepareStatement(GET_AVVENT)
            .use { preparedStatement ->
                preparedStatement.setObject(1, uuid)
                preparedStatement.executeQuery().toList { toAvvent() }.firstOrNull()
            }

    private fun Connection.queryActiveAvvent(personident: PersonIdent): Avvent? =
        this
            .prepareStatement(GET_ACTIVE_AVVENT_FOR_PERSON)
            .use { preparedStatement ->
                preparedStatement.setString(1, personident.value)
                preparedStatement.executeQuery().toList { toAvvent() }.firstOrNull()
            }

    private fun Connection.queryActiveAvventForPersonidenter(personidenter: List<PersonIdent>): List<Avvent> {
        val personidentArray = this.createArrayOf("varchar", personidenter.map { it.value }.toTypedArray())
        return this
            .prepareStatement(GET_ACTIVE_AVVENT_FOR_PERSONIDENTER)
            .use { preparedStatement ->
                preparedStatement.setArray(1, personidentArray)
                preparedStatement.executeQuery().toList { toAvvent() }
            }
    }

    private fun Connection.updateLukket(uuid: UUID) {
        this
            .prepareStatement(UPDATE_LUKKET)
            .use { preparedStatement ->
                preparedStatement.setObject(1, uuid)
                preparedStatement.executeUpdate()
            }
    }

    companion object {
        private const val INSERT_AVVENT =
            """
                INSERT INTO avvent (uuid, created_at, frist, created_by, personident, beskrivelse, is_lukket)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING *
            """
        private const val GET_AVVENT =
            """
                SELECT * FROM avvent WHERE uuid = ?
            """
        private const val GET_ACTIVE_AVVENT_FOR_PERSON =
            """
                SELECT * FROM avvent 
                WHERE personident = ?
                AND is_lukket = false
                ORDER BY created_by desc
                LIMIT 1
            """
        private const val GET_ACTIVE_AVVENT_FOR_PERSONIDENTER =
            """
                SELECT * FROM avvent 
                WHERE personident = ANY(?)
                AND is_lukket = false
                ORDER BY created_at desc 
            """
        private const val UPDATE_LUKKET =
            """
                UPDATE avvent SET is_lukket = true WHERE uuid = ?
            """
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
