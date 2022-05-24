package no.nav.syfo.client.journalpostdistribusjon

data class JournalpostdistribusjonRequest(
    val journalpostId: String,
    val bestillendeFagsystem: String,
    val dokumentProdApp: String,
    val distribusjonstype: String = Distribusjonstype.ANNET.name,
    val distribusjonstidspunkt: String = Distribusjonstidspunkt.KJERNETID.name,
)

enum class Distribusjonstype {
    VEDTAK,
    VIKTIG,
    ANNET,
}

enum class Distribusjonstidspunkt {
    UMIDDELBART,
    KJERNETID,
}
