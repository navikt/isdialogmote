package no.nav.syfo.api

fun bearerHeader(token: String): String {
    return "Bearer $token"
}
