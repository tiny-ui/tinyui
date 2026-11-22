package app.tinyui.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.inject.Inject

/** What a build embedded for one package: the only record of it, the host repository holds none (docs/updates.md §1.4). */
internal fun writeRecord(file: File, fields: Map<String, String?>) {
    file.parentFile.mkdirs()
    file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(fields.filterValues { it != null })) + "\n")
}

/** Resolves one package from its channel. Asks the channel on every build: what it points at is not something Gradle can track. */
@UntrackedTask(because = "the channel's pointer moves without anything in the build changing")
abstract class FetchTinyUIPackage : DefaultTask() {
    @get:Input abstract val updatesUrl: Property<String>
    @get:Input abstract val app: Property<String>
    @get:Input abstract val channel: Property<String>
    @get:Input abstract val hostVersion: Property<String>
    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val publicKey: Property<String>
    @get:Input abstract val offline: Property<Boolean>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:OutputFile abstract val record: RegularFileProperty

    @TaskAction
    fun resolve() {
        val url = updatesUrl.get().trimEnd('/')
        if (!url.startsWith("https://") && !Regex("^http://(localhost|127\\.0\\.0\\.1)(:|/|$)").containsMatchIn(url)) {
            throw GradleException("tinyui.updatesUrl must be https (http only for localhost), got $url")
        }
        val pkg = packageName.get()
        val base = "$url/${app.get()}/${channel.get()}"
        val fetch = if (offline.get()) Fetch { throw IOException("Gradle is offline") } else httpFetch()
        val resolved = try {
            Delivery(fetch).resolve(base, pkg, hostVersion.get(), publicKey.get(), strict = channel.get() == "production", out = outputDir.get().asFile)
        } catch (e: ResolveException) {
            throw GradleException("TinyUI: ${e.message}")
        }
        resolved.warning?.let { logger.warn("TinyUI: $it") }
        logger.lifecycle("TinyUI: $pkg ${resolved.version} (${channel.get()}, host version ${resolved.hostVersion})" + if (resolved.changed) "" else ", unchanged")
        writeRecord(
            record.get().asFile,
            mapOf("package" to pkg, "version" to resolved.version, "createdAt" to resolved.createdAt, "source" to "channel", "channel" to channel.get(), "hostVersion" to resolved.hostVersion, "from" to base),
        )
    }

    private fun httpFetch(): Fetch {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
        return Fetch { url ->
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build()
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            } catch (e: InterruptedException) {
                throw IOException("interrupted")
            }
            when (response.statusCode()) {
                200 -> response.body()
                404 -> null
                else -> throw IOException("HTTP ${response.statusCode()}")
            }
        }
    }
}

/** Builds one package from its page sources with the page project's own `tinyui embed` (docs/updates.md §1.4). */
abstract class EmbedTinyUIPackage : DefaultTask() {
    @get:Inject abstract val exec: ExecOperations

    @get:Input abstract val packageName: Property<String>

    /** True when the sources come from `local.properties` while the build embeds for `production`. */
    @get:Input abstract val leftover: Property<Boolean>

    /** The JS project; what of it is an input is [sources]. */
    @get:Internal abstract val projectDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:OutputFile abstract val record: RegularFileProperty

    @TaskAction
    fun embed() {
        val root = projectDir.get().asFile
        val pkg = packageName.get()
        if (leftover.get()) {
            throw GradleException("TinyUI: $pkg: local.properties has tinyui.$pkg.dir=$root, and this build embeds for production; remove the line, or pass -Ptinyui.$pkg.dir to embed page sources on purpose")
        }
        val cli = File(root, "node_modules/.bin/tinyui")
        if (!cli.isFile) throw GradleException("TinyUI: $pkg: ${cli.path} does not exist; install the page project's dependencies first (tinyui.$pkg.dir=$root)")
        val out = outputDir.get().asFile
        // `tinyui embed` refuses a directory that holds something else than a package: start from none
        val result = exec.exec {
            workingDir = root
            commandLine(cli.path, "embed", "--out", out.path, "--dist", File(temporaryDir, "dist").path, "--maps")
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) throw GradleException("TinyUI: $pkg: tinyui embed failed in $root (exit ${result.exitValue}); it needs Node on the PATH of the Gradle daemon")
        val manifest = JsonSlurper().parse(File(out, "manifest.json")) as Map<*, *>
        if (manifest["name"] != pkg) throw GradleException("TinyUI: $root builds package ${manifest["name"]}, not $pkg")
        logger.lifecycle("TinyUI: $pkg ${manifest["version"]} (built from $root)")
        writeRecord(
            record.get().asFile,
            mapOf("package" to pkg, "version" to manifest["version"] as String, "createdAt" to manifest["createdAt"] as String, "source" to "sources", "from" to root.path),
        )
    }
}

/** `TinyUIBuild`: the host version and package names the build declared, for the host's Kotlin code. */
abstract class GenerateTinyUIBuild : DefaultTask() {
    @get:Input abstract val generatedPackage: Property<String>
    @get:Input abstract val hostVersion: Property<String>
    @get:Input abstract val packageNames: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val host = hostVersion.get()
        if (!Regex("^[1-9][0-9]*$").matches(host)) throw GradleException("tinyui.hostVersion must be a positive integer, got \"$host\"")
        val pkg = generatedPackage.get()
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        File(dir, pkg.replace('.', '/') + "/TinyUIBuild.kt").apply { parentFile.mkdirs() }.writeText(
            """
            |// Generated by the app.tinyui Gradle plugin from the `tinyui { }` block. Do not edit.
            |package $pkg
            |
            |internal object TinyUIBuild {
            |    /** What this host promises its pages; `Updates(hostVersion = …)` and the host snapshot read it. */
            |    const val HOST_VERSION: String = "$host"
            |
            |    /** The packages this build embeds, each under Compose resources `files/tinyui/<name>/`. */
            |    val PACKAGES: List<String> = listOf(${packageNames.get().joinToString { "\"$it\"" }})
            |}
            |""".trimMargin(),
        )
    }
}
