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
) {
    fun isNotFound() = this.extensions.code == "not_found"
}

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
)

data class PdlPersonNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class PdlIdenter(
    val identer: List<PdlIdent>
) {
    val aktivIdent: String? = identer.firstOrNull {
        it.gruppe == IdentGruppe.FOLKEREGISTERIDENT && !it.historisk
    }?.ident

    fun identhendelseIsNotHistorisk(newIdent: String): Boolean {
        return identer.none { it.ident == newIdent && it.historisk }
    }
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

fun String.lowerCapitalize() =
    this.split(" ").joinToString(" ") { name ->
        val nameWithDash = name.split("-")
        if (nameWithDash.size > 1) {
            nameWithDash.joinToString("-") { it.capitalizeName() }
        } else {
            name.capitalizeName()
        }
    }

private fun String.capitalizeName() =
    this.lowercase(Locale.getDefault()).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

fun PdlError.errorMessage(): String {
    return "${this.message} with code: ${extensions.code} and classification: ${extensions.classification}"
}
