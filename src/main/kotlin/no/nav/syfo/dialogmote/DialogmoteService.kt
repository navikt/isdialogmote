package no.nav.syfo.dialogmote

import no.nav.syfo.application.api.authentication.getNAVIdentFromToken
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.dialogmote.api.domain.*
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.*
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*

class DialogmoteService(
    private val database: DatabaseInterface,
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val dialogmotestatusService: DialogmotestatusService,
    private val dialogmoterelasjonService: DialogmoterelasjonService,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val narmesteLederClient: NarmesteLederClient,
    private val pdfGenClient: PdfGenClient,
    private val kontaktinformasjonClient: KontaktinformasjonClient,
    private val varselService: VarselService,
    private val pdlClient: PdlClient
) {
    fun getDialogmote(
        moteUUID: UUID
    ): Dialogmote {
        return database.getDialogmote(moteUUID).first().let { pDialogmote ->
            dialogmoterelasjonService.extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteList(
        personIdent: PersonIdent,
    ): List<Dialogmote> {
        return database.getDialogmoteList(personIdent).map { pDialogmote ->
            dialogmoterelasjonService.extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteList(
        enhetNr: EnhetNr,
    ): List<Dialogmote> {
        return database.getDialogmoteList(enhetNr).map { pDialogmote ->
            dialogmoterelasjonService.extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteUnfinishedList(enhetNr: EnhetNr): List<Dialogmote> {
        return database.getDialogmoteUnfinishedList(enhetNr).map { pDialogmote ->
            dialogmoterelasjonService.extendDialogmoteRelations(pDialogmote)
        }
    }

    fun getDialogmoteUnfinishedListForVeilederIdent(veilederIdent: String): List<Dialogmote> {
        return database.getDialogmoteUnfinishedListForVeilederIdent(veilederIdent).map { pDialogmote ->
            dialogmoterelasjonService.extendDialogmoteRelations(pDialogmote)
        }
    }

    suspend fun createMoteinnkalling(
        newDialogmoteDTO: NewDialogmoteDTO,
        callId: String,
        token: String,
    ) {
        val personIdent = PersonIdent(newDialogmoteDTO.arbeidstaker.personIdent)
        val virksomhetsnummer = Virksomhetsnummer(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer)

        val anyUnfinishedDialogmote =
            getDialogmoteList(personIdent).filter {
                it.arbeidsgiver.virksomhetsnummer == virksomhetsnummer
            }.anyUnfinished()

        if (anyUnfinishedDialogmote) {
            throw ConflictException("Denied access to create Dialogmote: unfinished Dialogmote exists for PersonIdent")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val arbeidstakernavn = pdlClient.navn(personIdent)

        val behandlendeEnhet = behandlendeEnhetClient.getEnhet(
            callId = callId,
            personIdent = personIdent,
            token = token,
        ) ?: throw RuntimeException("Failed to request (or missing) BehandlendeEnhet of Person")

        val newDialogmote = newDialogmoteDTO.toNewDialogmote(
            requestByNAVIdent = getNAVIdentFromToken(token),
            navEnhet = EnhetNr(behandlendeEnhet.enhetId),
        )

        val pdfInnkallingArbeidstaker = pdfGenClient.pdfInnkalling(
            callId = callId,
            documentComponentDTOList = newDialogmoteDTO.arbeidstaker.innkalling,
        ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidstaker")

        val pdfInnkallingArbeidsgiver = pdfGenClient.pdfInnkalling(
            callId = callId,
            documentComponentDTOList = newDialogmoteDTO.arbeidsgiver.innkalling,
        ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidsgiver")

        val pdfInnkallingBehandler = newDialogmoteDTO.behandler?.let {
            pdfGenClient.pdfInnkalling(
                callId = callId,
                documentComponentDTOList = it.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Behandler")
        }

        val createdDialogmoteIdentifiers: CreatedDialogmoteIdentifiers

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = personIdent,
            token = token,
            callId = callId,
        )

        database.connection.use { connection ->
            createdDialogmoteIdentifiers = connection.createNewDialogmoteWithReferences(
                commit = false,
                newDialogmote = newDialogmote,
            )
            dialogmotestatusService.createMoteStatusEndring(
                callId = callId,
                connection = connection,
                newDialogmote = newDialogmote,
                dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                dialogmoteStatus = newDialogmote.status,
                opprettetAv = newDialogmote.opprettetAv,
                token = token,
            )
            createAndSendVarsel(
                connection = connection,
                arbeidstakerId = createdDialogmoteIdentifiers.motedeltakerArbeidstakerIdPair.first,
                arbeidstakernavn = arbeidstakernavn,
                arbeidsgiverId = createdDialogmoteIdentifiers.motedeltakerArbeidsgiverIdPair.first,
                behandlerId = createdDialogmoteIdentifiers.motedeltakerBehandlerIdPair?.first,
                behandlerRef = newDialogmote.behandler?.behandlerRef,
                behandlerParentVarselId = null,
                behandlerInnkallingUuid = null,
                arbeidstakerPersonIdent = newDialogmote.arbeidstaker.personIdent,
                pdfArbeidstaker = pdfInnkallingArbeidstaker,
                pdfArbeidsgiver = pdfInnkallingArbeidsgiver,
                pdfBehandler = pdfInnkallingBehandler,
                narmesteLeder = narmesteLeder,
                varselType = MotedeltakerVarselType.INNKALT,
                fritekstArbeidstaker = newDialogmote.arbeidstaker.fritekstInnkalling.orEmpty(),
                fritekstArbeidsgiver = newDialogmote.arbeidsgiver.fritekstInnkalling.orEmpty(),
                fritekstBehandler = newDialogmote.behandler?.fritekstInnkalling.orEmpty(),
                documentArbeidstaker = newDialogmoteDTO.arbeidstaker.innkalling,
                documentArbeidsgiver = newDialogmoteDTO.arbeidsgiver.innkalling,
                documentBehandler = newDialogmoteDTO.behandler?.innkalling ?: emptyList(),
                moteTidspunkt = newDialogmoteDTO.tidSted.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer,
                token = token,
                callId = callId,
            )

            connection.commit()
        }
    }

    suspend fun avlysMoteinnkalling(
        callId: String,
        dialogmote: Dialogmote,
        avlysDialogmote: AvlysDialogmoteDTO,
        token: String,
    ) {
        val virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer

        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw ConflictException("Failed to Avlys Dialogmote: already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw ConflictException("Failed to Avlys Dialogmote: already Avlyst")
        }
        if (dialogmote.behandler != null && avlysDialogmote.behandler == null) {
            throw IllegalArgumentException("Failed to Avlys Dialogmote: missing behandler")
        }

        val pdfAvlysningArbeidstaker = pdfGenClient.pdfAvlysning(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidstaker.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidstaker")

        val pdfAvlysningArbeidsgiver = pdfGenClient.pdfAvlysning(
            callId = callId,
            documentComponentDTOList = avlysDialogmote.arbeidsgiver.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidsgiver")

        val pdfAvlysningBehandler = avlysDialogmote.behandler?.let {
            pdfGenClient.pdfAvlysning(
                callId = callId,
                documentComponentDTOList = it.avlysning,
            ) ?: throw RuntimeException("Failed to request PDF - Avlysning Behandler")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val arbeidstakernavn = pdlClient.navn(dialogmote.arbeidstaker.personIdent)

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = dialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        database.connection.use { connection ->
            dialogmotestatusService.updateMoteStatus(
                callId = callId,
                connection = connection,
                dialogmote = dialogmote,
                newDialogmoteStatus = DialogmoteStatus.AVLYST,
                opprettetAv = getNAVIdentFromToken(token),
                token = token,
            )
            dialogmotedeltakerService.slettBrukeroppgaverPaMote(
                dialogmote = dialogmote
            )
            createAndSendVarsel(
                connection = connection,
                arbeidstakerId = dialogmote.arbeidstaker.id,
                arbeidstakernavn = arbeidstakernavn,
                arbeidsgiverId = dialogmote.arbeidsgiver.id,
                behandlerId = dialogmote.behandler?.id,
                behandlerRef = dialogmote.behandler?.behandlerRef,
                behandlerParentVarselId = dialogmote.behandler?.findParentVarselId(),
                behandlerInnkallingUuid = dialogmote.behandler?.findInnkallingVarselUuid(),
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                pdfArbeidstaker = pdfAvlysningArbeidstaker,
                pdfArbeidsgiver = pdfAvlysningArbeidsgiver,
                pdfBehandler = pdfAvlysningBehandler,
                narmesteLeder = narmesteLeder,
                varselType = MotedeltakerVarselType.AVLYST,
                fritekstArbeidstaker = avlysDialogmote.arbeidstaker.begrunnelse,
                fritekstArbeidsgiver = avlysDialogmote.arbeidsgiver.begrunnelse,
                documentArbeidstaker = avlysDialogmote.arbeidstaker.avlysning,
                documentArbeidsgiver = avlysDialogmote.arbeidsgiver.avlysning,
                documentBehandler = avlysDialogmote.behandler?.avlysning ?: emptyList(),
                moteTidspunkt = dialogmote.tidStedList.latest()!!.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer,
                token = token,
                callId = callId,
            )
            connection.commit()
        }
    }

    suspend fun nyttMoteinnkallingTidSted(
        callId: String,
        dialogmote: Dialogmote,
        endreDialogmoteTidSted: EndreTidStedDialogmoteDTO,
        token: String,
    ) {
        val virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer

        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw ConflictException("Failed to change tid/sted, already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw ConflictException("Failed to change tid/sted, already Avlyst")
        }
        if (dialogmote.behandler != null && endreDialogmoteTidSted.behandler == null) {
            throw IllegalArgumentException("Failed to change tid/sted: missing behandler")
        }

        val pdfEndringArbeidstaker = pdfGenClient.pdfEndringTidSted(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidstaker.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidstaker")

        val pdfEndringArbeidsgiver = pdfGenClient.pdfEndringTidSted(
            callId = callId,
            documentComponentDTOList = endreDialogmoteTidSted.arbeidsgiver.endringsdokument
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidsgiver")

        val pdfEndringBehandler = endreDialogmoteTidSted.behandler?.let {
            pdfGenClient.pdfEndringTidSted(
                callId = callId,
                documentComponentDTOList = it.endringsdokument
            ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Behandler")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val arbeidstakernavn = pdlClient.navn(dialogmote.arbeidstaker.personIdent)

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = dialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        database.connection.use { connection ->
            connection.updateMoteTidSted(
                commit = false,
                moteId = dialogmote.id,
                newDialogmoteTidSted = endreDialogmoteTidSted,
            )
            dialogmotestatusService.updateMoteStatus(
                callId = callId,
                connection = connection,
                dialogmote = dialogmote,
                newDialogmoteStatus = DialogmoteStatus.NYTT_TID_STED,
                opprettetAv = getNAVIdentFromToken(token),
                token = token,
            )
            dialogmotedeltakerService.slettBrukeroppgaverPaMote(
                dialogmote = dialogmote
            )
            createAndSendVarsel(
                connection = connection,
                arbeidstakerId = dialogmote.arbeidstaker.id,
                arbeidsgiverId = dialogmote.arbeidsgiver.id,
                behandlerId = dialogmote.behandler?.id,
                behandlerRef = dialogmote.behandler?.behandlerRef,
                behandlerParentVarselId = dialogmote.behandler?.findParentVarselId(),
                behandlerInnkallingUuid = dialogmote.behandler?.findInnkallingVarselUuid(),
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                arbeidstakernavn = arbeidstakernavn,
                pdfArbeidstaker = pdfEndringArbeidstaker,
                pdfArbeidsgiver = pdfEndringArbeidsgiver,
                pdfBehandler = pdfEndringBehandler,
                narmesteLeder = narmesteLeder,
                varselType = MotedeltakerVarselType.NYTT_TID_STED,
                fritekstArbeidstaker = endreDialogmoteTidSted.arbeidstaker.begrunnelse,
                fritekstArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.begrunnelse,
                documentArbeidstaker = endreDialogmoteTidSted.arbeidstaker.endringsdokument,
                documentArbeidsgiver = endreDialogmoteTidSted.arbeidsgiver.endringsdokument,
                documentBehandler = endreDialogmoteTidSted.behandler?.endringsdokument ?: emptyList(),
                moteTidspunkt = endreDialogmoteTidSted.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer,
                token = token,
                callId = callId,
            )

            connection.commit()
        }
    }

    private suspend fun createAndSendVarsel(
        connection: Connection,
        arbeidstakerId: Int,
        arbeidsgiverId: Int,
        behandlerId: Int?,
        behandlerRef: String?,
        behandlerParentVarselId: String?,
        behandlerInnkallingUuid: UUID?,
        arbeidstakerPersonIdent: PersonIdent,
        arbeidstakernavn: String,
        pdfArbeidstaker: ByteArray,
        pdfArbeidsgiver: ByteArray,
        pdfBehandler: ByteArray?,
        narmesteLeder: NarmesteLederRelasjonDTO?,
        varselType: MotedeltakerVarselType,
        fritekstArbeidstaker: String = "",
        fritekstArbeidsgiver: String = "",
        fritekstBehandler: String = "",
        documentArbeidstaker: List<DocumentComponentDTO> = emptyList(),
        documentArbeidsgiver: List<DocumentComponentDTO> = emptyList(),
        documentBehandler: List<DocumentComponentDTO> = emptyList(),
        moteTidspunkt: LocalDateTime,
        digitalArbeidstakerVarsling: Boolean,
        virksomhetsnummer: Virksomhetsnummer,
        token: String,
        callId: String,
    ) {
        val (pdfArbeidstakerId, _) = connection.createPdf(
            commit = false,
            pdf = pdfArbeidstaker,
        )
        val (pdfArbeidsgiverId, _) = connection.createPdf(
            commit = false,
            pdf = pdfArbeidsgiver,
        )
        val (_, varselArbeidstakerId) = connection.createMotedeltakerVarselArbeidstaker(
            commit = false,
            motedeltakerArbeidstakerId = arbeidstakerId,
            status = "OK",
            varselType = varselType,
            digitalt = digitalArbeidstakerVarsling,
            pdfId = pdfArbeidstakerId,
            fritekst = fritekstArbeidstaker,
            document = documentArbeidstaker,
        )
        val (_, virksomhetsbrevId) = connection.createMotedeltakerVarselArbeidsgiver(
            commit = false,
            motedeltakerArbeidsgiverId = arbeidsgiverId,
            status = "OK",
            varselType = varselType,
            pdfId = pdfArbeidsgiverId,
            fritekst = fritekstArbeidsgiver,
            sendAltinn = narmesteLeder == null,
            document = documentArbeidsgiver,
        )
        val behandlerVarselIdPair = behandlerId?.let {
            val (pdfBehandlerId, _) = connection.createPdf(
                commit = false,
                pdf = pdfBehandler!!,
            )
            connection.createMotedeltakerVarselBehandler(
                commit = false,
                motedeltakerBehandlerId = it,
                status = "OK",
                varselType = varselType,
                pdfId = pdfBehandlerId,
                fritekst = fritekstBehandler,
                document = documentBehandler,
            )
        }

        val now = LocalDateTime.now()
        val isDialogmoteTidPassed = moteTidspunkt.isBefore(now)

        if (!isDialogmoteTidPassed) {
            varselService.sendVarsel(
                varselType = varselType,
                isDigitalVarselEnabledForArbeidstaker = digitalArbeidstakerVarsling,
                arbeidstakerPersonIdent = arbeidstakerPersonIdent,
                arbeidstakernavn = arbeidstakernavn,
                arbeidstakerbrevId = varselArbeidstakerId,
                narmesteLeder = narmesteLeder,
                virksomhetsbrevId = virksomhetsbrevId,
                virksomhetsPdf = pdfArbeidsgiver,
                virksomhetsnummer = virksomhetsnummer,
                behandlerId = behandlerId,
                behandlerRef = behandlerRef,
                behandlerDocument = documentBehandler,
                behandlerPdf = pdfBehandler,
                behandlerbrevId = behandlerVarselIdPair?.second,
                behandlerbrevParentId = behandlerParentVarselId,
                behandlerInnkallingUuid = behandlerInnkallingUuid,
                token = token,
                callId = callId,
            )
        }
    }

    fun overtaMoter(veilederIdent: String, dialogmoter: List<Dialogmote>) {
        database.connection.use { connection ->
            dialogmoter.forEach { dialogmote ->
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = veilederIdent
                )
            }
            connection.commit()
        }
    }

    fun mellomlagreReferat(
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
    ) {
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw ConflictException("Failed to mellomlagre referat Dialogmote, already Avlyst")
        }

        database.connection.use { connection ->

            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            val newReferat = referat.toNewReferat(
                moteId = dialogmote.id,
                ferdigstilt = false,
            )
            val existingReferat = dialogmote.referatList.firstOrNull()
            if (existingReferat == null || existingReferat.ferdigstilt) {
                connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = null,
                    digitalt = true,
                    sendAltinn = false,
                )
            } else {
                connection.updateReferat(
                    commit = false,
                    referat = existingReferat,
                    newReferat = newReferat,
                    pdfId = null,
                    digitalt = true,
                    sendAltinn = false,
                )
            }

            if (dialogmote.behandler != null) {
                connection.updateMotedeltakerBehandler(
                    deltakerId = dialogmote.behandler.id,
                    deltatt = referat.behandlerDeltatt ?: true,
                    mottarReferat = referat.behandlerMottarReferat ?: true,
                )
            }
            connection.commit()
        }
    }

    suspend fun ferdigstillMote(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
        token: String,
    ) {
        if (dialogmote.status == DialogmoteStatus.FERDIGSTILT) {
            throw ConflictException("Failed to Ferdigstille Dialogmote, already Ferdigstilt")
        }
        if (dialogmote.status == DialogmoteStatus.AVLYST) {
            throw ConflictException("Failed to Ferdigstille Dialogmote, already Avlyst")
        }

        val virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val arbeidstakernavn = pdlClient.navn(dialogmote.arbeidstaker.personIdent)

        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            documentComponentDTOList = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = dialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        database.connection.use { connection ->

            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            dialogmotestatusService.updateMoteStatus(
                callId = callId,
                connection = connection,
                dialogmote = dialogmote,
                newDialogmoteStatus = DialogmoteStatus.FERDIGSTILT,
                opprettetAv = opprettetAv,
                token = token,
            )
            val (pdfId, _) = connection.createPdf(
                commit = false,
                pdf = pdfReferat,
            )
            val newReferat = referat.toNewReferat(
                moteId = dialogmote.id,
                ferdigstilt = true,
            )
            val (_, referatUuid) = if (dialogmote.referatList.isEmpty()) {
                connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            } else {
                val existingReferat = dialogmote.referatList.first()
                if (existingReferat.ferdigstilt) {
                    throw ConflictException("Failed to Ferdigstille referat for Dialogmote, referat already Ferdigstilt")
                }
                connection.updateReferat(
                    commit = false,
                    referat = existingReferat,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            }
            val behandler = dialogmote.behandler
            if (behandler != null) {
                connection.updateMotedeltakerBehandler(
                    deltakerId = behandler.id,
                    deltatt = referat.behandlerDeltatt ?: true,
                    mottarReferat = referat.behandlerMottarReferat ?: true,
                )
            }

            varselService.sendVarsel(
                varselType = MotedeltakerVarselType.REFERAT,
                isDigitalVarselEnabledForArbeidstaker = digitalVarsling,
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                arbeidstakernavn = arbeidstakernavn,
                arbeidstakerbrevId = referatUuid,
                narmesteLeder = narmesteLeder,
                virksomhetsbrevId = referatUuid,
                virksomhetsPdf = pdfReferat,
                virksomhetsnummer = virksomhetsnummer,
                skalVarsleBehandler = referat.behandlerMottarReferat ?: true,
                behandlerId = behandler?.id,
                behandlerRef = behandler?.behandlerRef,
                behandlerDocument = referat.document,
                behandlerPdf = pdfReferat,
                behandlerbrevId = referatUuid,
                behandlerbrevParentId = behandler?.findParentVarselId(),
                behandlerInnkallingUuid = behandler?.findInnkallingVarselUuid(),
                token = token,
                callId = callId,
            )

            connection.commit()
        }
    }

    suspend fun endreFerdigstiltReferat(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
        token: String,
    ) {
        if (dialogmote.status != DialogmoteStatus.FERDIGSTILT) {
            throw ConflictException("Failed to Endre Ferdigstilt Dialogmote, not Ferdigstilt")
        }

        val virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = dialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val arbeidstakernavn = pdlClient.navn(dialogmote.arbeidstaker.personIdent)

        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            documentComponentDTOList = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = dialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        val newReferat = referat.toNewReferat(
            moteId = dialogmote.id,
            ferdigstilt = true,
        )
        val existingReferat = dialogmote.referatList.firstOrNull()
            ?: throw RuntimeException("Ferdigstilt mote ${dialogmote.id} does not have referat")

        database.connection.use { connection ->

            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            val (pdfId, _) = connection.createPdf(
                commit = false,
                pdf = pdfReferat,
            )

            val (_, referatUuid) = if (existingReferat.ferdigstilt) {
                connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            } else {
                connection.updateReferat(
                    commit = false,
                    referat = existingReferat,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            }
            val behandler = dialogmote.behandler
            if (behandler != null) {
                connection.updateMotedeltakerBehandler(
                    deltakerId = behandler.id,
                    deltatt = referat.behandlerDeltatt ?: true,
                    mottarReferat = referat.behandlerMottarReferat ?: true,
                )
            }

            varselService.sendVarsel(
                varselType = MotedeltakerVarselType.REFERAT,
                isDigitalVarselEnabledForArbeidstaker = digitalVarsling,
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                arbeidstakernavn = arbeidstakernavn,
                arbeidstakerbrevId = referatUuid,
                narmesteLeder = narmesteLeder,
                virksomhetsbrevId = referatUuid,
                virksomhetsPdf = pdfReferat,
                virksomhetsnummer = virksomhetsnummer,
                skalVarsleBehandler = referat.behandlerMottarReferat ?: true,
                behandlerId = behandler?.id,
                behandlerRef = behandler?.behandlerRef,
                behandlerDocument = referat.document,
                behandlerPdf = pdfReferat,
                behandlerbrevId = referatUuid,
                behandlerbrevParentId = behandler?.findParentVarselId(),
                behandlerInnkallingUuid = behandler?.findInnkallingVarselUuid(),
                token = token,
                callId = callId,
            )

            connection.commit()
        }
    }

    private fun getFerdigReferat(
        referatUUID: UUID
    ): Referat? {
        val referat = getReferat(referatUUID)
        return if (referat?.ferdigstilt == true) referat else null
    }

    private fun getReferat(
        referatUUID: UUID
    ): Referat? {
        return database.getReferat(referatUUID).firstOrNull()?.let { pReferat ->
            val andreDeltakere = dialogmoterelasjonService.getAndreDeltakere(pReferat.id)
            val motedeltakerArbeidstakerId = database.getMoteDeltakerArbeidstaker(pReferat.moteId).id
            val motedeltakerArbeidsgiverId = database.getMoteDeltakerArbeidsgiver(pReferat.moteId).id
            pReferat.toReferat(
                andreDeltakere = andreDeltakere,
                motedeltakerArbeidstakerId = motedeltakerArbeidstakerId,
                motedeltakerArbeidsgiverId = motedeltakerArbeidsgiverId
            )
        }
    }

    fun getArbeidstakerBrevFromUuid(
        brevUuid: UUID
    ): ArbeidstakerBrev {
        val motedeltakerArbeidstakerVarsel = dialogmotedeltakerService.getDialogmoteDeltakerArbeidstakerVarselList(
            uuid = brevUuid
        ).firstOrNull()

        val ferdigreferat = getFerdigReferat(
            referatUUID = brevUuid
        )
        val noBrevFound = (motedeltakerArbeidstakerVarsel == null && ferdigreferat == null)
        if (noBrevFound) {
            throw IllegalArgumentException("No Brev found for arbeidstaker with uuid=$brevUuid")
        }
        return motedeltakerArbeidstakerVarsel ?: ferdigreferat!!
    }

    fun getNarmesteLederBrevFromUuid(brevUuid: UUID): NarmesteLederBrev {
        val moteDeltagerArbeidsgiverVarsel =
            dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiverVarselList(uuid = brevUuid).firstOrNull()

        val ferdigReferat = getFerdigReferat(
            referatUUID = brevUuid
        )
        val noBrevFound = (moteDeltagerArbeidsgiverVarsel == null && ferdigReferat == null)
        if (noBrevFound) {
            throw IllegalArgumentException("No Brev found for arbeidsgiver with uuid=$brevUuid")
        }

        return moteDeltagerArbeidsgiverVarsel ?: ferdigReferat!!
    }

    private suspend fun isDigitalVarselEnabled(
        personIdent: PersonIdent,
        token: String,
        callId: String,
    ): Boolean {
        return kontaktinformasjonClient.isDigitalVarselEnabled(
            personIdent = personIdent,
            token = token,
            callId = callId,
        )
    }
}
