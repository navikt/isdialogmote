package no.nav.syfo.application.mq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import com.ibm.msg.client.wmq.compat.base.internal.MQC
import no.nav.syfo.application.Environment
import no.nav.syfo.varsel.MotedeltakerVarselType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.Session

private const val UTF_8_WITH_PUA = 1208
private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.application.mq")

interface MQSenderInterface {
    fun sendMQMessage(varseltype: MotedeltakerVarselType, payload: String)
}

class MQSender(private val env: Environment) : MQSenderInterface {
    override fun sendMQMessage(varseltype: MotedeltakerVarselType, payload: String) {
        val queueName = getQueueName(env)
        if (env.mqSendingEnabled) {
            log.info("Sending message of type $varseltype to $queueName where payload is\n $payload")
            connectionFactory(env).createConnection(env.mqUsername, env.mqPassword).use { connection ->
                connection.createSession(false, Session.AUTO_ACKNOWLEDGE).use { session ->
                    val destination = session.createQueue(queueName)
                    val message = session.createTextMessage(payload)
                    session.createProducer(destination).send(message)
                }
            }
        } else {
            log.info("MQ-message sending disabled, would have sent message of type $varseltype to $queueName")
        }
    }

    private fun getQueueName(env: Environment): String {
        return env.mqTredjepartsVarselQueue
    }

    private fun connectionFactory(config: Environment) = MQConnectionFactory().apply {
        hostName = config.mqHostname
        port = config.mqPort
        queueManager = config.mqQueueManager
        transportType = WMQConstants.WMQ_CM_CLIENT
        channel = config.mqChannelName
        ccsid = UTF_8_WITH_PUA
        setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQC.MQENC_NATIVE)
        setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, UTF_8_WITH_PUA)
    }
}
