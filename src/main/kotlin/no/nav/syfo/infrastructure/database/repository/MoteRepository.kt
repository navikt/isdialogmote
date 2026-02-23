package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.api.authentication.configuredJacksonMapper
import no.nav.syfo.application.IMoteRepository
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmote.DialogmoteTidSted
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidsgiver
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerArbeidstaker
import no.nav.syfo.domain.dialogmote.DialogmotedeltakerBehandler
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.Referat
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.model.PDialogmote
import no.nav.syfo.infrastructure.database.model.PMotedeltakerAnnen
import no.nav.syfo.infrastructure.database.model.PMotedeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.model.PMotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.model.PMotedeltakerBehandlerVarselSvar
import no.nav.syfo.infrastructure.database.model.PReferat
import no.nav.syfo.infrastructure.database.model.toDialogmoteDeltakerAnnen
import no.nav.syfo.infrastructure.database.model.toDialogmoteTidSted
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerBehandler
import no.nav.syfo.infrastructure.database.model.toDialogmotedeltakerBehandlerVarsel
import no.nav.syfo.infrastructure.database.model.toReferat
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.infrastructure.database.toPMotedeltakerAnnen
import no.nav.syfo.infrastructure.database.toPMotedeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.toPMotedeltakerArbeidsgiverVarsel
import no.nav.syfo.infrastructure.database.toPMotedeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.toPMotedeltakerArbeidstakerVarsel
import no.nav.syfo.infrastructure.database.toPMotedeltakerBehandler
import no.nav.syfo.infrastructure.database.toPMotedeltakerBehandlerVarsel
import no.nav.syfo.infrastructure.database.toPTidSted
import no.nav.syfo.util.toOffsetDateTimeUTC
import java.sql.Connection
import java.sql.ResultSet
import java.util.*

class MoteRepository(private val database: DatabaseInterface) : IMoteRepository {

    private val mapper = configuredJacksonMapper()

    override fun getMote(moteUUID: UUID): PDialogmote =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTE_FOR_UUID_QUERY).use {
                it.setString(1, moteUUID.toString())
                it.executeQuery().toList { toPDialogmote() }
            }.first()
        }

    override fun getMoterFor(personIdent: PersonIdent): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTER_FOR_PERSONIDENT_QUERY).use {
                it.setString(1, personIdent.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    override fun getDialogmoteList(enhetNr: EnhetNr): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_DIALOGMOTER_FOR_ENHET).use {
                it.setString(1, enhetNr.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    override fun getUnfinishedMoterForEnhet(enhetNr: EnhetNr): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNFINISHED_MOTER_FOR_ENHET).use {
                it.setString(1, enhetNr.value)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    override fun getUnfinishedMoterForVeileder(veilederIdent: String): List<PDialogmote> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNFINISHED_MOTER_FOR_VEILEDER).use {
                it.setString(1, veilederIdent)
                it.executeQuery().toList { toPDialogmote() }
            }
        }

    override fun getMotedeltakerArbeidstaker(moteId: Int): DialogmotedeltakerArbeidstaker {
        return database.connection.use { connection ->
            val arbeidstaker = connection.getMoteDeltakerArbeidstaker(moteId)
            val varsler = connection.prepareStatement(GET_VARSLER_MOTEDELTAKER_ARBEIDSTAKER).use {
                it.setInt(1, arbeidstaker.id)
                it.executeQuery().toList { toPMotedeltakerArbeidstakerVarsel() }
            }

            arbeidstaker.toDialogmotedeltakerArbeidstaker(varsler)
        }
    }

    private fun Connection.getMoteDeltakerArbeidstaker(moteId: Int): PMotedeltakerArbeidstaker =
        this.prepareStatement(GET_MOTEDELTAKER_ARBEIDSTAKER).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerArbeidstaker() }
        }.single()

    override fun getMotedeltakerArbeidsgiver(moteId: Int): DialogmotedeltakerArbeidsgiver {
        return database.connection.use { connection ->
            val arbeidsgiver = connection.getMoteDeltakerArbeidsgiver(moteId)
            val varsler = connection.prepareStatement(GET_VARSLER_MOTEDELTAKER_ARBEIDSGIVER).use {
                it.setInt(1, arbeidsgiver.id)
                it.executeQuery().toList { toPMotedeltakerArbeidsgiverVarsel() }
            }

            arbeidsgiver.toDialogmotedeltakerArbeidsgiver(varsler)
        }
    }

    private fun Connection.getMoteDeltakerArbeidsgiver(moteId: Int): PMotedeltakerArbeidsgiver =
        this.prepareStatement(GET_MOTEDELTAGER_ARBEIDSGIVER).use {
            it.setInt(1, moteId)
            it.executeQuery().toList { toPMotedeltakerArbeidsgiver() }
        }.single()

    override fun getMotedeltakerBehandler(moteId: Int): DialogmotedeltakerBehandler? {
        return database.connection.use { connection ->
            val behandler = connection.prepareStatement(GET_MOTEDELTAKER_BEHANDLER).use {
                it.setInt(1, moteId)
                it.executeQuery().toList { toPMotedeltakerBehandler() }
            }.firstOrNull() ?: return null

            val pVarsler = connection.prepareStatement(GET_VARSLER_MOTEDELTAKER_BEHANDLER).use {
                it.setInt(1, behandler.id)
                it.executeQuery().toList { toPMotedeltakerBehandlerVarsel() }
            }

            val varsler = pVarsler.map { pVarsel ->
                val varselSvar = connection.getMoteDeltakerBehandlerVarselSvar(pVarsel.id)
                pVarsel.toDialogmotedeltakerBehandlerVarsel(varselSvar)
            }

            behandler.toDialogmotedeltakerBehandler(varsler)
        }
    }

    override fun getTidSted(moteId: Int): List<DialogmoteTidSted> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_TID_STED_FOR_MOTE).use {
                it.setInt(1, moteId)
                it.executeQuery().toList { toPTidSted() }
            }.map { it.toDialogmoteTidSted() }
        }

    override fun getReferat(referatUUID: UUID): Referat? =
        database.connection.use { connection ->
            val referat = connection.prepareStatement(GET_REFERAT_QUERY).use {
                it.setString(1, referatUUID.toString())
                it.executeQuery().toList { toPReferat() }.firstOrNull()
            } ?: return null

            val arbeidstaker = connection.getMoteDeltakerArbeidstaker(referat.moteId)
            val arbeidsgiver = connection.getMoteDeltakerArbeidsgiver(referat.moteId)
            val andreDeltakere = connection.getAndreDeltakereForReferatID(referat.id)
                .map { it.toDialogmoteDeltakerAnnen() }

            referat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = arbeidstaker.id,
                motedeltakerArbeidsgiverId = arbeidsgiver.id,
            )
        }

    override fun getFerdigstilteReferatWithoutJournalpostArbeidstakerList(): List<Pair<PersonIdent, Referat>> =
        database.connection.use { connection ->
            val referater = connection.prepareStatement(GET_FERDIGSTILTE_REFERAT_WITHOUT_JOURNALPOST_ARBEIDSTAKER).use {
                it.executeQuery().toList { toPReferat() }
            }
            referater.map { referat ->
                val arbeidstaker = connection.getMoteDeltakerArbeidstaker(referat.moteId)
                val arbeidsgiver = connection.getMoteDeltakerArbeidsgiver(referat.moteId)
                val andreDeltakere = connection.getAndreDeltakereForReferatID(referat.id)
                    .map { it.toDialogmoteDeltakerAnnen() }

                arbeidstaker.personIdent to referat.toReferat(
                    andreDeltakere = andreDeltakere,
                    motedeltakerArbeidstakerId = arbeidstaker.id,
                    motedeltakerArbeidsgiverId = arbeidsgiver.id,
                )
            }
        }

    private fun Connection.getAndreDeltakereForReferatID(referatId: Int): List<PMotedeltakerAnnen> =
        this.prepareStatement(GET_ANDRE_DELTAKERE_FOR_REFERAT_ID).use {
            it.setInt(1, referatId)
            it.executeQuery().toList { toPMotedeltakerAnnen() }
        }

    internal fun Connection.getMoteDeltakerBehandlerVarselSvar(varselId: Int): List<PMotedeltakerBehandlerVarselSvar> =
        this.prepareStatement(GET_VARSEL_SVAR_MOTEDELTAKER_BEHANDLER).use {
            it.setInt(1, varselId)
            it.executeQuery().toList { toPMotedeltakerBehandlerVarselSvar() }
        }

    private fun ResultSet.toPReferat(): PReferat =
        PReferat(
            id = getInt("id"),
            uuid = UUID.fromString(getString("uuid")),
            createdAt = getTimestamp("created_at").toLocalDateTime(),
            updatedAt = getTimestamp("updated_at").toLocalDateTime(),
            moteId = getInt("mote_id"),
            digitalt = getBoolean("digitalt"),
            begrunnelseEndring = getString("begrunnelse_endring"),
            situasjon = getString("situasjon"),
            konklusjon = getString("konklusjon"),
            arbeidstakerOppgave = getString("arbeidstaker_oppgave"),
            arbeidsgiverOppgave = getString("arbeidsgiver_oppgave"),
            veilederOppgave = getString("veileder_oppgave"),
            behandlerOppgave = getString("behandler_oppgave"),
            narmesteLederNavn = getString("narmeste_leder_navn"),
            document = mapper.readValue(getString("document"), object : TypeReference<List<DocumentComponentDTO>>() {}),
            pdfId = getInt("pdf_id"),
            journalpostIdArbeidstaker = getString("journalpost_id"),
            lestDatoArbeidstaker = getTimestamp("lest_dato_arbeidstaker")?.toLocalDateTime(),
            lestDatoArbeidsgiver = getTimestamp("lest_dato_arbeidsgiver")?.toLocalDateTime(),
            brevBestillingsId = getString("brev_bestilling_id"),
            brevBestiltTidspunkt = getTimestamp("brev_bestilt_tidspunkt")?.toLocalDateTime(),
            ferdigstilt = getBoolean("ferdigstilt"),
        )

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

        private const val GET_MOTEDELTAGER_ARBEIDSGIVER =
            """
                SELECT *
                FROM MOTEDELTAKER_ARBEIDSGIVER
                WHERE mote_id = ?
            """

        private const val GET_VARSLER_MOTEDELTAKER_ARBEIDSGIVER =
            """
                SELECT *
                FROM MOTEDELTAKER_ARBEIDSGIVER_VARSEL
                WHERE motedeltaker_arbeidsgiver_id = ?
                ORDER BY created_at DESC
            """

        private const val GET_MOTEDELTAKER_BEHANDLER =
            """
                SELECT *
                FROM MOTEDELTAKER_BEHANDLER
                WHERE mote_id = ?
            """

        private const val GET_VARSLER_MOTEDELTAKER_BEHANDLER =
            """
                SELECT *
                FROM MOTEDELTAKER_BEHANDLER_VARSEL
                WHERE motedeltaker_behandler_id = ?
                ORDER BY created_at DESC
            """

        private const val GET_VARSEL_SVAR_MOTEDELTAKER_BEHANDLER =
            """
                SELECT *
                FROM MOTEDELTAKER_BEHANDLER_VARSEL_SVAR
                WHERE motedeltaker_behandler_varsel_id = ?
                ORDER BY created_at DESC
            """

        private const val GET_TID_STED_FOR_MOTE =
            """
                SELECT *
                FROM TID_STED
                WHERE mote_id = ?
            """

        private const val GET_REFERAT_QUERY =
            """
                SELECT *
                FROM MOTE_REFERAT
                WHERE uuid = ?
            """

        private const val GET_ANDRE_DELTAKERE_FOR_REFERAT_ID =
            """
                SELECT *
                FROM MOTEDELTAKER_ANNEN
                WHERE mote_referat_id = ?
            """

        private const val GET_FERDIGSTILTE_REFERAT_WITHOUT_JOURNALPOST_ARBEIDSTAKER =
            """
                SELECT MOTE_REFERAT.*
                FROM MOTE INNER JOIN MOTE_REFERAT ON (MOTE.ID = MOTE_REFERAT.MOTE_ID)
                    INNER JOIN MOTEDELTAKER_ARBEIDSTAKER ON (MOTE.ID = MOTEDELTAKER_ARBEIDSTAKER.MOTE_ID) 
                WHERE MOTE_REFERAT.journalpost_id IS NULL AND MOTE_REFERAT.ferdigstilt = true
                ORDER BY MOTE_REFERAT.created_at ASC
                LIMIT 20
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

private fun ResultSet.toPMotedeltakerBehandlerVarselSvar(): PMotedeltakerBehandlerVarselSvar =
    PMotedeltakerBehandlerVarselSvar(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getTimestamp("created_at").toLocalDateTime(),
        motedeltakerBehandlerVarselId = getInt("motedeltaker_behandler_varsel_id"),
        svarType = getString("svar_type"),
        svarTekst = getString("svar_tekst"),
        msgId = getString("msg_id"),
        svarPublishedToKafkaAt = getTimestamp("svar_published_to_kafka_at")?.toLocalDateTime()?.toOffsetDateTimeUTC(),
    )
