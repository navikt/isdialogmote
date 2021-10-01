package no.nav.syfo.client.pdl

data class PdlHentIdenterRequest(
    val query: String,
    val variables: PdlHentIdenterRequestVariables,
)

data class PdlHentIdenterRequestVariables(
    val ident: String,
    val historikk: Boolean = false,
    val grupper: List<String>,
)
