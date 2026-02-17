package no.nav.syfo.testdata.reset.database

import no.nav.syfo.infrastructure.database.UnitOfWork

const val queryDeleteMote =
    """
    DELETE FROM MOTE
    WHERE id = ?
    """

fun UnitOfWork.deleteMote(
    moteId: Int,
) {
    this.connection.prepareStatement(queryDeleteMote).use {
        it.setInt(1, moteId)
        it.execute()
    }
}
