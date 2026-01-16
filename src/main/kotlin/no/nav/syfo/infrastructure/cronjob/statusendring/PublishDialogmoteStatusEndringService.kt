package no.nav.syfo.infrastructure.cronjob.statusendring

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.infrastructure.database.model.PDialogmote
import no.nav.syfo.infrastructure.database.model.toDialogmoteStatusEndret
import no.nav.syfo.infrastructure.database.model.toDialogmoteTidSted
import no.nav.syfo.infrastructure.database.getDialogmote
import no.nav.syfo.infrastructure.database.getMoteDeltakerArbeidsgiver
import no.nav.syfo.infrastructure.database.getMoteDeltakerArbeidstaker
import no.nav.syfo.infrastructure.database.getTidSted
import no.nav.syfo.infrastructure.database.repository.MoteStatusEndretRepository
import no.nav.syfo.domain.dialogmote.DialogmoteStatusEndret
import no.nav.syfo.domain.dialogmote.DialogmoteTidSted
import no.nav.syfo.domain.dialogmote.latest
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class PublishDialogmoteStatusEndringService(
    private val database: DatabaseInterface,
    private val dialogmoteStatusEndringProducer: DialogmoteStatusEndringProducer,
    private val moteStatusEndretRepository: MoteStatusEndretRepository,
) {
    fun getDialogmoteStatuEndretToPublishList(): List<DialogmoteStatusEndret> {
        return moteStatusEndretRepository.getMoteStatusEndretNotPublished().map {
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

        moteStatusEndretRepository.updateMoteStatusEndretPublishedAt(
            moteStatusEndretId = dialogmoteStatusEndret.id,
        )
    }
}

fun createKDialogmoteStatusEndring(
    dialogmoteStatusEndret: DialogmoteStatusEndret,
    dialogmoteTidStedList: List<DialogmoteTidSted>,
    pDialogmote: PDialogmote,
    personIdent: PersonIdent,
    virksomhetsnummer: Virksomhetsnummer,
): KDialogmoteStatusEndring {
    val kDialogmoteStatusEndring = KDialogmoteStatusEndring()
    kDialogmoteStatusEndring.setDialogmoteUuid(pDialogmote.uuid.toString())
    kDialogmoteStatusEndring.setDialogmoteTidspunkt(dialogmoteTidStedList.latest()!!.tid.toInstantOslo())
    kDialogmoteStatusEndring.setStatusEndringType(dialogmoteStatusEndret.status.name)
    kDialogmoteStatusEndring.setStatusEndringTidspunkt(dialogmoteStatusEndret.createdAt.toInstantOslo())
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
