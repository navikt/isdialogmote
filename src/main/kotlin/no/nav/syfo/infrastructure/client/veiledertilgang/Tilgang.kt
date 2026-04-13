package no.nav.syfo.infrastructure.client.veiledertilgang

data class Tilgang(
    val erGodkjent: Boolean,
    val erAvslatt: Boolean,
    val fullTilgang: Boolean,
    val finnfastlegeTilgang: Boolean,
    val legacyTilgang: Boolean,
)
