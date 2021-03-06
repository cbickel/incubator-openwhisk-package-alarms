/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package system.packages

import com.jayway.restassured.RestAssured
import com.jayway.restassured.config.SSLConfig
import com.jayway.restassured.http.ContentType
import common.TestUtils.FORBIDDEN
import common.{Wsk, WskProps}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import spray.json._

@RunWith(classOf[JUnitRunner])
class AlarmsFeedWebTests
    extends FlatSpec
    with BeforeAndAfter
    with Matchers {

    val wskprops = WskProps()

    val webAction = "/whisk.system/alarmsWeb/alarmWebAction"
    val webActionURL = s"https://${wskprops.apihost}/api/v1/web${webAction}.http"

    val originalParams = JsObject(
        "triggerName" -> JsString("/invalidNamespace/invalidTrigger"),
        "authKey" -> JsString("DoesNotWork")
    )

    behavior of "Alarms web action"

    it should "not be obtainable using the CLI" in {
        val wsk = new Wsk()
        implicit val wp = wskprops

        wsk.action.get(webAction, FORBIDDEN)
    }

    it should "reject put of a trigger due to missing triggerName argument" in {
        val params = JsObject(originalParams.fields - "triggerName")

        makePutCallWithExpectedResult(params, JsObject("error" -> JsString("no trigger name parameter was provided")), 400)
    }

    it should "reject put of a trigger due to missing cron argument" in {
        val params = JsObject(originalParams.fields - "cron")

        makePutCallWithExpectedResult(params, JsObject("error" -> JsString("alarms trigger feed is missing the cron parameter")), 400)
    }

    it should "reject put of a trigger due to invalid cron argument" in {
        val params = JsObject(originalParams.fields + ("cron" -> JsString("***")))

        makePutCallWithExpectedResult(params, JsObject("error" -> JsString("cron pattern '***' is not valid")), 400)
    }

    it should "reject put of a trigger when authentication fails" in {
        val params = JsObject(originalParams.fields + ("cron" -> JsString("* * * * *")))
        makePutCallWithExpectedResult(params, JsObject("error" -> JsString("Trigger authentication request failed.")), 401)
    }

    it should "reject delete of a trigger due to missing triggerName argument" in {
        val params = JsObject(originalParams.fields - "triggerName")

        makeDeleteCallWithExpectedResult(params, JsObject("error" -> JsString("no trigger name parameter was provided")), 400)
    }

    it should "reject delete of a trigger when authentication fails" in {
        makeDeleteCallWithExpectedResult(originalParams, JsObject("error" -> JsString("Trigger authentication request failed.")), 401)
    }

    def makePutCallWithExpectedResult(params: JsObject, expectedResult: JsObject, expectedCode: Int) = {
        val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
                .body(params.toString())
                .put(webActionURL)
        assert(response.statusCode() == expectedCode)
        response.body.asString.parseJson.asJsObject shouldBe expectedResult
    }

    def makeDeleteCallWithExpectedResult(params: JsObject, expectedResult: JsObject, expectedCode: Int) = {
        val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
                .body(params.toString())
                .delete(webActionURL)
        assert(response.statusCode() == expectedCode)
        response.body.asString.parseJson.asJsObject shouldBe expectedResult
    }

}
