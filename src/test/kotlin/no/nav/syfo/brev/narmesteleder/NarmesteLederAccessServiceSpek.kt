package no.nav.syfo.brev.narmesteleder

import io.ktor.server.testing.TestApplicationEngine
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.client.oppfolgingstilfelle.Oppfolgingstilfelle
import no.nav.syfo.client.oppfolgingstilfelle.OppfolgingstilfelleClient
import no.nav.syfo.dialogmote.DialogmotedeltakerService
import no.nav.syfo.dialogmote.domain.toNarmesteLederBrevDTOList
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateDialogmote
import no.nav.syfo.testhelper.generator.generateDialogmotedeltakerArbeidsgiver
import no.nav.syfo.testhelper.generator.generateDialogmotedeltakerArbeidstaker
import no.nav.syfo.testhelper.generator.generateReferat
import org.amshove.kluent.shouldBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NarmesteLederAccessServiceSpek : Spek({
    describe(NarmesteLederAccessServiceSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val anyToken = "any-token"
            val anyCallId = "any-call-id"

            val dialogmotedeltakerArbeidsgiver = generateDialogmotedeltakerArbeidsgiver()
            val dialogmotedeltakerArbeidstaker = generateDialogmotedeltakerArbeidstaker()
            val dialogmoteList = listOf(generateDialogmote())

            val dialogmotedeltakerServiceMock = mockk<DialogmotedeltakerService>()
            val narmesteLederClientMock = mockk<NarmesteLederClient>()
            val oppfolgingstilfelleClientMock = mockk<OppfolgingstilfelleClient>()
            val referatCreated6MonthsPrior = generateReferat(6L)
            val referatCreated3MonthsPrior = generateReferat(3L)

            val narmesteLederAccessService = NarmesteLederAccessService(
                dialogmotedeltakerServiceMock,
                narmesteLederClientMock,
                oppfolgingstilfelleClientMock
            )

            beforeEachTest {
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

            describe("removeExpiredBrevInDialogmoter removes brev created before a validity period") {
                it("when validity period is extended by oppfolgingstilfelle") {
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

                    brev.size shouldBe 4
                }

                it("when oppfolgingstilfelle does not overlap with grace period") {
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

                    brev.size shouldBe 2
                }

                it("when there is no oppfolgingstilfelle") {
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

                    brev.size shouldBe 2
                }

                it("when oppfolgingstilfelle client fails") {
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

                    brev.size shouldBe 2
                }

                describe("isBrevExpired") {
                    describe("returns false when brev is created in a validity period") {
                        it("and validity period is extended by oppfolgingstilfelle") {
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

                            isBrevExpired shouldBe false
                        }

                        it("when there is no oppfolgingstilfelle") {
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

                            isBrevExpired shouldBe false
                        }
                    }

                    describe("returns true when brev is created before a validity period") {
                        it("when there is no oppfolgingstilfelle") {
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

                            isBrevExpired shouldBe true
                        }

                        it("when oppfolgingstilfelle ended before grace period") {
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

                            isBrevExpired shouldBe true
                        }
                    }
                }
            }
        }
    }
})
