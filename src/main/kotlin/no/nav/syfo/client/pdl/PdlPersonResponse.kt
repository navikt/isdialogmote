package no.nav.syfo.client.pdl

import java.util.Locale

data class PdlPersonResponse(
    val errors: List<PdlError>?,
    val data: PdlHentPerson?
)

data class PdlIdentResponse(
    val errors: List<PdlError>?,
    val data: PdlHentIdenter?
)

data class PdlError(
    val message: String,
    val locations: List<PdlErrorLocation>,
    val path: List<String>?,
    val extensions: PdlErrorExtension
)

data class PdlErrorLocation(
    val line: Int?,
    val column: Int?
)

data class PdlErrorExtension(
    val code: String?,
    val classification: String
)

data class PdlHentPerson(
    val hentPerson: PdlPerson?
)

data class PdlHentIdenter(
    val hentIdenter: PdlIdenter?
)

data class PdlPerson(
    val navn: List<PdlPersonNavn>,
    val adressebeskyttelse: List<Adressebeskyttelse>?
)

data class PdlPersonNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class Adressebeskyttelse(
    val gradering: Gradering
)

enum class Gradering {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT
}

data class PdlIdenter(
    val identer: List<PdlIdent>
) {
    val gjeldendeIdent: String? = identer.firstOrNull {
        it.gruppe == IdentGruppe.FOLKEREGISTERIDENT && !it.historisk
    }?.ident
}

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
    val gruppe: IdentGruppe,
)

enum class IdentGruppe {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}

fun PdlHentPerson.fullName(): String? {
    val nameList = this.hentPerson?.navn
    if (nameList.isNullOrEmpty()) {
        return null
    }
    nameList.first().let {
        val fornavn = it.fornavn.lowerCapitalize()
        val mellomnavn = it.mellomnavn
        val etternavn = it.etternavn.lowerCapitalize()

        return if (mellomnavn.isNullOrBlank()) {
            "$fornavn $etternavn"
        } else {
            "$fornavn ${mellomnavn.lowerCapitalize()} $etternavn"
        }
    }
}

fun PdlHentPerson?.isKode6Or7(): Boolean {
    val adressebeskyttelse = this?.hentPerson?.adressebeskyttelse
    return if (adressebeskyttelse.isNullOrEmpty()) {
        false
    } else {
        return adressebeskyttelse.any {
            it.isKode6() || it.isKode7()
        }
    }
}

fun String.lowerCapitalize(): String {
    return this.lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun Adressebeskyttelse.isKode6(): Boolean {
    return this.gradering == Gradering.STRENGT_FORTROLIG || this.gradering == Gradering.STRENGT_FORTROLIG_UTLAND
}

fun Adressebeskyttelse.isKode7(): Boolean {
    return this.gradering == Gradering.FORTROLIG
}

fun PdlError.errorMessage(): String {
    return "${this.message} with code: ${extensions.code} and classification: ${extensions.classification}"
}
