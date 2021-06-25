package no.nav.syfo.dialogmote

import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederDTO
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.person.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.*
import no.nav.syfo.varsel.MotedeltakerVarselType
import no.nav.syfo.varsel.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.varsel.narmesteleder.NarmesteLederVarselService
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*

class DialogmoteService(
    private val database: DatabaseInterface,
    private val arbeidstakerVarselService: ArbeidstakerVarselService,
    private val narmesteLederVarselService: NarmesteLederVarselService,
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val narmesteLederClient: NarmesteLederClient,
    private val oppfolgingstilfelleClient: OppfolgingstilfelleClient,
    private val pdfGenClient: PdfGenClient,
) {
    fun getDialogmote(
        moteUUID: UUID
    ): Dialogmote {
        return database.getDialogmote(moteUUID).first().let { pDialogmote ->
            dialogmote(pDialogmote)
        }
    }

    fun getDialogmote(
        id: Int
    ): Dialogmote {
        return database.getDialogmote(id).first().let { pDialogmote ->
            dialogmote(pDialogmote)
        }
    }

    private fun dialogmote(pDialogmote: PDialogmote): Dialogmote {
        val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
        val motedeltakerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
        val dialogmoteTidStedList = getDialogmoteTidStedList(pDialogmote.id)
        val referat = getReferatForMote(pDialogmote.uuid)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmoteTidStedList = dialogmoteTidStedList,
            referat = referat,
        )
    }

    fun getDialogmoteList(
        personIdentNumber: PersonIdentNumber,
    ): List<Dialogmote> {
        return database.getDialogmoteList(personIdentNumber).map { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteList(
        enhetNr: EnhetNr,
    ): List<Dialogmote> {
        return database.getDialogmoteList(enhetNr).map { pDialogmote ->
            extendDialogmoteRelations(pDialogmote)
        }
    }

    fun extendDialogmoteRelations(
        pDialogmote: PDialogmote,
    ): Dialogmote {
        val motedeltakerArbeidstaker = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstaker(pDialogmote.id)
        val motedeltakerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiver(pDialogmote.id)
        val dialogmoteTidStedList = getDialogmoteTidStedList(pDialogmote.id)
        val referat = getReferatForMote(pDialogmote.uuid)
        return pDialogmote.toDialogmote(
            dialogmotedeltakerArbeidstaker = motedeltakerArbeidstaker,
            dialogmotedeltakerArbeidsgiver = motedeltakerArbeidsgiver,
            dialogmoteTidStedList = dialogmoteTidStedList,
            referat = referat,
        )
    }

    fun getDialogmoteTidStedList(
        moteId: Int
    ): List<DialogmoteTidSted> {
        return database.getTidSted(moteId).map {
            it.toDialogmoteTidSted()
        }
    }

    suspend fun createMoteinnkalling(
        newDialogmoteDTO: NewDialogmoteDTO,
        callId: String,
        token: String,
    ): Boolean {
        val personIdentNumber = PersonIdentNumber(newDialogmoteDTO.arbeidstaker.personIdent)

        val anyUnfinishedDialogmote = getDialogmoteList(personIdentNumber).anyUnfinished()
        if (anyUnfinishedDialogmote) {
            throw IllegalStateException("Denied access to create Dialogmote: unfinished Dialogmote exists for PersonIdent")
        }

        val virksomhetsnummer = Virksomhetsnummer(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer)
        val narmesteLeder = narmesteLederClient.activeLeader(
            personIdentNumber = personIdentNumber,
            virksomhetsnummer = virksomhetsnummer,
            token = token,
            callId = callId
        )
        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            val behandlendeEnhet = behandlendeEnhetClient.getEnhet(
                callId = callId,
                personIdentNumber = personIdentNumber,
                token = token,
            ) ?: throw RuntimeException("Failed to request BehandlendeEnhet of Person")

            val newDialogmote = newDialogmoteDTO.toNewDialogmote(
                requestByNAVIdent = getNAVIdentFromToken(token),
                narmesteLeder = narmesteLeder,
                navEnhet = EnhetNr(behandlendeEnhet.enhetId),
            )

            val pdfInnkallingArbeidstaker = pdfGenClient.pdfInnkallingArbeidstaker(
                callId = callId,
                documentComponentDTOList = newDialogmoteDTO.arbeidstaker.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidstaker")

            val pdfInnkallingArbeidsgiver = pdfGenClient.pdfInnkallingArbeidsgiver(
                callId = callId,
                documentComponentDTOList = newDialogmoteDTO.arbeidsgiver.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidsgiver")

            val createdDialogmoteIdentifiers: CreatedDialogmoteIdentifiers

            database.connection.use { connection ->
                createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(
                    commit = false,
                    newDialogmote = newDialogmote,
                )
                createMoteStatusEndring(
                    callId = callId,
                    connection = connection,
                    dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                    dialogmoteStatus = newDialogmote.status,
                    opprettetAv = newDialogmote.opprettetAv,
                    personIdentNumber = newDialogmote.arbeidstaker.personIdent,
                    token = token,
                )
                createAndSendVarsel(
                    connection = connection,
                    arbeidstakerId = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdList.first,
                    arbeidstakerUuid = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdList.second,
                    arbeidsgiverId = createdDialogmoteIdentifiers.motedeltakerArbeidsgiverIdList.first,
                    arbeidstakerPersonIdent = newDialogmote.arbeidstaker.personIdent,
                    pdfArbeidstaker = pdfInnkallingArbeidstaker,
                    pdfArbeidsgiver = pdfInnkallingArbeidsgiver,
                    narmesteLeder = narmesteLeder,
                    varselType = MotedeltakerVarselType.INNKALT,
                    fritekstArbeidstaker = newDialogmote.arbeidstaker.fritekstInnkalling.orEmpty(),
                    fritekstArbeidsgiver = newDialogmote.arbeidsgiver.fritekstInnkalling.orEmpty(),
                    documentArbeidstaker = newDialogmoteDTO.arbeidstaker.innkalling,
                    documentArbeidsgiver = newDialogmoteDTO.arbeidsgiver.innkalling,
                )

                connection.commit()
            }
            true
        }
    }

    suspend fun avlysMoteinnkalling(
        callId: String,
        dialogmote: Dialogmote,
        avlysDialogmote: AvlysDialogmoteDTO,
        token: String
    ): Boolean {
        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw RuntimeException("Failed to Avlys Dialogmote: already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw RuntimeException("Failed to Avlys Dialogmote: already Avlyst")
        }
        val isDialogmoteTidPassed = dialogmote.tidStedList.latest()?.passed()
            ?: throw RuntimeException("Failed to Avlys Dialogmote: No TidSted found")

        val pdfAvlysningArbeidstaker = pdfGenClient.pdfAvlysningArbeidstaker(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidstaker.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidstaker")

        val pdfAvlysningArbeidsgiver = pdfGenClient.pdfAvlysningArbeidsgiver(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidsgiver.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidsgiver")

        val narmesteLeder = narmesteLederClient.activeLeader(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            token = token,
            callId = callId
        )
        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            database.connection.use { connection ->
                updateMoteStatus(
                    callId = callId,
                    connection = connection,
                    dialogmoteId = dialogmote.id,
                    newDialogmoteStatus = DialogmoteStatus.AVLYST,
                    opprettetAv = getNAVIdentFromToken(token),
                    personIdentNumber = dialogmote.arbeidstaker.personIdent,
                    token = token,
                )
                if (!isDialogmoteTidPassed) {
                    createAndSendVarsel(
                        connection = connection,
                        arbeidstakerId = dialogmote.arbeidstaker.id,
                        arbeidstakerUuid = dialogmote.arbeidstaker.uuid,
                        arbeidsgiverId = dialogmote.arbeidsgiver.id,
                        arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                        pdfArbeidstaker = pdfAvlysningArbeidstaker,
                        pdfArbeidsgiver = pdfAvlysningArbeidsgiver,
                        narmesteLeder = narmesteLeder,
                        varselType = MotedeltakerVarselType.AVLYST,
                        fritekstArbeidstaker = avlysDialogmote.arbeidstaker.begrunnelse,
                        fritekstArbeidsgiver = avlysDialogmote.arbeidsgiver.begrunnelse,
                        documentArbeidstaker = avlysDialogmote.arbeidstaker.avlysning,
                        documentArbeidsgiver = avlysDialogmote.arbeidsgiver.avlysning
                    )
                }
                connection.commit()
            }
            true
        }
    }

    suspend fun nyttMoteinnkallingTidSted(
        callId: String,
        dialogmote: Dialogmote,
        endreDialogmoteTidSted: EndreTidStedDialogmoteDTO,
        token: String
    ): Boolean {
        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw RuntimeException("Failed to change tid/sted, already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw RuntimeException("Failed to change tid/sted, already Avlyst")
        }
        val pdfEndringArbeidstaker = pdfGenClient.pdfEndringTidStedArbeidstaker(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidstaker.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidstaker")

        val pdfEndringArbeidsgiver = pdfGenClient.pdfEndringTidStedArbeidsgiver(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidsgiver.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidsgiver")

        val narmesteLeder = narmesteLederClient.activeLeader(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            token = token,
            callId = callId
        )

        return if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            false
        } else {
            database.connection.use { connection ->
                connection.updateMoteTidSted(
                    commit = false,
                    moteId = dialogmote.id,
                    newDialogmoteTidSted = endreDialogmoteTidSted,
                )
                updateMoteStatus(
                    callId = callId,
                    connection = connection,
                    dialogmoteId = dialogmote.id,
                    newDialogmoteStatus = DialogmoteStatus.NYTT_TID_STED,
                    opprettetAv = getNAVIdentFromToken(token),
                    personIdentNumber = dialogmote.arbeidstaker.personIdent,
                    token = token,
                )
                createAndSendVarsel(
                    connection = connection,
                    arbeidstakerId = dialogmote.arbeidstaker.id,
                    arbeidstakerUuid = dialogmote.arbeidstaker.uuid,
                    arbeidsgiverId = dialogmote.arbeidsgiver.id,
                    arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                    pdfArbeidstaker = pdfEndringArbeidstaker,
                    pdfArbeidsgiver = pdfEndringArbeidsgiver,
                    narmesteLeder = narmesteLeder,
                    varselType = MotedeltakerVarselType.NYTT_TID_STED,
                    fritekstArbeidstaker = endreDialogmoteTidSted.arbeidstaker.begrunnelse,
                    fritekstArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.begrunnelse,
                    documentArbeidstaker = endreDialogmoteTidSted.arbeidstaker.endringsdokument,
                    documentArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.endringsdokument,
                )

                connection.commit()
            }
            true
        }
    }

    private fun createAndSendVarsel(
        connection: Connection,
        arbeidstakerUuid: UUID,
        arbeidstakerId: Int,
        arbeidsgiverId: Int,
        arbeidstakerPersonIdent: PersonIdentNumber,
        pdfArbeidstaker: ByteArray,
        pdfArbeidsgiver: ByteArray,
        narmesteLeder: NarmesteLederDTO,
        varselType: MotedeltakerVarselType,
        fritekstArbeidstaker: String = "",
        fritekstArbeidsgiver: String = "",
        documentArbeidstaker: List<DocumentComponentDTO> = emptyList(),
        documentArbeidsgiver: List<DocumentComponentDTO> = emptyList(),
    ) {
        val (_, varselArbeidstakerId) = connection.createMotedeltakerVarselArbeidstaker(
            commit = false,
            motedeltakerArbeidstakerId = arbeidstakerId,
            status = "OK",
            varselType = varselType,
            digitalt = true,
            pdf = pdfArbeidstaker,
            fritekst = fritekstArbeidstaker,
            document = documentArbeidstaker,
        )
        connection.createMotedeltakerVarselArbeidsgiver(
            commit = false,
            motedeltakerArbeidsgiverId = arbeidsgiverId,
            status = "OK",
            varselType = varselType,
            pdf = pdfArbeidsgiver,
            fritekst = fritekstArbeidsgiver,
            document = documentArbeidsgiver,
        )
        val now = LocalDateTime.now()
        arbeidstakerVarselService.sendVarsel(
            createdAt = now,
            personIdent = arbeidstakerPersonIdent,
            type = varselType,
            motedeltakerArbeidstakerUuid = arbeidstakerUuid,
            varselUuid = varselArbeidstakerId,
        )
        narmesteLederVarselService.sendVarsel(
            createdAt = now,
            narmesteLeder = narmesteLeder,
            varseltype = varselType
        )
    }

    suspend fun ferdigstillMote(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
        token: String,
    ): Boolean {
        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw RuntimeException("Failed to Ferdigstille Dialogmote, already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw RuntimeException("Failed to Ferdigstille Dialogmote, already Avlyst")
        }

        val narmesteLeder = narmesteLederClient.activeLeader(
            personIdentNumber = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
            token = token,
            callId = callId
        )
        if (narmesteLeder == null) {
            log.warn("Denied access to Dialogmoter: No NarmesteLeder was found for person")
            throw RuntimeException("Denied access to Dialogmoter: No NarmesteLeder was found for person")
        }

        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            documentComponentDTOList = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        val now = LocalDateTime.now()

        database.connection.use { connection ->

            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            updateMoteStatus(
                callId = callId,
                connection = connection,
                dialogmoteId = dialogmote.id,
                newDialogmoteStatus = DialogmoteStatus.FERDIGSTILT,
                opprettetAv = opprettetAv,
                personIdentNumber = dialogmote.arbeidstaker.personIdent,
                token = token,
            )
            val (_, referatUuid) = connection.createNewReferat(
                commit = false,
                newReferat = referat.toNewReferat(dialogmote.id),
                pdf = pdfReferat,
                digitalt = true,
            )
            arbeidstakerVarselService.sendVarsel(
                createdAt = now,
                personIdent = dialogmote.arbeidstaker.personIdent,
                type = MotedeltakerVarselType.REFERAT,
                motedeltakerArbeidstakerUuid = dialogmote.arbeidstaker.uuid,
                varselUuid = referatUuid,
            )
            narmesteLederVarselService.sendVarsel(
                createdAt = now,
                narmesteLeder = narmesteLeder,
                varseltype = MotedeltakerVarselType.REFERAT,
            )
            connection.commit()
        }
        return true
    }

    private suspend fun updateMoteStatus(
        callId: String,
        connection: Connection,
        dialogmoteId: Int,
        newDialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        personIdentNumber: PersonIdentNumber,
        token: String,
    ) {
        connection.updateMoteStatus(
            commit = false,
            moteId = dialogmoteId,
            moteStatus = newDialogmoteStatus,
        )
        createMoteStatusEndring(
            callId = callId,
            connection = connection,
            dialogmoteId = dialogmoteId,
            dialogmoteStatus = newDialogmoteStatus,
            opprettetAv = opprettetAv,
            personIdentNumber = personIdentNumber,
            token = token,
        )
    }

    private suspend fun createMoteStatusEndring(
        callId: String,
        connection: Connection,
        dialogmoteId: Int,
        dialogmoteStatus: DialogmoteStatus,
        opprettetAv: String,
        personIdentNumber: PersonIdentNumber,
        token: String,
    ) {
        val tilfelleStart = oppfolgingstilfelleClient.oppfolgingstilfelle(
            callId = callId,
            personIdentNumber = personIdentNumber,
            token = token,
        )?.fom ?: throw RuntimeException("Cannot create MoteStatusEndring: No TilfelleStart was found")
        connection.createMoteStatusEndring(
            commit = false,
            moteId = dialogmoteId,
            opprettetAv = opprettetAv,
            status = dialogmoteStatus,
            tilfelleStart = tilfelleStart,
        )
    }

    fun getReferat(
        referatUUID: UUID
    ): Referat? {
        return database.getReferat(referatUUID).firstOrNull()?.let { pReferat ->
            val andreDeltakere = getAndreDeltakere(pReferat.id)
            val motedeltakerArbeidstakerId = database.getMoteDeltakerArbeidstaker(pReferat.moteId).id
            pReferat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
            )
        }
    }

    fun getReferatForMote(
        moteUUID: UUID
    ): Referat? {
        return database.getReferatForMote(moteUUID).firstOrNull()?.let { pReferat ->
            val andreDeltakere = getAndreDeltakere(pReferat.id)
            val motedeltakerArbeidstakerId = database.getMoteDeltakerArbeidstaker(pReferat.moteId).id
            pReferat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
            )
        }
    }

    private fun getAndreDeltakere(
        referatId: Int
    ): List<DialogmotedeltakerAnnen> {
        return database.getAndreDeltakereForReferatID(referatId).map { pMotedeltakerAnnen ->
            pMotedeltakerAnnen.toDialogmoteDeltakerAnnen()
        }
    }

    fun getArbeidstakerBrevFromUuid(
        brevUuid: UUID
    ): ArbeidstakerBrev {
        val motedeltakerArbeidstakerVarsel = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerVarselList(
            uuid = brevUuid
        ).firstOrNull()

        val referat = getReferat(
            referatUUID = brevUuid
        )

        if (motedeltakerArbeidstakerVarsel == null && referat == null) {
            throw IllegalArgumentException("No Brev found for arbeidstaker with uuid=$brevUuid")
        }
        return motedeltakerArbeidstakerVarsel ?: referat!!
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteService::class.java)
    }
}
