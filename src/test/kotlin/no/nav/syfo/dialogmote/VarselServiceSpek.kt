package no.nav.syfo.dialogmote

import io.mockk.*
import no.nav.syfo.brev.arbeidstaker.ArbeidstakerVarselService
import no.nav.syfo.brev.behandler.BehandlerVarselService
import no.nav.syfo.brev.narmesteleder.NarmesteLederVarselService
import no.nav.syfo.client.altinn.AltinnClient
import no.nav.syfo.client.altinn.createAltinnMelding
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_NO_NARMESTELEDER
import no.nav.syfo.testhelper.mock.narmesteLeder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.util.*

object VarselServiceSpek : Spek({

    describe(VarselServiceSpek::class.java.simpleName) {

        val arbeidstakerVarselService = mockk<ArbeidstakerVarselService>()
        val narmesteLederVarselService = mockk<NarmesteLederVarselService>()
        val behandlerVarselService = mockk<BehandlerVarselService>()
        val altinnClient = mockk<AltinnClient>()

        val varselService = VarselService(
            arbeidstakerVarselService = arbeidstakerVarselService,
            narmesteLederVarselService = narmesteLederVarselService,
            behandlerVarselService = behandlerVarselService,
            altinnClient = altinnClient,
        )

        beforeEachTest {
            clearMocks(arbeidstakerVarselService)
            clearMocks(narmesteLederVarselService)
            clearMocks(behandlerVarselService)
            clearMocks(altinnClient)

            justRun { arbeidstakerVarselService.sendVarsel(any(), any(), any(), any(), any()) }
            justRun { narmesteLederVarselService.sendVarsel(any(), any()) }
            justRun { behandlerVarselService.sendVarsel(any(), any(), any(), any(), any(), any(), any(), any()) }
            justRun { altinnClient.sendToVirksomhet(any()) }
        }

        it("Send varsel to nærmeste leder") {
            val virksomhetsbrevId = UUID.randomUUID()
            val virksomhetsPdf = byteArrayOf(0x2E, 0x38)
            val tidspunktForVarsel = LocalDateTime.now()
            val moteTidspunkt = LocalDateTime.now()

            varselService.sendVarsel(
                tidspunktForVarsel = tidspunktForVarsel,
                varselType = MotedeltakerVarselType.INNKALT,
                isDigitalVarselEnabledForArbeidstaker = false,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                arbeidstakerId = UUID.randomUUID(),
                arbeidstakerbrevId = UUID.randomUUID(),
                narmesteLeder = narmesteLeder,
                virksomhetsbrevId = virksomhetsbrevId,
                virksomhetsPdf = virksomhetsPdf,
                virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
                behandlerId = null,
                behandlerRef = null,
                behandlerDocument = emptyList(),
                behandlerPdf = byteArrayOf(0x2E, 0x38),
                behandlerbrevId = null,
                behandlerbrevParentId = null,
                behandlerInnkallingUuid = null
            )

            val altinnMelding = createAltinnMelding(
                virksomhetsbrevId,
                VIRKSOMHETSNUMMER_HAS_NARMESTELEDER,
                virksomhetsPdf,
                MotedeltakerVarselType.INNKALT,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                true
            )

            verify(exactly = 1) { altinnClient.sendToVirksomhet(altinnMelding) }

            verify(exactly = 1) {
                narmesteLederVarselService.sendVarsel(
                    narmesteLeder, MotedeltakerVarselType.INNKALT
                )
            }
        }

        it("Send brev to Altinn when no nærmeste leder") {
            val virksomhetsbrevId = UUID.randomUUID()
            val virksomhetsPdf = byteArrayOf(0x2E, 0x38)

            varselService.sendVarsel(
                tidspunktForVarsel = LocalDateTime.now(),
                varselType = MotedeltakerVarselType.INNKALT,
                isDigitalVarselEnabledForArbeidstaker = false,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                arbeidstakerId = UUID.randomUUID(),
                arbeidstakerbrevId = UUID.randomUUID(),
                narmesteLeder = null,
                virksomhetsbrevId = virksomhetsbrevId,
                virksomhetsPdf = virksomhetsPdf,
                virksomhetsnummer = VIRKSOMHETSNUMMER_NO_NARMESTELEDER,
                behandlerId = null,
                behandlerRef = null,
                behandlerDocument = emptyList(),
                behandlerPdf = byteArrayOf(0x2E, 0x38),
                behandlerbrevId = null,
                behandlerbrevParentId = null,
                behandlerInnkallingUuid = null
            )

            val altinnMelding = createAltinnMelding(
                virksomhetsbrevId,
                VIRKSOMHETSNUMMER_NO_NARMESTELEDER,
                virksomhetsPdf,
                MotedeltakerVarselType.INNKALT,
                arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                arbeidstakernavn = UserConstants.ARBEIDSTAKERNAVN,
                false
            )

            verify(exactly = 1) {
                altinnClient.sendToVirksomhet(
                    altinnMelding
                )
            }

            verify(exactly = 0) { narmesteLederVarselService.sendVarsel(any(), any()) }
        }
    }
})
