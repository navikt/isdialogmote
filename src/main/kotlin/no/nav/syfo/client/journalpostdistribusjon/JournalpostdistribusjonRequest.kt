package no.nav.syfo.client.journalpostdistribusjon

data class JournalpostdistribusjonRequest(
    val journalpostId: String,
    val bestillendeFagsystem: String,
    val dokumentProdApp: String,
)
