package no.nav.syfo.infrastructure.cronjob.dialogmotesvar

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmote.database.getUnpublishedArbeidsgiversvar
import no.nav.syfo.infrastructure.database.dialogmote.database.getUnpublishedArbeidstakersvar
import no.nav.syfo.infrastructure.database.dialogmote.database.getUnpublishedBehandlersvar
import no.nav.syfo.infrastructure.database.dialogmote.database.updateArbeidsgiverVarselPublishedAt
import no.nav.syfo.infrastructure.database.dialogmote.database.updateArbeidstakerVarselPublishedAt
import no.nav.syfo.infrastructure.database.dialogmote.database.updateBehandlersvarPublishedAt

class PublishDialogmotesvarService(
    private val database: DatabaseInterface,
    private val dialogmotesvarProducer: DialogmotesvarProducer,
) {
    fun getUnpublishedDialogmotesvar(): List<Dialogmotesvar> {
        val unpublishedDialogmotesvar = mutableListOf<Dialogmotesvar>()
        database.connection.use { connection ->
            val arbeidstakeresUnpublishedMotesvar = connection.getUnpublishedArbeidstakersvar()
            val arbeidsgiveresUnpublishedMotesvar = connection.getUnpublishedArbeidsgiversvar()
            val behandleresUnpublishedMotesvar = connection.getUnpublishedBehandlersvar()

            unpublishedDialogmotesvar.addAll(arbeidstakeresUnpublishedMotesvar)
            unpublishedDialogmotesvar.addAll(arbeidsgiveresUnpublishedMotesvar)
            unpublishedDialogmotesvar.addAll(behandleresUnpublishedMotesvar)
        }

        return unpublishedDialogmotesvar
    }

    fun publishAndUpdateDialogmotesvar(
        dialogmotesvar: Dialogmotesvar,
    ) {
        publishDialogmotesvar(dialogmotesvar)
        updateDialogmotesvar(dialogmotesvar)
    }

    private fun publishDialogmotesvar(
        dialogmotesvar: Dialogmotesvar,
    ) {
        val moteId = dialogmotesvar.moteuuid
        val kDialogmotesvar = dialogmotesvar.toKDialogmotesvar()
        dialogmotesvarProducer.sendDialogmotesvar(kDialogmotesvar, moteId)
    }

    private fun updateDialogmotesvar(
        dialogmotesvar: Dialogmotesvar,
    ) {
        when (dialogmotesvar.senderType) {
            SenderType.ARBEIDSTAKER -> {
                database.updateArbeidstakerVarselPublishedAt(varseluuid = dialogmotesvar.dbRef)
            }
            SenderType.ARBEIDSGIVER -> {
                database.updateArbeidsgiverVarselPublishedAt(varseluuid = dialogmotesvar.dbRef)
            }
            SenderType.BEHANDLER -> {
                database.updateBehandlersvarPublishedAt(svaruuid = dialogmotesvar.dbRef)
            }
        }
    }
}
