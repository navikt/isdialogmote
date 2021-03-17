package no.nav.syfo.client.person.kontaktinfo

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.mock.SyfopersonMock
import no.nav.syfo.testhelper.mock.digitalKontaktinfoBolkKanVarslesTrue
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class KontaktinformasjonClientSpek : Spek({

    val personIdent = UserConstants.ARBEIDSTAKER_FNR
    val digitalKontaktInfo = digitalKontaktinfoBolkKanVarslesTrue(personIdent.value)
    val digitalKontaktInfoCacheKey = "person-kontaktinformasjon-${personIdent.value}"

    val anyToken = "token"
    val anyCallId = "callId"

    describe(KontaktinformasjonClientSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val syfopersonMock = SyfopersonMock()
            val cacheMock = mockk<RedisStore>(relaxed = true)
            val client = KontaktinformasjonClient(cacheMock, syfopersonMock.url)

            beforeGroup {
                syfopersonMock.server.start()
            }

            afterGroup {
                syfopersonMock.server.stop(1L, 10L)
            }

            beforeEachTest {
                clearMocks(cacheMock)
            }

            it("returns cached kontaktinformasjon") {
                every {
                    cacheMock.getObject<DigitalKontaktinfoBolk>(
                        digitalKontaktInfoCacheKey
                    )
                } returns digitalKontaktInfo

                runBlocking {
                    client.kontaktinformasjon(personIdent, anyToken, anyCallId) shouldBeEqualTo digitalKontaktInfo
                }
                verify(exactly = 1) {
                    cacheMock.getObject<DigitalKontaktinfoBolk>(
                        digitalKontaktInfoCacheKey
                    )
                }
                verify(exactly = 0) { cacheMock.set(any(), any(), any()) }
            }

            it("caches kontaktinformasjon") {
                every {
                    cacheMock.getObject<DigitalKontaktinfoBolk>(
                        digitalKontaktInfoCacheKey
                    )
                } returns null

                runBlocking {
                    client.kontaktinformasjon(personIdent, anyToken, anyCallId) shouldBeEqualTo digitalKontaktInfo
                }

                verify(exactly = 1) {
                    cacheMock.getObject<DigitalKontaktinfoBolk>(
                        digitalKontaktInfoCacheKey
                    )
                }
                verify(exactly = 1) { cacheMock.setObject(digitalKontaktInfoCacheKey, digitalKontaktInfo, 600) }
            }
        }
    }
})
