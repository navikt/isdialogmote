package no.nav.syfo.application

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.client.IMotebehovClient
import no.nav.syfo.domain.dialogmote.Avvent
import no.nav.syfo.domain.motebehov.Tilbakemelding
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class MotebehovServiceTest {

    private val motebehovClient = mockk<IMotebehovClient>()
    private val avventRepository = mockk<IAvventRepository>()
    private val transactionManager = mockk<ITransactionManager>()

    private val motebehovService = MotebehovService(
        motebehovClient = motebehovClient,
        avventRepository = avventRepository,
        transactionManager = transactionManager,
    )

    private val token = "test-token"
    private val callId = "test-call-id"
    private val personident = ARBEIDSTAKER_FNR

    private val tilbakemelding = Tilbakemelding(
        varseltekst = "Test tilbakemelding",
        motebehovId = "test-motebehov-id",
    )

    private val activeAvvent = Avvent(
        uuid = UUID.randomUUID(),
        createdAt = OffsetDateTime.now(),
        frist = LocalDate.now().plusWeeks(2),
        createdBy = "Z999999",
        personident = personident,
        beskrivelse = "Avventer noe",
        isLukket = false,
    )

    @BeforeEach
    fun setup() {
        clearMocks(motebehovClient, avventRepository, transactionManager)

        coJustRun { motebehovClient.behandleMotebehov(any(), any(), any()) }
        coJustRun { motebehovClient.sendTilbakemelding(any(), any(), any()) }
        coEvery { transactionManager.run<Any?>(any()) } coAnswers {
            val block = firstArg<suspend (ITransaction) -> Any?>()
            block(mockk())
        }
    }

    @Nested
    inner class BehandleMotebehov {
        @Test
        fun `calls behandleMotebehov on client`() {
            runBlocking {
                every { avventRepository.getActiveAvvent(any(), any()) } returns null

                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = true,
                    tilbakemeldinger = emptyList(),
                    token = token,
                    callId = callId,
                )

                coVerify(exactly = 1) {
                    motebehovClient.behandleMotebehov(personident, token, callId)
                }
            }
        }

        @Test
        fun `lukker active avvent when har ikke behov for mote`() {
            runBlocking {
                every { avventRepository.getActiveAvvent(any(), any()) } returns activeAvvent
                justRun { avventRepository.setLukket(any(), any()) }

                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = false,
                    tilbakemeldinger = emptyList(),
                    token = token,
                    callId = callId,
                )

                verify(exactly = 1) {
                    avventRepository.setLukket(activeAvvent.uuid, any())
                }
            }
        }

        @Test
        fun `does not lukke avvent when har behov for mote`() {
            runBlocking {
                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = true,
                    tilbakemeldinger = emptyList(),
                    token = token,
                    callId = callId,
                )

                verify(exactly = 0) {
                    avventRepository.getActiveAvvent(any(), any())
                }
                verify(exactly = 0) {
                    avventRepository.setLukket(any(), any())
                }
            }
        }

        @Test
        fun `does not lukke avvent when no active avvent exists`() {
            runBlocking {
                every { avventRepository.getActiveAvvent(any(), any()) } returns null

                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = false,
                    tilbakemeldinger = emptyList(),
                    token = token,
                    callId = callId,
                )

                verify(exactly = 1) {
                    avventRepository.getActiveAvvent(personident, any())
                }
                verify(exactly = 0) {
                    avventRepository.setLukket(any(), any())
                }
            }
        }

        @Test
        fun `sends tilbakemeldinger via client`() {
            runBlocking {
                every { avventRepository.getActiveAvvent(any(), any()) } returns null
                val tilbakemelding2 = Tilbakemelding(
                    varseltekst = "Andre tilbakemelding",
                    motebehovId = "motebehov-id-2",
                )

                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = false,
                    tilbakemeldinger = listOf(tilbakemelding, tilbakemelding2),
                    token = token,
                    callId = callId,
                )

                coVerify(exactly = 1) {
                    motebehovClient.sendTilbakemelding(tilbakemelding, token, callId)
                }
                coVerify(exactly = 1) {
                    motebehovClient.sendTilbakemelding(tilbakemelding2, token, callId)
                }
            }
        }

        @Test
        fun `does not send tilbakemelding when list is empty`() {
            runBlocking {
                every { avventRepository.getActiveAvvent(any(), any()) } returns null

                motebehovService.behandleMotebehov(
                    personident = personident,
                    harBehovForMote = false,
                    tilbakemeldinger = emptyList(),
                    token = token,
                    callId = callId,
                )

                coVerify(exactly = 0) {
                    motebehovClient.sendTilbakemelding(any(), any(), any())
                }
            }
        }
    }
}
