package no.nav.syfo.infrastructure.database.dialogmote.database.repository

import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.PDialogmote
import no.nav.syfo.infrastructure.database.dialogmote.database.domain.toDialogmotedeltakerArbeidstakerWithVarsler
import no.nav.syfo.infrastructure.database.dialogmote.database.toPMotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.dialogmote.database.toPMotedeltakerArbeidstakerVarsel
import no.nav.syfo.infrastructure.database.toList
import java.sql.ResultSet
import java.util.*

class MoteRepository(private val database: DatabaseInterface) {

    fun getMote(moteUUID: UUID): PDialogmote =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTE_FOR_UUID_QUERY).use {
                it.setString(1, moteUUID.toString())
                it.executeQuery().toList { toPDialogmote() }
            }.first()
        }

    fun getMoterFor(personIdent: PersonIdent): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTER_FOR_PERSONIDENT_QUERY).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    fun getDialogmoteList(enhetNr: EnhetNr): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTER_FOR_ENHET).use {
                it.setString(1, enhetNr.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    fun getUnfinishedMoterForEnhet(enhetNr: EnhetNr): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNFINISHED_MOTER_FOR_ENHET).use {
                it.setString(1, enhetNr.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    fun getUnfinishedMoterForVeileder(veilederIdent: String): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNFINISHED_MOTER_FOR_VEILEDER).use {
                it.setString(1, veilederIdent)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    fun getMotedeltakerArbeidstaker(moteId: Int): DialogmotedeltakerArbeidstaker {
        return database.connection.use { connection ->
            val arbeidstaker = connection.prepareStatement(GET_MOTEDELTAKER_ARBEIDSTAKER).use {
                it.setInt(1, moteId)
                it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
            }.first()
            val varsler = connection.prepareStatement(GET_VARSLER_MOTEDELTAKER_ARBEIDSTAKER).use {
                it.setInt(1, arbeidstaker.id)
                it.executeQuery().toList { toPMotedeltakerArbeidstakerVarsel() }
            }
            arbeidstaker.toDialogmotedeltakerArbeidstakerWithVarsler(varsler)
        }
    }

    companion object {
        private const val GET_DIALOGMOTE_FOR_UUID_QUERY =
            """
                SELECT *
                FROM MOTE
                WHERE uuid = ?
            """

        private const val GET_DIALOGMOTER_FOR_PERSONIDENT_QUERY =
            """
                SELECT *
                FROM MOTE
                INNER JOIN MOTEDELTAKER_ARBEIDSTAKER on MOTEDELTAKER_ARBEIDSTAKER.mote_id = MOTE.id
                WHERE personident = ?
                ORDER BY MOTE.created_at DESC
            """

        private const val GET_DIALOGMOTER_FOR_ENHET =
            """
                SELECT *
                FROM MOTE
                WHERE tildelt_enhet = ?
                ORDER BY MOTE.created_at DESC
            """

        private const val GET_UNFINISHED_MOTER_FOR_ENHET =
            """
                SELECT *
                FROM MOTE
                WHERE tildelt_enhet = ? AND status IN ('INNKALT', 'NYTT_TID_STED')
                ORDER BY MOTE.created_at DESC
            """

        private const val GET_UNFINISHED_MOTER_FOR_VEILEDER =
            """
                SELECT *
                FROM MOTE
                WHERE tildelt_veileder_ident = ? AND status IN ('INNKALT', 'NYTT_TID_STED')
                ORDER BY MOTE.created_at DESC
            """

        private const val GET_MOTEDELTAKER_ARBEIDSTAKER =
            """
                SELECT *
                FROM MOTEDELTAKER_ARBEIDSTAKER
                WHERE mote_id = ?
            """

        private const val GET_VARSLER_MOTEDELTAKER_ARBEIDSTAKER =
            """
                SELECT *
                FROM MOTEDELTAKER_ARBEIDSTAKER_VARSEL
                WHERE motedeltaker_arbeidstaker_id = ?
                ORDER BY created_at DESC
            """
    }
}

fun ResultSet.toPDialogmote(): PDialogmote =
    PDialogmote(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        updatedAt = getTimestamp("updated_at").toLocalDateTime(),
        status = getString("status"),
        opprettetAv = getString("opprettet_av"),
        tildeltVeilederIdent = getString("tildelt_veileder_ident"),
        tildeltEnhet = getString("tildelt_enhet")
    )
