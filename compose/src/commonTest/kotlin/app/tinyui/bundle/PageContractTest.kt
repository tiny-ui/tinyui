package app.tinyui.bundle

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A page's contract as the CLI writes it, checked against what a caller sends (docs/build-chain.md §10). */
class PageContractTest {
    private val contract = assertNotNull(
        PageContract.parse(
            Json.parseToJsonElement(
                """
                {
                  "params": {
                    "type": "object",
                    "properties": {
                      "scene": { "enum": ["login", "switch"] },
                      "token": { "type": "string" },
                      "options": { "type": "array", "items": { "type": "object", "properties": { "method": { "enum": ["email", "sms"] }, "target": { "type": "string" } }, "required": ["method", "target"] } },
                      "pin": { "type": "boolean" },
                      "retries": { "type": "number" },
                      "note": { "anyOf": [{ "type": "null" }, { "type": "string" }] },
                      "extra": { "type": "object", "additionalProperties": { "type": "string" } }
                    },
                    "required": ["scene", "token", "options", "pin", "note"]
                  },
                  "result": {
                    "anyOf": [
                      { "type": "object", "properties": { "status": { "const": "expired" } }, "required": ["status"] },
                      { "type": "object", "properties": { "status": { "const": "passed" }, "token": { "type": "string" } }, "required": ["status", "token"] }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        ),
    )

    private val good = """{"scene":"login","token":"t","options":[{"method":"sms","target":"138"}],"pin":false,"note":null}"""

    @Test
    fun propsThatFitHaveNoProblems() {
        assertEquals(emptyList(), contract.problems(good))
        assertEquals(emptyList(), contract.problems("""{"scene":"switch","token":"t","options":[],"pin":true,"note":"n","retries":2,"extra":{"a":"b"}}"""))
    }

    @Test
    fun aFieldThePageDoesNotListIsAllowedAndAnAbsentOptionalOneMayBeNull() {
        assertEquals(emptyList(), contract.problems("""{"scene":"login","token":"t","options":[],"pin":false,"note":null,"newer":{"x":1},"retries":null}"""))
    }

    @Test
    fun everyMisfitIsNamedByItsPathAndNeverByItsValue() {
        val problems = contract.problems("""{"scene":"reset","token":42,"options":[{"method":"sms"},{"method":"fax","target":"1"}],"pin":"yes","extra":{"a":1}}""")
        assertEquals(
            listOf(
                "$.scene: expected one of \"login\", \"switch\"",
                "$.token: expected a string",
                "$.options[0].target: missing",
                "$.options[1].method: expected one of \"email\", \"sms\"",
                "$.pin: expected a boolean",
                "$.note: missing",
                "$.extra.a: expected a string",
            ),
            problems,
        )
        assertFalse(problems.any { "42" in it || "fax" in it || "yes" in it }, "props carry tokens: the lines never quote what was sent")
    }

    @Test
    fun aRequiredNullIsNotAMissingField() {
        assertEquals(listOf("$.pin: expected a boolean"), contract.problems("""{"scene":"login","token":"t","options":[],"pin":null,"note":null}"""))
    }

    @Test
    fun whatIsNotAnObjectOrNotJsonIsOneProblem() {
        assertEquals(listOf("$: expected an object"), contract.problems("[]"))
        assertEquals(listOf("$: not JSON"), contract.problems("{"))
    }

    @Test
    fun aUnionReportsAgainstItsClosestShape() {
        assertTrue(contract.hasResult)
        assertEquals(emptyList(), contract.resultProblems("""{"status":"passed","token":"t"}"""))
        assertEquals(emptyList(), contract.resultProblems("""{"status":"expired"}"""))
        assertEquals(listOf("$.token: missing"), contract.resultProblems("""{"status":"passed"}"""), "told apart by status: the passed shape is the one meant")
    }

    @Test
    fun aKeywordFromANewerCliPasses() {
        val newer = assertNotNull(PageContract.parse(Json.parseToJsonElement("""{"params":{"type":"object","properties":{"at":{"format":"date"}},"required":["at"]}}""")))
        assertEquals(emptyList(), newer.problems("""{"at":"2026-01-01"}"""))
        assertFalse(newer.hasResult)
        assertEquals(emptyList(), newer.resultProblems("42"), "no declared result, nothing to check")
        assertNull(PageContract.parse(Json.parseToJsonElement("""{"result":{}}""")), "not what the build writes")
    }
}
