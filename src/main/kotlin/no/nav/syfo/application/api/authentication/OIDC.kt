package no.nav.syfo.application.api.authentication

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(JsonFeature) {
        serializer = JacksonSerializer {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

fun getWellKnown(wellKnownUrl: String) = runBlocking { HttpClient(Apache, proxyConfig).use { cli -> cli.get<WellKnown>(wellKnownUrl) } }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
