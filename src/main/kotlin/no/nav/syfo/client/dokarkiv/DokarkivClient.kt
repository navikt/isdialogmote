package no.nav.syfo.client.dokarkiv

import no.nav.syfo.client.azuread.AzureAdClient
import org.slf4j.LoggerFactory

class DokarkivClient(
    private val azureAdClient: AzureAdClient,
    private val dokarkivClientId: String,
) {
    suspend fun journalfor() {
        log.info("Requesting access token from AzureAD")
        azureAdClient.getAccessTokenForResource(dokarkivClientId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DokarkivClient::class.java)
    }
}
