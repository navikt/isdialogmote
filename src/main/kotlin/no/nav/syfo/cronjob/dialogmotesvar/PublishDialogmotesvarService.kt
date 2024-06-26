package no.nav.syfo.cronjob.dialogmotesvar

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.*

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
