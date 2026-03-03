package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.dialogmoteflyt.DialogmoteFlyt
import java.util.UUID

interface IDialogmoteFlytRepository {
    fun getDialogmoteFlyt(dialogmoteFlytUuid: UUID): DialogmoteFlyt?
    fun getDialogmoteFlytForPerson(personIdent: PersonIdent): List<DialogmoteFlyt>
}
