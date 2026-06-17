package no.nav.syfo.testhelper.mock

import no.nav.syfo.common.mock.tilgangskontroll.MockUserSyfoTilgangLevel
import no.nav.syfo.common.mock.tilgangskontroll.MockUserTilgangDetails
import no.nav.syfo.common.types.ident.NavIdent
import no.nav.syfo.common.types.ident.PersonIdent as LibPersonIdent
import no.nav.syfo.testhelper.UserConstants

val accessiblePersonIdents = setOf(
    LibPersonIdent(UserConstants.ARBEIDSTAKER_FNR.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_ANNEN_FNR.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_TREDJE_FNR.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_FJERDE_FNR.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_NO_JOURNALFORING.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_IKKE_VARSEL.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_INACTIVE_OPPFOLGINGSTILFELLE.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_NO_BEHANDLENDE_ENHET.value),
    LibPersonIdent(UserConstants.ARBEIDSTAKER_NO_OPPFOLGINGSTILFELLE.value),
)

val mockTilgangDetailsPerNavIdent = mapOf(
    NavIdent(UserConstants.VEILEDER_IDENT) to MockUserTilgangDetails(
        syfoTilgangLevel = MockUserSyfoTilgangLevel.FULL,
        personsUserHasAccessTo = accessiblePersonIdents,
    ),
    NavIdent(UserConstants.VEILEDER_IDENT_2) to MockUserTilgangDetails(
        syfoTilgangLevel = MockUserSyfoTilgangLevel.FULL,
        personsUserHasAccessTo = accessiblePersonIdents,
    ),
    NavIdent(UserConstants.VEILEDER_IDENT_READONLY) to MockUserTilgangDetails(
        syfoTilgangLevel = MockUserSyfoTilgangLevel.READ,
        personsUserHasAccessTo = accessiblePersonIdents,
    ),
)
