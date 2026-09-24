package app.tinyui.updates

/** A `<version>/manifest.json` as `tinyui bundle` wrote it, with the `current.json` signature Node's crypto produced for these exact bytes. */
object Fixture {
    const val PUBLIC_KEY = "BNWxlTQYds/K39D4GXR39VXCy3lkqLnYC5sPcEQzXeKOz/Uh35/M6gdJng3aOLdtNiftlEb4j3oaDCPiMYVBSMY="
    const val OTHER_PUBLIC_KEY = "BMxPERlI+YlzaKiP61LhtPrGll7Qv3U0jzrb9OlKgXO3GxfxPdV7G+QQxJ0Lr5GWpxfPewUwyWn3URiJ/+/Eh3k="
    const val SIGNATURE = "MEUCICYDN1aFFq/VMY2n9MMK05I0oYgNp17QHTAprnGQHYL0AiEA9vR1+GdENGNaKxW57GKSje0P6cNsK0818PDB4bVieiA="
    const val ENGINE = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    const val TINYUI = "0.5.0"
    const val VERSION = "20260922T100000Z-abcdef1"
    val MANIFEST = """
        {
          "runtime": [
            "tinyui-core",
            "tinyui-native"
          ],
          "pages": [
            "shop/home"
          ],
          "files": {
            "tinyui-core": "runtime/core",
            "tinyui-native": "runtime/native",
            "shop/home": "pages/home"
          },
          "buildIds": {
            "tinyui-core": "aaaaaaaa",
            "tinyui-native": "bbbbbbbb",
            "shop/home": "cccccccc"
          },
          "name": "shop",
          "publicKey": "BNWxlTQYds/K39D4GXR39VXCy3lkqLnYC5sPcEQzXeKOz/Uh35/M6gdJng3aOLdtNiftlEb4j3oaDCPiMYVBSMY=",
          "version": "20260922T100000Z-abcdef1",
          "createdAt": "2026-09-22T10:00:00Z",
          "engine": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
          "tinyui": "0.5.0",
          "hashes": {
            "tinyui-core": "d63180d8a590da5a74a6a62a700b4f1c1ccf8eb1286e0509e6b30fc72d92697b",
            "tinyui-native": "8a65da82b504e482deb6e49382cd8b2abca80b6c6ad67ff82aa642f76c8fcc4d",
            "shop/home": "6ca9202ab8e55afbd5f0e68113ef733655c190b1531cc7e1fc17ea3bb8d32230"
          },
          "hostVersion": "1"
        }
    """.trimIndent() + "\n"

    /** The bytes whose sha256 the fixture manifest lists. */
    val FILES = mapOf("runtime/core.bin" to "CORE2", "runtime/native.bin" to "NATIVE2", "pages/home.bin" to "HOME2")
}
