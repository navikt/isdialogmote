package no.nav.syfo.testhelper.generator

import no.nav.syfo.client.pdl.*
import no.nav.syfo.testhelper.UserConstants

fun generatePdlPersonResponse(gradering: Gradering? = null) = PdlPersonResponse(
    errors = null,
    data = generatePdlHentPerson(
        generatePdlPersonNavn(),
        generateAdressebeskyttelse(gradering = gradering)
    )
)

fun generatePdlPersonNavn(): PdlPersonNavn {
    return PdlPersonNavn(
        fornavn = UserConstants.PERSON_FORNAVN,
        mellomnavn = UserConstants.PERSON_MELLOMNAVN,
        etternavn = UserConstants.PERSON_ETTERNAVN,
    )
}

fun generateAdressebeskyttelse(
    gradering: Gradering? = null
): Adressebeskyttelse {
    return Adressebeskyttelse(
        gradering = gradering ?: Gradering.UGRADERT
    )
}

fun generatePdlHentPerson(
    pdlPersonNavn: PdlPersonNavn?,
    adressebeskyttelse: Adressebeskyttelse? = null
): PdlHentPerson {
    return PdlHentPerson(
        hentPerson = PdlPerson(
            navn = listOf(
                pdlPersonNavn ?: generatePdlPersonNavn()
            ),
            adressebeskyttelse = listOf(
                adressebeskyttelse ?: generateAdressebeskyttelse()
            )
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

private fun String.toFakeOldIdent(): String {
    val substring = this.drop(1)
    return "9$substring"
}
