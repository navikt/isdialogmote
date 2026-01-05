package no.nav.syfo.brev.narmesteleder

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.NarmesteLederAccessService
import no.nav.syfo.domain.dialogmote.toNarmesteLederBrevDTOList
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.Oppfolgingstilfelle
import no.nav.syfo.infrastructure.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.application.DialogmotedeltakerService
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateDialogmote
import no.nav.syfo.testhelper.generator.generateDialogmotedeltakerArbeidsgiver
import no.nav.syfo.testhelper.generator.generateDialogmotedeltakerArbeidstaker
import no.nav.syfo.testhelper.generator.generateReferat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NarmesteLederAccessServiceTest {
    private val anyToken = "any-token"
    private val anyCallId = "any-call-id"

    private val dialogmotedeltakerArbeidsgiver = generateDialogmotedeltakerArbeidsgiver()
    private val dialogmotedeltakerArbeidstaker = generateDialogmotedeltakerArbeidstaker()
    private val dialogmoteList = listOf(generateDialogmote())

    private val dialogmotedeltakerServiceMock = mockk<DialogmotedeltakerService>()
    private val narmesteLederClientMock = mockk<NarmesteLederClient>()
    private val oppfolgingstilfelleClientMock = mockk<OppfolgingstilfelleClient>()
    private val referatCreated6MonthsPrior = generateReferat(6L)
    private val referatCreated3MonthsPrior = generateReferat(3L)

    private val narmesteLederAccessService = NarmesteLederAccessService(
        dialogmotedeltakerServiceMock,
        narmesteLederClientMock,
        oppfolgingstilfelleClientMock
    )

    @BeforeEach
    fun beforeEach() {
        clearMocks(dialogmotedeltakerServiceMock)
        clearMocks(narmesteLederClientMock)
        clearMocks(oppfolgingstilfelleClientMock)

        coEvery {
            dialogmotedeltakerServiceMock.getDialogmoteDeltakerArbeidsgiverById(
                any(),
            )
        } returns dialogmotedeltakerArbeidsgiver

        coEvery {
            dialogmotedeltakerServiceMock.getDialogmoteDeltakerArbeidstaker(
                any(),
            )
        } returns dialogmotedeltakerArbeidstaker
    }

    @Nested
    @DisplayName("removeExpiredBrevInDialogmoter removes brev created before a validity period")
    inner class RemoveExpiredBrev {

        @Test
        fun `when validity period is extended by oppfolgingstilfelle`() {
            coEvery {
                oppfolgingstilfelleClientMock.oppfolgingstilfelleTokenx(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns listOf(
                Oppfolgingstilfelle(
                    start = LocalDate.now().minusMonths(5),
                    end = LocalDate.now().minusMonths(2)
                ),
                Oppfolgingstilfelle(
                    start = LocalDate.now().minusMonths(6),
                    end = LocalDate.now().minusMonths(2)
                )
            )

            val moteList = runBlocking {
                narmesteLederAccessService.removeExpiredBrevInDialogmoter(
                    moteList = dialogmoteList,
                    narmesteLederPersonIdentNumber = UserConstants.NARMESTELEDER_FNR,
                    arbeidstakerPersonIdentNumber = UserConstants.ARBEIDSTAKER_FNR,
                    tokenx = anyToken,
                    callId = anyCallId
                )
            }
            val brev = moteList.toNarmesteLederBrevDTOList()

            assertEquals(4, brev.size)
        }

        @Test
        fun `when oppfolgingstilfelle does not overlap with grace period`() {
            coEvery {
                oppfolgingstilfelleClientMock.oppfolgingstilfelleTokenx(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns listOf(
                Oppfolgingstilfelle(
                    start = LocalDate.now().minusMonths(10),
                    end = LocalDate.now().minusMonths(6)
                )
            )

            val moteList = runBlocking {
                narmesteLederAccessService.removeExpiredBrevInDialogmoter(
                    moteList = dialogmoteList,
                    narmesteLederPersonIdentNumber = UserConstants.NARMESTELEDER_FNR,
                    arbeidstakerPersonIdentNumber = UserConstants.ARBEIDSTAKER_FNR,
                    tokenx = anyToken,
                    callId = anyCallId
                )
            }
            val brev = moteList.toNarmesteLederBrevDTOList()

            assertEquals(2, brev.size)
        }

        @Test
        fun `when there is no oppfolgingstilfelle`() {
            coEvery {
                oppfolgingstilfelleClientMock.oppfolgingstilfelleTokenx(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns emptyList()

            val moteList = runBlocking {
                narmesteLederAccessService.removeExpiredBrevInDialogmoter(
                    moteList = dialogmoteList,
                    narmesteLederPersonIdentNumber = UserConstants.NARMESTELEDER_FNR,
                    arbeidstakerPersonIdentNumber = UserConstants.ARBEIDSTAKER_FNR,
                    tokenx = anyToken,
                    callId = anyCallId
                )
            }
            val brev = moteList.toNarmesteLederBrevDTOList()

            assertEquals(2, brev.size)
        }

        @Test
        fun `when oppfolgingstilfelle client fails`() {
            coEvery {
                oppfolgingstilfelleClientMock.oppfolgingstilfelleTokenx(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns null

            val moteList = runBlocking {
                narmesteLederAccessService.removeExpiredBrevInDialogmoter(
                    moteList = dialogmoteList,
                    narmesteLederPersonIdentNumber = UserConstants.NARMESTELEDER_FNR,
                    arbeidstakerPersonIdentNumber = UserConstants.ARBEIDSTAKER_FNR,
                    tokenx = anyToken,
                    callId = anyCallId
                )
            }
            val brev = moteList.toNarmesteLederBrevDTOList()

            assertEquals(2, brev.size)
        }
    }

    @Nested
    @DisplayName("isBrevExpired")
    inner class IsBrevExpired {

        @Nested
        @DisplayName("returns false when brev is created in a validity period")
        inner class ReturnsFalseWhenBrevIsCreatedInAValidityPeriod {

            @Test
            fun `and validity period is extended by oppfolgingstilfelle`() {
                coEvery {
                    oppfolgingstilfelleClientMock.oppfolgingstilfelleTokenx(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } returns listOf(
                    Oppfolgingstilfelle(
                        start = LocalDate.now().minusMonths(5),
                        end = LocalDate.now().minusMonths(2)
                    ),
                    Oppfolgingstilfelle(
                        start = LocalDate.now().minusMonths(6),
                        end = LocalDate.now().minusMonths(2)
                    )
                )

                val isBrevExpired = runBlocking {
                    narmesteLederAccessService.isBrevExpired(
                        brev = referatCreated6MonthsPrior,
                        narmesteLederPersonIdentNumber = UserConstants.NARMESTELEDER_FNR,
                        tokenx = anyToken,
                        callId = anyCallId
                    )
                }

                assertFalse(isBrevExpired)
            }

            @Test
            fun `when there is no oppfolgingstilfelle`() {
                coEvery {
                    oppfolgingstilfelleClientMock.oppfolgingstilfelleTokenx(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } returns emptyList()

                val isBrevExpired = runBlocking {
                    narmesteLederAccessService.isBrevExpired(
                        brev = referatCreated3MonthsPrior,
                        narmesteLederPersonIdentNumber = UserConstants.NARMESTELEDER_FNR,
                        tokenx = anyToken,
                        callId = anyCallId
                    )
                }

                assertFalse(isBrevExpired)
            }
        }

        @Nested
        @DisplayName("returns true when brev is created before a validity period")
        inner class ReturnsTrueWhenBrevIsCreatedBeforeAValidityPeriod {

            @Test
            fun `when there is no oppfolgingstilfelle`() {
                coEvery {
                    oppfolgingstilfelleClientMock.oppfolgingstilfelleTokenx(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } returns emptyList()

                val isBrevExpired = runBlocking {
                    narmesteLederAccessService.isBrevExpired(
                        brev = referatCreated6MonthsPrior,
                        narmesteLederPersonIdentNumber = UserConstants.NARMESTELEDER_FNR,
                        tokenx = anyToken,
                        callId = anyCallId
                    )
                }

                assertTrue(isBrevExpired)
            }

            @Test
            fun `when oppfolgingstilfelle ended before grace period`() {
                coEvery {
                    oppfolgingstilfelleClientMock.oppfolgingstilfelleTokenx(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } returns listOf(
                    Oppfolgingstilfelle(
                        start = LocalDate.now().minusMonths(10),
                        end = LocalDate.now().minusMonths(6)
                    )
                )

                val isBrevExpired = runBlocking {
                    narmesteLederAccessService.isBrevExpired(
                        brev = referatCreated6MonthsPrior,
                        narmesteLederPersonIdentNumber = UserConstants.NARMESTELEDER_FNR,
                        tokenx = anyToken,
                        callId = anyCallId
                    )
                }

                assertTrue(isBrevExpired)
            }
        }
    }
}
