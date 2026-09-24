package app.tinyui.updates

/** A `<version>/manifest.json` as `tinyui bundle` wrote it, with the `current.json` signature Node's crypto produced for these exact bytes. */
object Fixture {
    const val PUBLIC_KEY = "BEbccwd4v7RnidrsHeZ6qqJF+yNy4DTEe/JHkHL470V74M9sYGxpekukLlpXv2qrru7ptyfKS1c7eC26sn7wKdI="
    const val OTHER_PUBLIC_KEY = "BMxPERlI+YlzaKiP61LhtPrGll7Qv3U0jzrb9OlKgXO3GxfxPdV7G+QQxJ0Lr5GWpxfPewUwyWn3URiJ/+/Eh3k="
    const val SIGNATURE = "MEUCIQCFnOw6pTRT9OYOsp/tdMzCD9DB8SnFmjop2KssJl0QEwIgXr0EWLdYlxagsjCVFwz0GTP5UZBlI0U1pGcQMWzOjuw="
    const val ENGINE = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    const val TINYUI = "0.5.0"
    const val VERSION = "20260922T100000Z-abcdef1"
    val MANIFEST = """
        {
          "pages": [
            "shop/home"
          ],
          "files": {
            "shop/home": "pages/home"
          },
          "buildIds": {
            "shop/home": "cccccccc"
          },
          "name": "shop",
          "publicKey": "BEbccwd4v7RnidrsHeZ6qqJF+yNy4DTEe/JHkHL470V74M9sYGxpekukLlpXv2qrru7ptyfKS1c7eC26sn7wKdI=",
          "version": "20260922T100000Z-abcdef1",
          "createdAt": "2026-09-22T10:00:00Z",
          "engine": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
          "tinyui": "0.5.0",
          "hashes": {
            "shop/home": "6ca9202ab8e55afbd5f0e68113ef733655c190b1531cc7e1fc17ea3bb8d32230"
          },
          "hostVersion": "1"
        }
    """.trimIndent() + "\n"

    /** The bytes whose sha256 the fixture manifest lists. */
    val FILES = mapOf("pages/home.bin" to "HOME2")
}
