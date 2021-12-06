package no.nav.syfo.cronjob.statusendring

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.dialogmote.database.*
import no.nav.syfo.dialogmote.database.domain.*
import no.nav.syfo.dialogmote.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.*

class PublishDialogmoteStatusEndringService(
    private val database: DatabaseInterface,
    private val dialogmoteStatusEndringProducer: DialogmoteStatusEndringProducer,
) {
    fun getDialogmoteStatuEndretToPublishList(): List<DialogmoteStatusEndret> {
        return database.getMoteStatusEndretNotPublished().map {
            it.toDialogmoteStatusEndret()
        }
    }

    fun publishAndUpdateDialogmoteStatusEndring(
        dialogmoteStatusEndret: DialogmoteStatusEndret,
    ) {
        val moteId = dialogmoteStatusEndret.moteId

        val kDialogmoteStatusEndring = createKDialogmoteStatusEndring(
            dialogmoteStatusEndret = dialogmoteStatusEndret,
            dialogmoteTidStedList = database.getTidSted(moteId).map {
                it.toDialogmoteTidSted()
            },
            pDialogmote = database.getDialogmote(id = moteId).first(),
            personIdent = database.getMoteDeltakerArbeidstaker(moteId).personIdent,
            virksomhetsnummer = database.getMoteDeltakerArbeidsgiver(moteId).virksomhetsnummer,
        )
        dialogmoteStatusEndringProducer.sendDialogmoteStatusEndring(kDialogmoteStatusEndring)

        database.updateMoteStatusEndretPublishedAt(
            moteStatusEndretId = dialogmoteStatusEndret.id,
        )
    }
}

fun createKDialogmoteStatusEndring(
    dialogmoteStatusEndret: DialogmoteStatusEndret,
    dialogmoteTidStedList: List<DialogmoteTidSted>,
    pDialogmote: PDialogmote,
    personIdent: PersonIdentNumber,
    virksomhetsnummer: Virksomhetsnummer,
): KDialogmoteStatusEndring {
    val kDialogmoteStatusEndring = KDialogmoteStatusEndring()
    kDialogmoteStatusEndring.setDialogmoteUuid(pDialogmote.uuid.toString())
    kDialogmoteStatusEndring.setDialogmoteTidspunkt(dialogmoteTidStedList.latest()!!.tid.toInstantOslo())
    kDialogmoteStatusEndring.setStatusEndringType(dialogmoteStatusEndret.status.name)
    kDialogmoteStatusEndring.setStatusEndringTidspunkt(LocalDateTime.now().toInstantOslo())
    kDialogmoteStatusEndring.setPersonIdent(personIdent.value)
    kDialogmoteStatusEndring.setVirksomhetsnummer(virksomhetsnummer.value)
    kDialogmoteStatusEndring.setEnhetNr(pDialogmote.tildeltEnhet)
    kDialogmoteStatusEndring.setNavIdent(dialogmoteStatusEndret.opprettetAv)
    kDialogmoteStatusEndring.setTilfelleStartdato(dialogmoteStatusEndret.tilfelleStart.atStartOfDay().toInstantOslo())
    kDialogmoteStatusEndring.setArbeidstaker(true)
    kDialogmoteStatusEndring.setArbeidsgiver(true)
    kDialogmoteStatusEndring.setSykmelder(dialogmoteStatusEndret.motedeltakerBehandler)
    return kDialogmoteStatusEndring
}

fun LocalDateTime.toInstantOslo(): Instant = toInstant(
    ZoneId.of("Europe/Oslo").rules.getOffset(this)
)
