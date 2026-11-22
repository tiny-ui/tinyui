package app.tinyui.gradle

import groovy.json.JsonSlurper
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Why a package could not be resolved from its channel; the message is what the build prints. */
class ResolveException(message: String) : RuntimeException(message)

/** A GET against the delivery endpoints: the bytes, `null` for 404; anything else throws [IOException]. */
fun interface Fetch {
    fun get(url: String): ByteArray?
}

data class Resolved(
    val version: String,
    /** The host version the package was published for: lower than the host's own when it fell back. */
    val hostVersion: String,
    val createdAt: String,
    /** False when the directory already held this version. */
    val changed: Boolean,
    /** Set when what is embedded is not simply the channel's current version for this host version. */
    val warning: String? = null,
)

/**
 * Resolves the package a channel currently points at into a host's embedded package directory, verified the way a
 * device verifies it (docs/updates.md §1.4, §4.3): signature over the manifest's raw bytes, name / version / host
 * version, every file's sha256. Writes nothing unless all of it checks out.
 */
class Delivery(private val fetch: Fetch) {

    fun resolve(
        /** `<instance>/<app>/<channel>` */
        base: String,
        pkg: String,
        hostVersion: String,
        publicKey: String,
        /** A strict channel is what release builds embed: no falling back to an older host version or to the last result. */
        strict: Boolean,
        out: File,
    ): Resolved {
        val host = hostVersion.toIntOrNull()?.takeIf { it > 0 && it.toString() == hostVersion }
            ?: throw ResolveException("hostVersion must be a positive integer, got \"$hostVersion\"")
        val previous = published(out, pkg, publicKey)
        var candidate = host
        while (true) {
            val root = "$base/$pkg/$candidate"
            val pointerBytes = try {
                fetch.get("$root/current.json")
            } catch (e: IOException) {
                // no network is not a reason to stop a debug build that already resolved this package once
                if (strict || previous == null) throw ResolveException("$pkg: cannot reach $root/current.json (${e.message})" + if (strict) "" else ", and nothing was resolved before")
                return previous.copy(changed = false, warning = "$pkg: cannot reach $root/current.json (${e.message}), keeping ${previous.version}")
            }
            if (pointerBytes == null) {
                // the host version was just raised and the package has not been published for it yet
                if (strict) throw ResolveException("$pkg: nothing is published at $root; publish the package for host version $candidate first")
                if (candidate == 1) throw ResolveException("$pkg: nothing is published under $base/$pkg for host version $host or lower")
                candidate--
                continue
            }
            val resolved = download(root, pointerBytes, pkg, candidate.toString(), publicKey, out, previous)
            return if (candidate == host) resolved else resolved.copy(warning = "$pkg: nothing is published for host version $host yet, embedding ${resolved.version} of host version $candidate")
        }
    }

    private fun download(root: String, pointerBytes: ByteArray, pkg: String, hostVersion: String, publicKey: String, out: File, previous: Resolved?): Resolved {
        val pointer = json(pointerBytes, "$root/current.json")
        val version = pointer["version"] as? String
        if (version == null || !isPathSegment(version)) throw ResolveException("$pkg: $root/current.json: version ${pointer["version"]} is not a path segment")
        val signature = pointer["signature"] as? String ?: throw ResolveException("$pkg: $root/current.json has no signature")
        // a version still rolling out is not yet what every user runs: it must not become the floor of every new install
        val rollout = (pointer["rollout"] as? Number)?.toInt() ?: 100
        if (rollout < 100) throw ResolveException("$pkg: $version is rolling out to $rollout% at $root; a version mid-rollout cannot be embedded, raise it to 100 first")
        if (previous != null && previous.version == version && previous.hostVersion == hostVersion) return previous.copy(changed = false)

        val manifestBytes = fetch.get("$root/$version/manifest.json") ?: throw ResolveException("$pkg: $root/$version/manifest.json is missing")
        // verified before parsed: nothing in a manifest with a wrong signature can be trusted
        if (!verify(publicKey, manifestBytes, signature)) throw ResolveException("$pkg: $version is not signed with the declared key $publicKey")
        val manifest = json(manifestBytes, "$root/$version/manifest.json")
        if (manifest["publicKey"] != publicKey) throw ResolveException("$pkg: $version names the key ${manifest["publicKey"]}, the build declares $publicKey")
        if (manifest["name"] != pkg) throw ResolveException("$pkg: the manifest is for package ${manifest["name"]}")
        if (manifest["version"] != version) throw ResolveException("$pkg: the manifest says version ${manifest["version"]}, the pointer $version")
        if (manifest["hostVersion"] != hostVersion) throw ResolveException("$pkg: the manifest targets host version ${manifest["hostVersion"]}, not $hostVersion")
        val createdAt = manifest["createdAt"] as? String ?: throw ResolveException("$pkg: the manifest has no createdAt")

        val files = LinkedHashMap<String, ByteArray>()
        for ((path, hashKey) in payload(manifest, pkg)) {
            val bytes = fetch.get("$root/$version/$path") ?: throw ResolveException("$pkg: $root/$version/$path is missing")
            val expected = (manifest["hashes"] as? Map<*, *>)?.get(hashKey)
            if (sha256(bytes) != expected) throw ResolveException("$pkg: $path does not match manifest.hashes")
            files[path] = bytes
        }
        // the raw bytes: what the signature covers, and what the library hashes nothing of but reads as is
        files["manifest.json"] = manifestBytes
        replaceDir(out, files)
        return Resolved(version, hostVersion, createdAt, changed = true)
    }

    companion object {
        private val segment = Regex("^[A-Za-z0-9._-]+$")

        fun isPathSegment(value: String): Boolean = segment.matches(value) && value != "." && value != ".."

        /** What [out] holds, when it is a published version of [pkg] signed for [publicKey] and none of its files changed since. */
        fun published(out: File, pkg: String, publicKey: String): Resolved? {
            val manifestFile = File(out, "manifest.json")
            if (!manifestFile.isFile) return null
            return try {
                val manifest = json(manifestFile.readBytes(), manifestFile.path)
                // a package built from page sources has no hostVersion: it is never what a channel resolved
                val hostVersion = manifest["hostVersion"] as? String ?: return null
                if (manifest["name"] != pkg || manifest["publicKey"] != publicKey) return null
                val hashes = manifest["hashes"] as? Map<*, *> ?: return null
                for ((path, hashKey) in payload(manifest, pkg)) {
                    val file = File(out, path)
                    if (!file.isFile || sha256(file.readBytes()) != hashes[hashKey]) return null
                }
                Resolved(manifest["version"] as String, hostVersion, manifest["createdAt"] as String, changed = false)
            } catch (e: RuntimeException) {
                null
            }
        }

        /** Every file of the package besides `manifest.json`: its path inside the package and its key into `hashes` (docs/updates.md §1.1). */
        private fun payload(manifest: Map<*, *>, pkg: String): List<Pair<String, String>> {
            val files = manifest["files"] as? Map<*, *> ?: throw ResolveException("$pkg: the manifest has no files")
            val pages = (manifest["pages"] as? List<*>).orEmpty().map { module ->
                val file = files[module] as? String ?: throw ResolveException("$pkg: the manifest has no file for page $module")
                "$file.jsb" to module as String
            }
            val i18n = ((manifest["i18n"] as? Map<*, *>)?.get("files") as? Map<*, *>).orEmpty().values.map { it as String to it }
            return (pages + i18n).onEach { (path, _) ->
                if (!path.split('/').all(::isPathSegment)) throw ResolveException("$pkg: \"$path\" is not a plain relative path")
            }
        }

        private fun json(bytes: ByteArray, where: String): Map<*, *> = try {
            JsonSlurper().parse(bytes) as? Map<*, *>
        } catch (e: RuntimeException) {
            null
        } ?: throw ResolveException("$where is not a JSON object")

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        // the fixed SubjectPublicKeyInfo header of a P-256 key; the 65-byte uncompressed point follows (docs/updates.md §7)
        private val spkiHeader = "3059301306072a8648ce3d020106082a8648ce3d030107034200".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        /** ECDSA P-256 + SHA-256, DER signature in base64, the key an X9.63 uncompressed point in base64 (docs/updates.md §7). */
        fun verify(publicKey: String, data: ByteArray, signature: String): Boolean {
            val point = try {
                Base64.getDecoder().decode(publicKey)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (point == null || point.size != 65 || point[0] != 0x04.toByte()) throw ResolveException("publicKey is not a P-256 uncompressed point in base64: $publicKey")
            return try {
                val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spkiHeader + point))
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(key)
                    update(data)
                    verify(Base64.getDecoder().decode(signature))
                }
            } catch (e: Exception) {
                false
            }
        }

        /** Makes [out] hold exactly [files]. Assembled beside it and swapped in, so [out] never holds half a package. */
        fun replaceDir(out: File, files: Map<String, ByteArray>) {
            out.parentFile.mkdirs()
            val incoming = Files.createTempDirectory(out.parentFile.toPath(), "${out.name}.incoming-").toFile()
            try {
                for ((path, bytes) in files) {
                    File(incoming, path).apply { parentFile.mkdirs() }.writeBytes(bytes)
                }
                out.deleteRecursively()
                Files.move(incoming.toPath(), out.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally {
                incoming.deleteRecursively()
            }
        }
    }
}
