package no.nav.syfo.client.ereg

data class EregOrganisasjonNavn(
    val navnelinje1: String,
    val redigertnavn: String?,
)

data class EregOrganisasjonResponse(
    val navn: EregOrganisasjonNavn,
)

fun EregOrganisasjonResponse.toEregVirksomhetsnavn(): EregVirksomhetsnavn =
    this.navn.let { (navnelinje1, redigertnavn) ->
        if (redigertnavn.isNullOrBlank()) {
            EregVirksomhetsnavn(
                virksomhetsnavn = navnelinje1
            )
        } else {
            EregVirksomhetsnavn(
                virksomhetsnavn = redigertnavn
            )
        }
    }
