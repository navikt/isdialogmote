package no.nav.syfo.testhelper.generator

import no.nav.syfo.client.pdl.*
import no.nav.syfo.testhelper.UserConstants

fun generatePdlPersonResponse() = PdlPersonResponse(
    errors = null,
    data = generatePdlHentPerson(
        generatePdlPersonNavn(),
    )
)

fun generatePdlPersonNavn(): PdlPersonNavn {
    return PdlPersonNavn(
        fornavn = UserConstants.PERSON_FORNAVN,
        mellomnavn = UserConstants.PERSON_MELLOMNAVN,
        etternavn = UserConstants.PERSON_ETTERNAVN,
    )
}

fun generatePdlHentPerson(
    pdlPersonNavn: PdlPersonNavn?,
): PdlHentPerson {
    return PdlHentPerson(
        hentPerson = PdlPerson(
            navn = listOf(
                pdlPersonNavn ?: generatePdlPersonNavn()
            ),
            adressebeskyttelse = emptyList()
        )
    )
}

fun generatePdlIdenter(
    personident: String,
    anotherPersonIdent: String? = null,
) = PdlIdentResponse(
    data = PdlHentIdenter(
        hentIdenter = PdlIdenter(
            identer = mutableListOf(
                PdlIdent(
                    ident = personident,
                    historisk = false,
                    gruppe = IdentGruppe.FOLKEREGISTERIDENT,
                ),
                PdlIdent(
                    ident = personident.toFakeOldIdent(),
                    historisk = true,
                    gruppe = IdentGruppe.FOLKEREGISTERIDENT,
                ),
            ).also {
                if (anotherPersonIdent != null) {
                    it.add(
                        PdlIdent(
                            ident = anotherPersonIdent,
                            historisk = false,
                            gruppe = IdentGruppe.FOLKEREGISTERIDENT,
                        ),
                    )
                }
            },
        ),
    ),
    errors = null,
)

fun generatePdlError(code: String? = null) = listOf(
    PdlError(
        message = "Error",
        locations = emptyList(),
        path = emptyList(),
        extensions = PdlErrorExtension(
            code = code,
            classification = "Classification",
        )
    )
)

private fun String.toFakeOldIdent(): String {
    val substring = this.drop(1)
    return "9$substring"
}
