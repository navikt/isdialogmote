package no.nav.syfo.cronjob.dialogmotesvar

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.api.domain.Dialogmotesvar
import no.nav.syfo.dialogmote.database.getArbeidsgiveresUnpublishedMotesvar
import no.nav.syfo.dialogmote.database.getArbeidstakeresUnpublishedMotesvar
import no.nav.syfo.dialogmote.database.getBehandleresUnpublishedMotesvar

class PublishDialogmotesvarService(
    private val database: DatabaseInterface,
    private val dialogmotesvarProducer: DialogmotesvarProducer,
) {
    fun getUnpublishedDialogmotesvar(): List<Dialogmotesvar> {
        val unpublishedDialogmotesvar = mutableListOf<Dialogmotesvar>()
        database.connection.use { connection ->
            val arbeidstakeresUnpublishedMotesvar = connection.getArbeidstakeresUnpublishedMotesvar()
            val arbeidsgiveresUnpublishedMotesvar = connection.getArbeidsgiveresUnpublishedMotesvar()
            val behandleresUnpublishedMotesvar = connection.getBehandleresUnpublishedMotesvar()

            unpublishedDialogmotesvar.addAll(arbeidstakeresUnpublishedMotesvar)
            unpublishedDialogmotesvar.addAll(arbeidsgiveresUnpublishedMotesvar)
            unpublishedDialogmotesvar.addAll(behandleresUnpublishedMotesvar)
        }

        return unpublishedDialogmotesvar
    }

    fun publishAndUpdateDialogmotesvar(
        dialogmotesvar: Dialogmotesvar,
    ) {
        // TODO: Publish on kafka topic
        // val moteId = dialogmotesvar.moteuuid
        // val kDialogmotesvar = dialogmotesvar.toKDialogmotesvar()
        // dialogmotesvarProducer.sendDialogmotesvar(kDialogmotesvar, moteId)

        // Sende inn en connection her?
        // Switch på senderType, og så oppdatere på varseluuid?
        /*database.updateMotesvarPublishedAt(
            moteStatussvarId = dialogmotesvar.id,
        )*/
    }
}
