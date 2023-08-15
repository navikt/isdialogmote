package no.nav.syfo.testdata.reset.database

import java.sql.Connection

const val queryDeleteMote =
    """
    DELETE FROM MOTE
    WHERE id = ?
    """

fun Connection.deleteMote(
    commit: Boolean = true,
    moteId: Int,
) {
    this.prepareStatement(queryDeleteMote).use {
        it.setInt(1, moteId)
        it.execute()
    }
    if (commit) {
        this.commit()
    }
}
