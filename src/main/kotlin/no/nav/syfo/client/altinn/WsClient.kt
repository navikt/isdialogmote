package no.nav.syfo.client.altinn

import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean

fun createPort(serviceUrl: String): ICorrespondenceAgencyExternalBasic {
    return JaxWsProxyFactoryBean().apply {
        address = serviceUrl
        serviceClass = ICorrespondenceAgencyExternalBasic::class.java
    }.create(ICorrespondenceAgencyExternalBasic::class.java)
}
