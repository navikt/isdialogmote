package no.nav.syfo.application

import no.nav.syfo.api.authentication.getNAVIdentFromToken
import no.nav.syfo.api.dto.AvlysningTilMottakereDTO
import no.nav.syfo.api.dto.EndretTidStedDTO
import no.nav.syfo.api.dto.NewDialogmoteDTO
import no.nav.syfo.api.dto.NewReferatDTO
import no.nav.syfo.api.dto.toNewDialogmote
import no.nav.syfo.api.dto.toNewReferat
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.domain.EnhetNr
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.dialogmote.ArbeidstakerBrev
import no.nav.syfo.domain.dialogmote.Dialogmote
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.domain.dialogmote.NarmesteLederBrev
import no.nav.syfo.domain.dialogmote.Referat
import no.nav.syfo.domain.dialogmote.anyActive
import no.nav.syfo.domain.dialogmote.findInnkallingVarselUuid
import no.nav.syfo.domain.dialogmote.findParentVarselId
import no.nav.syfo.domain.dialogmote.latest
import no.nav.syfo.infrastructure.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.infrastructure.client.behandlendeenhet.getEnhetId
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.client.person.kontaktinfo.KontaktinformasjonClient
import no.nav.syfo.infrastructure.database.createMotedeltakerVarselArbeidsgiver
import no.nav.syfo.infrastructure.database.createMotedeltakerVarselArbeidstaker
import no.nav.syfo.infrastructure.database.createMotedeltakerVarselBehandler
import no.nav.syfo.infrastructure.database.createNewDialogmoteWithReferences
import no.nav.syfo.infrastructure.database.createNewReferat
import no.nav.syfo.infrastructure.database.updateMoteTidSted
import no.nav.syfo.infrastructure.database.updateMoteTildeltVeileder
import no.nav.syfo.infrastructure.database.updateMotedeltakerBehandler
import no.nav.syfo.infrastructure.database.updateReferat
import java.sql.Connection
import java.time.LocalDateTime
import java.util.*

class DialogmoteService(
    private val transactionManager: ITransactionManager,
    private val moteRepository: IMoteRepository,
    private val dialogmotedeltakerService: DialogmotedeltakerService,
    private val dialogmotestatusService: DialogmotestatusService,
    private val dialogmoterelasjonService: DialogmoterelasjonService,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val narmesteLederClient: NarmesteLederClient,
    private val pdfGenClient: PdfGenClient,
    private val kontaktinformasjonClient: KontaktinformasjonClient,
    private val varselService: VarselService,
    private val pdlClient: PdlClient,
    private val pdfRepository: IPdfRepository,
) {
    fun getDialogmote(moteUUID: UUID): Dialogmote =
        moteRepository.getMote(moteUUID)

    fun getDialogmoteList(personident: PersonIdent): List<Dialogmote> =
        moteRepository.getMoterFor(personident)

    fun getDialogmoteList(enhetNr: EnhetNr): List<Dialogmote> =
        moteRepository.getDialogmoteList(enhetNr)

    fun getDialogmoteUnfinishedList(enhetNr: EnhetNr): List<Dialogmote> =
        moteRepository.getUnfinishedMoterForEnhet(enhetNr)

    fun getDialogmoteUnfinishedListForVeilederIdent(veilederIdent: String): List<Dialogmote> =
        moteRepository.getUnfinishedMoterForVeileder(veilederIdent)

    suspend fun createMoteinnkalling(
        newDialogmoteDTO: NewDialogmoteDTO,
        callId: String,
        token: String,
    ) {
        val personIdent = PersonIdent(newDialogmoteDTO.arbeidstaker.personIdent)
        val virksomhetsnummer = Virksomhetsnummer(newDialogmoteDTO.arbeidsgiver.virksomhetsnummer)

        val isAnyUnfinishedDialogmoter = getDialogmoteList(personIdent).filter {
            it.arbeidsgiver.virksomhetsnummer == virksomhetsnummer
        }.anyActive()

        if (isAnyUnfinishedDialogmoter) {
            throw ConflictException("Denied access to create Dialogmote: unfinished Dialogmote exists for PersonIdent")
        }

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val arbeidstakernavn = pdlClient.navn(personIdent)

        val behandlendeEnhetDTO = behandlendeEnhetClient.getEnhet(
            callId = callId,
            personIdent = personIdent,
            token = token,
        ) ?: throw RuntimeException("Failed to request (or missing) BehandlendeEnhet of Person")

        val newDialogmote = newDialogmoteDTO.toNewDialogmote(
            requestByNAVIdent = getNAVIdentFromToken(token),
            navEnhet = EnhetNr(behandlendeEnhetDTO.getEnhetId()),
        )

        val pdfInnkallingArbeidstaker = pdfGenClient.pdfInnkalling(
            callId = callId,
            mottakerNavn = arbeidstakernavn,
            mottakerFodselsnummer = personIdent.value,
            pdfContent = newDialogmoteDTO.arbeidstaker.innkalling,
        ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidstaker")

        val pdfInnkallingArbeidsgiver = pdfGenClient.pdfInnkalling(
            callId = callId,
            mottakerNavn = narmesteLeder?.narmesteLederNavn ?: narmesteLeder?.virksomhetsnavn,
            pdfContent = newDialogmoteDTO.arbeidsgiver.innkalling,
        ) ?: throw RuntimeException("Failed to request PDF - Innkalling Arbeidsgiver")

        val pdfInnkallingBehandler = newDialogmoteDTO.behandler?.let {
            pdfGenClient.pdfInnkalling(
                callId = callId,
                mottakerNavn = newDialogmoteDTO.behandler.behandlerNavn,
                pdfContent = it.innkalling,
            ) ?: throw RuntimeException("Failed to request PDF - Innkalling Behandler")
        }

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = personIdent,
            token = token,
            callId = callId,
        )

        transactionManager.run { transaction ->
            val createdDialogmoteIdentifiers = transaction.connection.createNewDialogmoteWithReferences(
                commit = false,
                newDialogmote = newDialogmote,
            )
            dialogmotestatusService.createMoteStatusEndring(
                callId = callId,
                connection = transaction.connection,
                newDialogmote = newDialogmote,
                dialogmoteId = createdDialogmoteIdentifiers.dialogmoteIdPair.first,
                dialogmoteStatus = newDialogmote.status,
                opprettetAv = newDialogmote.opprettetAv,
                token = token,
            )
            createAndSendVarsel(
                connection = transaction.connection,
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
                motetidspunkt = newDialogmoteDTO.tidSted.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer,
                token = token,
                callId = callId,
            )
        }
    }

    suspend fun avlysMoteinnkalling(
        callId: String,
        dialogmote: Dialogmote,
        avlysningTilMottakere: AvlysningTilMottakereDTO,
        token: String,
    ) {
        val avlystDialogmote = dialogmote.avlysInnkalling()
        if (avlystDialogmote.behandler != null && avlysningTilMottakere.behandler == null) {
            throw IllegalArgumentException("Failed to Avlys Dialogmote: missing behandler")
        }

        val arbeidstakernavn = pdlClient.navn(avlystDialogmote.arbeidstaker.personIdent)
        val virksomhetsnummer = avlystDialogmote.arbeidsgiver.virksomhetsnummer
        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = avlystDialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val pdfAvlysningArbeidstaker = pdfGenClient.pdfAvlysning(
            callId = callId,
            mottakerNavn = arbeidstakernavn,
            mottakerFodselsnummer = avlystDialogmote.arbeidstaker.personIdent.value,
            pdfContent = avlysningTilMottakere.arbeidstaker.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidstaker")

        val pdfAvlysningArbeidsgiver = pdfGenClient.pdfAvlysning(
            callId = callId,
            mottakerNavn = narmesteLeder?.narmesteLederNavn ?: narmesteLeder?.virksomhetsnavn,
            pdfContent = avlysningTilMottakere.arbeidsgiver.avlysning,
        ) ?: throw RuntimeException("Failed to request PDF - Avlysning Arbeidsgiver")

        val pdfAvlysningBehandler = avlysningTilMottakere.behandler?.let {
            pdfGenClient.pdfAvlysning(
                callId = callId,
                mottakerNavn = avlystDialogmote.behandler?.behandlerNavn,
                pdfContent = it.avlysning,
            ) ?: throw RuntimeException("Failed to request PDF - Avlysning Behandler")
        }

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = avlystDialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        transactionManager.run { transaction ->
            dialogmotestatusService.updateMoteStatus(
                callId = callId,
                connection = transaction.connection,
                dialogmote = avlystDialogmote,
                newDialogmoteStatus = Dialogmote.Status.AVLYST,
                opprettetAv = getNAVIdentFromToken(token),
                token = token,
            )
            dialogmotedeltakerService.slettBrukeroppgaverPaMote(
                dialogmote = avlystDialogmote
            )
            createAndSendVarsel(
                connection = transaction.connection,
                arbeidstakerId = avlystDialogmote.arbeidstaker.id,
                arbeidstakernavn = arbeidstakernavn,
                arbeidsgiverId = avlystDialogmote.arbeidsgiver.id,
                behandlerId = avlystDialogmote.behandler?.id,
                behandlerRef = avlystDialogmote.behandler?.behandlerRef,
                behandlerParentVarselId = avlystDialogmote.behandler?.findParentVarselId(),
                behandlerInnkallingUuid = avlystDialogmote.behandler?.findInnkallingVarselUuid(),
                arbeidstakerPersonIdent = avlystDialogmote.arbeidstaker.personIdent,
                pdfArbeidstaker = pdfAvlysningArbeidstaker,
                pdfArbeidsgiver = pdfAvlysningArbeidsgiver,
                pdfBehandler = pdfAvlysningBehandler,
                narmesteLeder = narmesteLeder,
                varselType = MotedeltakerVarselType.AVLYST,
                fritekstArbeidstaker = avlysningTilMottakere.arbeidstaker.begrunnelse,
                fritekstArbeidsgiver = avlysningTilMottakere.arbeidsgiver.begrunnelse,
                documentArbeidstaker = avlysningTilMottakere.arbeidstaker.avlysning,
                documentArbeidsgiver = avlysningTilMottakere.arbeidsgiver.avlysning,
                documentBehandler = avlysningTilMottakere.behandler?.avlysning ?: emptyList(),
                motetidspunkt = avlystDialogmote.tidStedList.latest()!!.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer,
                token = token,
                callId = callId,
            )
        }
    }

    suspend fun nyttMoteinnkallingTidSted(
        callId: String,
        dialogmote: Dialogmote,
        endretTidSted: EndretTidStedDTO,
        token: String,
    ) {
        val endretDialogmote = dialogmote.nyttTidSted()
        if (dialogmote.behandler != null && endretTidSted.behandler == null) {
            throw IllegalArgumentException("Failed to change tid/sted: missing behandler")
        }
        val virksomhetsnummer = endretDialogmote.arbeidsgiver.virksomhetsnummer
        val arbeidstakernavn = pdlClient.navn(endretDialogmote.arbeidstaker.personIdent)
        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = endretDialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val pdfEndringArbeidstaker = pdfGenClient.pdfEndringTidSted(
            callId = callId,
            mottakerNavn = arbeidstakernavn,
            mottakerFodselsnummer = endretDialogmote.arbeidstaker.personIdent.value,
            pdfContent = endretTidSted.arbeidstaker.endringsdokument,
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidstaker")

        val pdfEndringArbeidsgiver = pdfGenClient.pdfEndringTidSted(
            callId = callId,
            mottakerNavn = narmesteLeder?.narmesteLederNavn ?: narmesteLeder?.virksomhetsnavn,
            pdfContent = endretTidSted.arbeidsgiver.endringsdokument,
        ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Arbeidsgiver")

        val pdfEndringBehandler = endretTidSted.behandler?.let {
            pdfGenClient.pdfEndringTidSted(
                callId = callId,
                mottakerNavn = endretDialogmote.behandler?.behandlerNavn,
                pdfContent = it.endringsdokument,
            ) ?: throw RuntimeException("Failed to request PDF - EndringTidSted Behandler")
        }

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = endretDialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        transactionManager.run { transaction ->
            transaction.connection.updateMoteTidSted(
                commit = false,
                moteId = endretDialogmote.id,
                newDialogmoteTidSted = endretTidSted,
            )
            dialogmotestatusService.updateMoteStatus(
                callId = callId,
                connection = transaction.connection,
                dialogmote = endretDialogmote,
                newDialogmoteStatus = Dialogmote.Status.NYTT_TID_STED,
                opprettetAv = getNAVIdentFromToken(token),
                token = token,
            )
            dialogmotedeltakerService.slettBrukeroppgaverPaMote(
                dialogmote = endretDialogmote
            )
            createAndSendVarsel(
                connection = transaction.connection,
                arbeidstakerId = endretDialogmote.arbeidstaker.id,
                arbeidsgiverId = endretDialogmote.arbeidsgiver.id,
                behandlerId = endretDialogmote.behandler?.id,
                behandlerRef = endretDialogmote.behandler?.behandlerRef,
                behandlerParentVarselId = endretDialogmote.behandler?.findParentVarselId(),
                behandlerInnkallingUuid = endretDialogmote.behandler?.findInnkallingVarselUuid(),
                arbeidstakerPersonIdent = endretDialogmote.arbeidstaker.personIdent,
                arbeidstakernavn = arbeidstakernavn,
                pdfArbeidstaker = pdfEndringArbeidstaker,
                pdfArbeidsgiver = pdfEndringArbeidsgiver,
                pdfBehandler = pdfEndringBehandler,
                narmesteLeder = narmesteLeder,
                varselType = MotedeltakerVarselType.NYTT_TID_STED,
                fritekstArbeidstaker = endretTidSted.arbeidstaker.begrunnelse,
                fritekstArbeidsgiver = endretTidSted.arbeidsgiver.begrunnelse,
                documentArbeidstaker = endretTidSted.arbeidstaker.endringsdokument,
                documentArbeidsgiver = endretTidSted.arbeidsgiver.endringsdokument,
                documentBehandler = endretTidSted.behandler?.endringsdokument ?: emptyList(),
                motetidspunkt = endretTidSted.tid,
                digitalArbeidstakerVarsling = digitalVarsling,
                virksomhetsnummer = virksomhetsnummer,
                token = token,
                callId = callId,
            )
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
        motetidspunkt: LocalDateTime,
        digitalArbeidstakerVarsling: Boolean,
        virksomhetsnummer: Virksomhetsnummer,
        token: String,
        callId: String,
    ) {
        val (pdfArbeidstakerId, _) = pdfRepository.createPdf(
            connection = connection,
            commit = false,
            pdf = pdfArbeidstaker,
        )
        val (pdfArbeidsgiverId, _) = pdfRepository.createPdf(
            connection = connection,
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
            val (pdfBehandlerId, _) = pdfRepository.createPdf(
                connection = connection,
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
        val isDialogmoteTidPassed = motetidspunkt.isBefore(now)

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
                motetidspunkt = motetidspunkt,
                token = token,
                callId = callId,
            )
        }
    }

    suspend fun tildelMoter(veilederIdent: String, dialogmoter: List<Dialogmote>) {
        transactionManager.run { transaction ->
            dialogmoter.forEach { dialogmote ->
                transaction.connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = veilederIdent
                )
            }
        }
    }

    suspend fun mellomlagreReferat(
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
    ) {
        if (dialogmote.status == Dialogmote.Status.AVLYST) {
            throw ConflictException("Failed to mellomlagre referat Dialogmote, already Avlyst")
        }

        transactionManager.run { transaction ->
            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                transaction.connection.updateMoteTildeltVeileder(
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
                transaction.connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = null,
                    digitalt = true,
                    sendAltinn = false,
                )
            } else {
                transaction.connection.updateReferat(
                    commit = false,
                    referat = existingReferat,
                    newReferat = newReferat,
                    pdfId = null,
                    digitalt = true,
                    sendAltinn = false,
                )
            }

            if (dialogmote.behandler != null) {
                transaction.connection.updateMotedeltakerBehandler(
                    deltakerId = dialogmote.behandler.id,
                    deltatt = referat.behandlerDeltatt ?: true,
                    mottarReferat = referat.behandlerMottarReferat ?: true,
                )
            }
        }
    }

    suspend fun ferdigstillMote(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
        token: String,
    ) {
        val ferdigstiltDialogmote = dialogmote.ferdigstill()

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = ferdigstiltDialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = ferdigstiltDialogmote.arbeidsgiver.virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val arbeidstakernavn = pdlClient.navn(ferdigstiltDialogmote.arbeidstaker.personIdent)

        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            pdfContent = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = ferdigstiltDialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        transactionManager.run { transaction ->
            if (ferdigstiltDialogmote.tildeltVeilederIdent != opprettetAv) {
                transaction.connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = ferdigstiltDialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            dialogmotestatusService.updateMoteStatus(
                callId = callId,
                connection = transaction.connection,
                dialogmote = ferdigstiltDialogmote,
                newDialogmoteStatus = Dialogmote.Status.FERDIGSTILT,
                opprettetAv = opprettetAv,
                token = token,
            )
            val (pdfId, _) = pdfRepository.createPdf(
                connection = transaction.connection,
                commit = false,
                pdf = pdfReferat,
            )
            val newReferat = referat.toNewReferat(
                moteId = ferdigstiltDialogmote.id,
                ferdigstilt = true,
            )
            val (_, referatUuid) = if (ferdigstiltDialogmote.referatList.isEmpty()) {
                transaction.connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            } else {
                val existingReferat = ferdigstiltDialogmote.referatList.first()
                if (existingReferat.ferdigstilt) {
                    throw ConflictException("Failed to Ferdigstille referat for Dialogmote, referat already Ferdigstilt")
                }
                transaction.connection.updateReferat(
                    commit = false,
                    referat = existingReferat,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            }
            ferdigstiltDialogmote.behandler?.let { behandler ->
                transaction.connection.updateMotedeltakerBehandler(
                    deltakerId = behandler.id,
                    deltatt = referat.behandlerDeltatt ?: true,
                    mottarReferat = referat.behandlerMottarReferat ?: true,
                )
            }

            dialogmotedeltakerService.slettBrukeroppgaverPaMote(
                dialogmote = dialogmote
            )

            varselService.sendVarsel(
                varselType = MotedeltakerVarselType.REFERAT,
                isDigitalVarselEnabledForArbeidstaker = digitalVarsling,
                arbeidstakerPersonIdent = dialogmote.arbeidstaker.personIdent,
                arbeidstakernavn = arbeidstakernavn,
                arbeidstakerbrevId = referatUuid,
                narmesteLeder = narmesteLeder,
                virksomhetsbrevId = referatUuid,
                virksomhetsPdf = pdfReferat,
                virksomhetsnummer = ferdigstiltDialogmote.arbeidsgiver.virksomhetsnummer,
                skalVarsleBehandler = referat.behandlerMottarReferat ?: true,
                behandlerId = ferdigstiltDialogmote.behandler?.id,
                behandlerRef = ferdigstiltDialogmote.behandler?.behandlerRef,
                behandlerDocument = referat.document,
                behandlerPdf = pdfReferat,
                behandlerbrevId = referatUuid,
                behandlerbrevParentId = ferdigstiltDialogmote.behandler?.findParentVarselId(),
                behandlerInnkallingUuid = ferdigstiltDialogmote.behandler?.findInnkallingVarselUuid(),
                motetidspunkt = dialogmote.sistMoteTidSted()?.tid,
                token = token,
                callId = callId,
            )
        }
    }

    suspend fun endreFerdigstiltReferat(
        callId: String,
        dialogmote: Dialogmote,
        opprettetAv: String,
        referat: NewReferatDTO,
        token: String,
    ) {
        val endretFerdigstiltReferatDialogmote = dialogmote.endreFerdigstiltReferat()

        val narmesteLeder = narmesteLederClient.activeLeder(
            personIdent = endretFerdigstiltReferatDialogmote.arbeidstaker.personIdent,
            virksomhetsnummer = endretFerdigstiltReferatDialogmote.arbeidsgiver.virksomhetsnummer,
            callId = callId,
            token = token,
        )

        val arbeidstakernavn = pdlClient.navn(endretFerdigstiltReferatDialogmote.arbeidstaker.personIdent)

        val pdfReferat = pdfGenClient.pdfReferat(
            callId = callId,
            pdfContent = referat.document,
        ) ?: throw RuntimeException("Failed to request PDF - Referat")

        val digitalVarsling = isDigitalVarselEnabled(
            personIdent = endretFerdigstiltReferatDialogmote.arbeidstaker.personIdent,
            token = token,
            callId = callId,
        )

        val newReferat = referat.toNewReferat(
            moteId = endretFerdigstiltReferatDialogmote.id,
            ferdigstilt = true,
        )
        val existingReferat = dialogmote.referatList.firstOrNull()
            ?: throw RuntimeException("Ferdigstilt mote ${dialogmote.id} does not have referat")

        transactionManager.run { transaction ->
            if (dialogmote.tildeltVeilederIdent != opprettetAv) {
                transaction.connection.updateMoteTildeltVeileder(
                    commit = false,
                    moteId = dialogmote.id,
                    veilederId = opprettetAv,
                )
            }
            val (pdfId, _) = pdfRepository.createPdf(
                connection = transaction.connection,
                commit = false,
                pdf = pdfReferat,
            )

            val (_, referatUuid) = if (existingReferat.ferdigstilt) {
                transaction.connection.createNewReferat(
                    commit = false,
                    newReferat = newReferat,
                    pdfId = pdfId,
                    digitalt = digitalVarsling,
                    sendAltinn = narmesteLeder == null,
                )
            } else {
                transaction.connection.updateReferat(
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
                transaction.connection.updateMotedeltakerBehandler(
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
                virksomhetsnummer = dialogmote.arbeidsgiver.virksomhetsnummer,
                skalVarsleBehandler = referat.behandlerMottarReferat ?: true,
                behandlerId = behandler?.id,
                behandlerRef = behandler?.behandlerRef,
                behandlerDocument = referat.document,
                behandlerPdf = pdfReferat,
                behandlerbrevId = referatUuid,
                behandlerbrevParentId = behandler?.findParentVarselId(),
                behandlerInnkallingUuid = behandler?.findInnkallingVarselUuid(),
                motetidspunkt = dialogmote.sistMoteTidSted()?.tid,
                token = token,
                callId = callId,
            )
        }
    }

    private fun getFerdigReferat(referatUUID: UUID): Referat? {
        val referat = moteRepository.getReferat(referatUUID)
        return if (referat?.ferdigstilt == true) referat else null
    }

    fun getArbeidstakerBrevFromUuid(
        brevUuid: UUID,
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

    fun publishNarmesteLederSvarVarselHendelse(
        brev: NarmesteLederBrev,
        narmesteLederSvar: DialogmoteSvarType,
        narmesteLederPersonIdent: PersonIdent,
    ) {
        val dialogmoteDeltagerArbeidsgiver = dialogmotedeltakerService.getDialogmoteDeltakerArbeidsgiverById(
            motedeltakerArbeidsgiverId = brev.motedeltakerArbeidsgiverId,
        )
        val arbeidstakerPersonIdent = moteRepository.getMotedeltakerArbeidstaker(
            moteId = dialogmoteDeltagerArbeidsgiver.moteId,
        ).personIdent

        varselService.sendNarmesteLederSvarVarselHendelse(
            narmesteLederSvar = narmesteLederSvar,
            narmesteLederPersonIdent = narmesteLederPersonIdent,
            arbeidstakerPersonIdent = arbeidstakerPersonIdent,
            virksomhetsnummer = dialogmoteDeltagerArbeidsgiver.virksomhetsnummer,
        )
    }
}
