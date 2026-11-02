package io.github.theeht.legadotidy.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class CliTest {

    @Test
    fun invalidProxyReturnsConfigurationExitCodeWithoutCreatingExport(@TempDir directory: Path) {
        val input = directory.resolve("sources.json")
        Files.writeString(
            input,
            """
            [
              {
                "bookSourceName": "proxy preflight fixture",
                "bookSourceUrl": "https://example.test",
                "searchUrl": "/search",
                "ruleSearch": {
                  "bookList": "div.book",
                  "name": "a@text",
                  "bookUrl": "a@href"
                }
              }
            ]
            """.trimIndent(),
        )
        val output = directory.resolve("out")

        val exitCode = runCli(
            arrayOf(
                "--in", input.toString(),
                "--out", output.toString(),
                "--proxy", "not a proxy",
            ),
        )

        assertEquals(2, exitCode)
        assertFalse(Files.exists(output))
    }
}
