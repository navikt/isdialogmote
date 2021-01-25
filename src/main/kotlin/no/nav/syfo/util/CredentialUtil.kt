package no.nav.syfo.util

fun bearerHeader(token: String): String {
    return "Bearer $token"
}
