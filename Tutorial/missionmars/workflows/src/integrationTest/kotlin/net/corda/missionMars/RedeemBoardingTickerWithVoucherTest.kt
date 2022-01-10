package net.corda.missionMars

import com.google.gson.GsonBuilder
import kong.unirest.json.JSONArray
import com.google.gson.JsonElement
import kong.unirest.HttpResponse
import kong.unirest.JsonNode
import kong.unirest.Unirest
import kong.unirest.json.JSONObject
import net.corda.missionMars.flows.CreateAndIssueMarsVoucherInitiator
import net.corda.missionMars.flows.CreateBoardingTicketInitiator
import net.corda.missionMars.flows.RedeemBoardingTicketWithVoucherInitiator
import net.corda.missionMars.flows.TemplateFlow
import net.corda.test.dev.network.Credentials
import net.corda.test.dev.network.TestNetwork
import net.corda.test.dev.network.withFlow
import net.corda.test.dev.network.x500Name
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class RedeemBoardingTickerWithVoucherTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            TestNetwork.forNetwork("missionmars-network").verify {
                hasNode("PartyA").withFlow<TemplateFlow>()
                hasNode("PartyB").withFlow<TemplateFlow>()
            }
        }
    }

    @Test
    fun `Redeem MarsVoucher Test`(){
        TestNetwork.forNetwork("missionmars-network").use {

            //Get nodes
            val partyB = getNode("PartyB")

            //Create and Issue MarsVoucher
            getNode("PartyA").httpRpc(Credentials("angelenos","password")){
                val clientId = "Launch Pad 1" + LocalDateTime.now()
                val flowId = with(startFlow(
                        flowName = CreateAndIssueMarsVoucherInitiator::class.java.name,
                        clientId = clientId,
                        parametersInJson = CreateAndIssueMarsVoucherParams(
                                voucherDesc = "Space Shuttle 324",
                                holder = partyB.x500Name.toString(),
                        )
                )){
                    Assertions.assertThat(status).isEqualTo(HttpStatus.SC_OK)
                    Assertions.assertThat(body.`object`.get("clientId")).isEqualTo(clientId)
                    val flowId = body.`object`.get("flowId") as JSONObject
                    Assertions.assertThat(flowId).isNotNull
                    flowId.get("uuid") as String
                }
                eventually {
                    with(retrieveOutcome(flowId)) {
                        Assertions.assertThat(status).isEqualTo(HttpStatus.SC_OK)
                        Assertions.assertThat(body.`object`.get("status")).isEqualTo("COMPLETED")
                    }
                }
                with(retrieveOutcome(flowId)){
                    val resultString = body.`object`.get("resultJson") as String
                    println("--------------------------------")
                    println("Create and Issue Mars Voucher Result: ")
                    println(resultString)
                    println("--------------------------------")
                }

                //Create Boarding Ticket
                val clientId2 = "Launch Pad 2" + LocalDateTime.now()
                val daysUntilLaunch = 10
                val flowId2 = with(startFlow(
                        flowName = CreateBoardingTicketInitiator::class.java.name,
                        clientId = clientId2,
                        parametersInJson = CreateBoardingTicketParams(
                                ticketDescription = "Space Shuttle 323 - 16C",
                                daysUntilLaunch = daysUntilLaunch.toString(),
                        )
                )){
                    Assertions.assertThat(status).isEqualTo(HttpStatus.SC_OK)
                    Assertions.assertThat(body.`object`.get("clientId")).isEqualTo(clientId2)
                    val flowId2 = body.`object`.get("flowId") as JSONObject
                    Assertions.assertThat(flowId).isNotNull
                    flowId2.get("uuid") as String
                }
                eventually {
                    with(retrieveOutcome(flowId2)) {
                        Assertions.assertThat(status).isEqualTo(HttpStatus.SC_OK)
                        Assertions.assertThat(body.`object`.get("status")).isEqualTo("COMPLETED")
                    }
                }
                with(retrieveOutcome(flowId2)){
                    val resultString = body.`object`.get("resultJson") as String
                    println("--------------------------------")
                    println("Create Boarding Ticket Result: ")
                    println(resultString)
                    println("--------------------------------")
                }

                //Get the MarsVoucherID
                var voucherID: String
                with(retrieveOutcome(flowId)){
                    val resultString = body.`object`.get("resultJson") as String
                    val resultJSON = JSONObject(resultString)
                    val outputObjectArray = JSONObject((resultJSON.get("outputStates") as JSONArray)[0] as String)
                    voucherID = outputObjectArray.get("linearId") as String
                }

                //Redeem the Voucher
                val clientId3 = "Launch Pad 3"+ LocalDateTime.now()
                val flowId3 = with(startFlow(
                        flowName = RedeemBoardingTicketWithVoucherInitiator::class.java.name,
                        clientId = clientId3,
                        parametersInJson = RedeemTicketParams(
                                voucherID = voucherID,
                                holder = partyB.x500Name.toString(),
                        )
                )){
                    Assertions.assertThat(status).isEqualTo(HttpStatus.SC_OK)
                    Assertions.assertThat(body.`object`.get("clientId")).isEqualTo(clientId3)
                    val flowId3 = body.`object`.get("flowId") as JSONObject
                    Assertions.assertThat(flowId).isNotNull
                    flowId3.get("uuid") as String
                }
                eventually {
                    with(retrieveOutcome(flowId3)) {
                        Assertions.assertThat(status).isEqualTo(HttpStatus.SC_OK)
                        Assertions.assertThat(body.`object`.get("status")).isEqualTo("COMPLETED")
                    }
                }
                with(retrieveOutcome(flowId3)){
                    val resultString = body.`object`.get("resultJson") as String
                    println("--------------------------------")
                    println("Redeem Flow Result: ")
                    println(resultString)
                    println("--------------------------------")
                }

            }
        }
    }
    //helper method.
    private fun CreateAndIssueMarsVoucherParams(voucherDesc: String, holder: String): String {
        return GsonBuilder()
                .create()
                .toJson(mapOf("voucherDesc" to voucherDesc, "holder" to holder))
    }

    //helper method.
    private fun CreateBoardingTicketParams(ticketDescription: String, daysUntilLaunch: String): String {
        return GsonBuilder()
                .create()
                .toJson(mapOf("ticketDescription" to ticketDescription, "daysUntilLaunch" to daysUntilLaunch))
    }

    //helper method.
    private fun RedeemTicketParams(voucherID: String, holder: String): String {
        return GsonBuilder()
                .create()
                .toJson(mapOf("voucherID" to voucherID, "holder" to holder))
    }




    private fun startFlow(
            flowName: String,
            clientId: String = "client-${UUID.randomUUID()}",
            parametersInJson: String
    ): HttpResponse<JsonNode> {
        val body = mapOf(
                "rpcStartFlowRequest" to
                        mapOf(
                                "flowName" to flowName,
                                "clientId" to clientId,
                                "parameters" to mapOf("parametersInJson" to parametersInJson)
                        )
        )
        val request = Unirest.post("flowstarter/startflow")
                .header("Content-Type", "application/json")
                .body(body)

        return request.asJson()
    }

    private fun retrieveOutcome(flowId: String): HttpResponse<JsonNode> {
        val request = Unirest.get("flowstarter/flowoutcome/$flowId").header("Content-Type", "application/json")
        return request.asJson()
    }

    private inline fun <R> eventually(
            duration: Duration = Duration.ofSeconds(5),
            waitBetween: Duration = Duration.ofMillis(100),
            waitBefore: Duration = waitBetween,
            test: () -> R
    ): R {
        val end = System.nanoTime() + duration.toNanos()
        var times = 0
        var lastFailure: AssertionError? = null

        if (!waitBefore.isZero) Thread.sleep(waitBefore.toMillis())

        while (System.nanoTime() < end) {
            try {
                return test()
            } catch (e: AssertionError) {
                if (!waitBetween.isZero) Thread.sleep(waitBetween.toMillis())
                lastFailure = e
            }
            times++
        }

        throw AssertionError("Test failed with \"${lastFailure?.message}\" after $duration; attempted $times times")
    }

}