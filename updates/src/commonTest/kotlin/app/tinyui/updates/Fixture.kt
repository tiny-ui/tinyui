package app.tinyui.updates

/** A `<version>/manifest.json` as `tinyui bundle` wrote it, with the `current.json` signature Node's crypto produced for these exact bytes. */
object Fixture {
    const val PUBLIC_KEY = "BC5lpOHvNqDvlsPmE+3KI2Lbr8fEh1U06S+2op5TSG5jjHvRdzk0DeXhMwsidC+i3V7YyVpwu9+qK/wUqs3hMnE="
    const val OTHER_PUBLIC_KEY = "BMxPERlI+YlzaKiP61LhtPrGll7Qv3U0jzrb9OlKgXO3GxfxPdV7G+QQxJ0Lr5GWpxfPewUwyWn3URiJ/+/Eh3k="
    const val SIGNATURE = "MEQCIHyi+3Gh1Ocj6vFfGCXLIqB+uHBW9MUGpl9j3ME58IigAiA/G/y10Jb6ytsjG4aDb1clS4mdJBBeyv/f0fzrZ4FVbw=="
    const val ENGINE = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
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
          "publicKey": "BC5lpOHvNqDvlsPmE+3KI2Lbr8fEh1U06S+2op5TSG5jjHvRdzk0DeXhMwsidC+i3V7YyVpwu9+qK/wUqs3hMnE=",
          "version": "20260922T100000Z-abcdef1",
          "createdAt": "2026-09-22T10:00:00Z",
          "engine": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
          "protocol": 1,
          "hashes": {
            "tinyui-core": "d63180d8a590da5a74a6a62a700b4f1c1ccf8eb1286e0509e6b30fc72d92697b",
            "tinyui-native": "8a65da82b504e482deb6e49382cd8b2abca80b6c6ad67ff82aa642f76c8fcc4d",
            "shop/home": "6ca9202ab8e55afbd5f0e68113ef733655c190b1531cc7e1fc17ea3bb8d32230"
          },
          "runtimeVersion": "1"
        }
    """.trimIndent() + "\n"

    /** The bytes whose sha256 the fixture manifest lists. */
    val FILES = mapOf("runtime/core.bin" to "CORE2", "runtime/native.bin" to "NATIVE2", "pages/home.bin" to "HOME2")
}
