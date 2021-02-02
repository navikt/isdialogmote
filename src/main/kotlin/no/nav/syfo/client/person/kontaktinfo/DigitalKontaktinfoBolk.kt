package no.nav.syfo.client.person.kontaktinfo

import java.io.Serializable

data class DigitalKontaktinfoBolk(
    val feil: Map<String, Feil>? = null,
    val kontaktinfo: Map<String, DigitalKontaktinfo>? = null
) : Serializable

data class DigitalKontaktinfo(
    val epostadresse: String? = null,
    val kanVarsles: Boolean,
    val reservert: Boolean? = null,
    val mobiltelefonnummer: String? = null,
    val personident: String
) : Serializable

data class Feil(
    val melding: String
) : Serializable
