package no.nav.syfo.testhelper.generator

import no.nav.syfo.client.dokarkiv.domain.*
import no.nav.syfo.testhelper.UserConstants
import java.util.*

fun generateJournalpostRequest(
    tittel: String,
    brevkodeType: BrevkodeType,
    pdf: ByteArray,
    kanal: String,
    varselId: UUID,
) = JournalpostRequest.create(
    avsenderMottaker = AvsenderMottaker.create(
        id = UserConstants.ARBEIDSTAKER_FNR.value,
        idType = BrukerIdType.PERSON_IDENT,
        navn = UserConstants.ARBEIDSTAKERNAVN,
    ),
    bruker = Bruker.create(
        id = UserConstants.ARBEIDSTAKER_FNR.value,
        idType = BrukerIdType.PERSON_IDENT
    ),
    tittel = tittel,
    dokumenter = listOf(
        Dokument.create(
            brevkode = brevkodeType,
            tittel = tittel,
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = tittel,
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
    kanal = JournalpostKanal.SENTRAL_UTSKRIFT,
    journalfoerendeEnhet = null,
    journalpostType = JournalpostType.UTGAAENDE,
    tema = JournalpostTema.OPPFOLGING,
    sak = Sak.invoke(SaksType.GENERELL),
    eksternReferanseId = varselId.toString(),
)
