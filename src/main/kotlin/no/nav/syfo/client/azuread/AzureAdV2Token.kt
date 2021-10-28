package no.nav.syfo.client.azuread

import java.io.Serializable
import java.time.LocalDateTime

data class AzureAdV2Token(
    val accessToken: String,
    val expires: LocalDateTime
) : Serializable

fun AzureAdV2Token.isExpired() = this.expires < LocalDateTime.now().plusSeconds(60)
