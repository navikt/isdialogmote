package no.nav.syfo.testhelper.generator

import no.nav.syfo.client.pdl.Adressebeskyttelse
import no.nav.syfo.client.pdl.Gradering
import no.nav.syfo.client.pdl.IdentGruppe
import no.nav.syfo.client.pdl.PdlHentIdenter
import no.nav.syfo.client.pdl.PdlHentPerson
import no.nav.syfo.client.pdl.PdlIdent
import no.nav.syfo.client.pdl.PdlIdentResponse
import no.nav.syfo.client.pdl.PdlIdenter
import no.nav.syfo.client.pdl.PdlPerson
import no.nav.syfo.client.pdl.PdlPersonNavn
import no.nav.syfo.client.pdl.PdlPersonResponse
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
) = PdlIdentResponse(
    data = PdlHentIdenter(
        hentIdenter = PdlIdenter(
            identer = listOf(
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
            ),
        ),
    ),
    errors = null,
)

private fun String.toFakeOldIdent(): String {
    val substring = this.drop(1)
    return "9$substring"
}
