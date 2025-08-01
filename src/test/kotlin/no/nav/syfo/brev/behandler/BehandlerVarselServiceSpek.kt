package no.nav.syfo.brev.behandler

import io.mockk.*
import no.nav.syfo.application.BehandlerVarselService
import no.nav.syfo.infrastructure.database.Database
import no.nav.syfo.infrastructure.kafka.behandler.BehandlerDialogmeldingProducer
import no.nav.syfo.infrastructure.database.dialogmote.database.createMotedeltakerBehandlerVarselSvar
import no.nav.syfo.infrastructure.database.dialogmote.database.getMote
import no.nav.syfo.infrastructure.database.dialogmote.database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid
import no.nav.syfo.domain.dialogmote.DialogmoteStatus
import no.nav.syfo.domain.dialogmote.DialogmoteSvarType
import no.nav.syfo.domain.dialogmote.MotedeltakerVarselType
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateDialogmoteSvar
import no.nav.syfo.testhelper.generator.generatePDialogmote
import no.nav.syfo.testhelper.generator.generatePMotedeltakerBehandlerVarsel
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.util.*

class BehandlerVarselServiceSpek : Spek({
    val database: Database = mockk()
    val behandlerDialogmeldingProducer = mockk<BehandlerDialogmeldingProducer>()
    val behandlerVarselService = BehandlerVarselService(
        database = database,
        behandlerDialogmeldingProducer = behandlerDialogmeldingProducer,
    )

    beforeEachTest {
        mockkStatic("no.nav.syfo.infrastructure.database.dialogmote.database.MoteQueryKt")
        mockkStatic("no.nav.syfo.infrastructure.database.dialogmote.database.MotedeltakerBehandlerVarselQueryKt")
        mockkStatic("no.nav.syfo.infrastructure.database.dialogmote.database.MotedeltakerBehandlerVarselSvarQueryKt")
    }

    afterEachTest {
        clearMocks(database)
        clearMocks(behandlerDialogmeldingProducer)
        unmockkStatic("no.nav.syfo.infrastructure.database.dialogmote.database.MoteQueryKt")
        unmockkStatic("no.nav.syfo.infrastructure.database.dialogmote.database.MotedeltakerBehandlerVarselQueryKt")
        unmockkStatic("no.nav.syfo.infrastructure.database.dialogmote.database.MotedeltakerBehandlerVarselSvarQueryKt")
    }

    describe("BehandlerVarselService handles sending of varsler to behandler and storing svar") {
        describe("find related varsel and store behandlersvar in database") {
            it("return false and don't store anything if no associated meeting is found") {
                val varseltypeInnkalt = MotedeltakerVarselType.INNKALT
                val conversationRef = "123456789"
                val pMotedeltakerBehandlerVarsel = generatePMotedeltakerBehandlerVarsel()
                val dialogmeldingSvar = generateDialogmoteSvar()

                every {
                    database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                        any(),
                        any(),
                        any()
                    )
                } returns Pair(1, pMotedeltakerBehandlerVarsel)
                every { database.getMote(any()) } returns null

                val isSvarSaved = behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
                    dialogmeldingSvar = dialogmeldingSvar,
                    msgId = "321",
                )

                isSvarSaved shouldBeEqualTo false
                verify(exactly = 1) {
                    database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                        varselType = varseltypeInnkalt,
                        arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                        uuid = conversationRef
                    )
                }
                verify(exactly = 1) { database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel) }
                verify(exactly = 0) {
                    database.createMotedeltakerBehandlerVarselSvar(
                        any(),
                        any(),
                        any(),
                        any()
                    )
                }
            }

            it("persist motesvar as valid when mote is unfinished") {
                val varseltypeInnkalt = MotedeltakerVarselType.INNKALT
                val conversationRef = "123456789"
                val pMotedeltakerBehandlerVarsel = generatePMotedeltakerBehandlerVarsel()
                val dialogmeldingSvar = generateDialogmoteSvar()
                val pDialogmote = generatePDialogmote()
                every {
                    database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                        any(),
                        any(),
                        any()
                    )
                } returns Pair(1, pMotedeltakerBehandlerVarsel)
                every { database.getMote(any()) } returns pDialogmote
                every { database.createMotedeltakerBehandlerVarselSvar(any(), any(), any(), any()) } returns Pair(1, UUID.randomUUID())

                val isSvarSaved = behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
                    dialogmeldingSvar = dialogmeldingSvar,
                    msgId = "321",
                )

                isSvarSaved shouldBeEqualTo true
                verify(exactly = 1) {
                    database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                        varselType = varseltypeInnkalt,
                        arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                        uuid = conversationRef
                    )
                }
                verify(exactly = 1) { database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel) }
                verify(exactly = 1) {
                    database.createMotedeltakerBehandlerVarselSvar(
                        motedeltakerBehandlerVarselId = 1,
                        type = DialogmoteSvarType.KOMMER_IKKE,
                        tekst = "tekst",
                        msgId = "321"
                    )
                }
            }

            it("persist motesvar as valid when it was sent before mote was finished") {
                val varseltypeInnkalt = MotedeltakerVarselType.INNKALT
                val conversationRef = "123456789"
                val twoDaysAgo = LocalDateTime.now().minusDays(2)
                val pMotedeltakerBehandlerVarsel = generatePMotedeltakerBehandlerVarsel()
                val dialogmeldingSvar = generateDialogmoteSvar().copy(
                    opprettetTidspunkt = twoDaysAgo,
                )
                val pDialogmote = generatePDialogmote().copy(
                    status = DialogmoteStatus.FERDIGSTILT.name,
                    updatedAt = LocalDateTime.now()
                )
                every {
                    database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                        any(),
                        any(),
                        any()
                    )
                } returns Pair(1, pMotedeltakerBehandlerVarsel)
                every { database.getMote(any()) } returns pDialogmote
                every { database.createMotedeltakerBehandlerVarselSvar(any(), any(), any(), any()) } returns Pair(1, UUID.randomUUID())

                val isSvarSaved = behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
                    dialogmeldingSvar = dialogmeldingSvar,
                    msgId = "321",
                )

                isSvarSaved shouldBeEqualTo true
                verify(exactly = 1) {
                    database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                        varselType = varseltypeInnkalt,
                        arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                        uuid = conversationRef
                    )
                }
                verify(exactly = 1) { database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel) }
                verify(exactly = 1) {
                    database.createMotedeltakerBehandlerVarselSvar(
                        motedeltakerBehandlerVarselId = 1,
                        type = DialogmoteSvarType.KOMMER_IKKE,
                        tekst = "tekst",
                        msgId = "321"
                    )
                }
            }

            it("persist motesvar as invalid when it was sent after mote was finished") {
                val varseltypeInnkalt = MotedeltakerVarselType.INNKALT
                val conversationRef = "123456789"
                val twoDaysAgo = LocalDateTime.now().minusDays(2)
                val pMotedeltakerBehandlerVarsel = generatePMotedeltakerBehandlerVarsel()
                val dialogmeldingSvar = generateDialogmoteSvar().copy(
                    opprettetTidspunkt = LocalDateTime.now(),
                )
                val pDialogmote = generatePDialogmote().copy(
                    status = DialogmoteStatus.FERDIGSTILT.name,
                    updatedAt = twoDaysAgo,
                )
                every {
                    database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                        any(),
                        any(),
                        any()
                    )
                } returns Pair(1, pMotedeltakerBehandlerVarsel)
                every { database.getMote(any()) } returns pDialogmote
                every { database.createMotedeltakerBehandlerVarselSvar(any(), any(), any(), any()) } returns Pair(1, UUID.randomUUID())

                val isSvarSaved = behandlerVarselService.finnBehandlerVarselOgOpprettSvar(
                    dialogmeldingSvar = dialogmeldingSvar,
                    msgId = "321",
                )

                isSvarSaved shouldBeEqualTo true
                verify(exactly = 1) {
                    database.getMotedeltakerBehandlerVarselOfTypeForArbeidstakerAndUuid(
                        varselType = varseltypeInnkalt,
                        arbeidstakerPersonIdent = UserConstants.ARBEIDSTAKER_FNR,
                        uuid = conversationRef
                    )
                }
                verify(exactly = 1) { database.getMote(behandlerVarsel = pMotedeltakerBehandlerVarsel) }
                verify(exactly = 1) {
                    database.createMotedeltakerBehandlerVarselSvar(
                        motedeltakerBehandlerVarselId = 1,
                        type = DialogmoteSvarType.KOMMER_IKKE,
                        tekst = "tekst",
                        msgId = "321"
                    )
                }
            }
        }
    }
})
