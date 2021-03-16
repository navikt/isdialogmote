package no.nav.syfo.client.person.adressebeskyttelse

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_FNR
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AdressebeskyttelseClientSpek : Spek({

    val cacheKey = "person-adressebeskyttelse-${ARBEIDSTAKER_FNR.value}"
    val mock = MockEngine {
        respond(
            "{ \"beskyttet\": false }",
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString())
        )
    }

    val httpClientMock = HttpClient(mock) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    describe(AdressebeskyttelseClientSpek::class.java.simpleName) {
        it("hasAdressebeskyttelse returns true when cached value is true") {
            val cache = mockk<RedisStore>()
            every { cache.get(cacheKey) } returns "true"
            val client = AdressebeskyttelseClient(cache, httpClientMock, "url")
            runBlocking {
                client.hasAdressebeskyttelse(ARBEIDSTAKER_FNR, "token", "callID") shouldBeEqualTo true
            }
            verify(exactly = 1) { cache.get(cacheKey) }
        }
        it("hasAdressebeskyttelse returns false when cached value is false") {
            val cache = mockk<RedisStore>()
            every { cache.get(cacheKey) } returns "false"
            val client = AdressebeskyttelseClient(cache, httpClientMock, "url")
            runBlocking {
                client.hasAdressebeskyttelse(ARBEIDSTAKER_FNR, "token", "callID") shouldBeEqualTo false
            }
            verify(exactly = 1) { cache.get(cacheKey) }
        }
        it("hasAdressebeskyttelse calls api and sets cached value when cached value is null") {
            val cache = mockk<RedisStore>()
            every { cache.get(cacheKey) } returns null
            justRun { cache.set(any(), any(), any()) }
            val client = AdressebeskyttelseClient(cache, httpClientMock, "url")
            runBlocking {
                client.hasAdressebeskyttelse(ARBEIDSTAKER_FNR, "token", "callID") shouldBeEqualTo false
            }
            verify(exactly = 1) { cache.get(cacheKey) }
            verify(exactly = 1) { cache.set(cacheKey, "false", 3600) }
        }
    }
})
