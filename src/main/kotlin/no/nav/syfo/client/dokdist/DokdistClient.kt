package no.nav.syfo.client.dokdist

import java.util.*

/*
TODO: Implementer kall til dokdistfordeling /rest/v1/distribuerJournalpost (https://confluence.adeo.no/pages/viewpage.action?pageId=320038938)
 for å sende journalpost som fysisk brev.
 */
class DokdistClient {
    suspend fun distribuerJournalpost(
        journalpostId: String
    ): DokdistResponse? {
        // TODO: Change before enabling allowVarselMedFysiskBrev
        return DokdistResponse(
            bestillingsId = UUID.randomUUID().toString()
        )
    }
}
