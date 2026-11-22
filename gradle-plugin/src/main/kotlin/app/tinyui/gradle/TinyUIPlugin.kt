package app.tinyui.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import java.util.Properties

/**
 * `app.tinyui`: a host's embedded packages are an input of its build, not bytes in its repository (docs/updates.md §1.4).
 * Applied to the module that reads them from Compose resources `files/tinyui/<pkg>/`.
 */
class TinyUIPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val tinyui = extensions.create("tinyui", TinyUIExtension::class.java)
        tinyui.updatesUrl.convention("https://updates.tinyui.app")

        val local = Properties().apply {
            providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull?.let { load(it.reader()) }
        }
        val embedded = layout.buildDirectory.dir("tinyui/embedded")
        val records = layout.buildDirectory.dir("tinyui/resolved")
        val resolveAll = tasks.register("resolveTinyUIPackages") {
            group = "tinyui"
            description = "Resolves every embedded TinyUI package"
        }
        val resolveTasks = mutableMapOf<String, TaskProvider<out Task>>()

        tinyui.packages.all {
            val pkg = name
            if (!Regex("^[a-z0-9-]+$").matches(pkg)) throw GradleException("tinyui: package name must match [a-z0-9-]+, got \"$pkg\"")
            val taskName = "resolveTinyUI" + pkg.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
            val out = embedded.map { it.dir(pkg) }
            val recordFile = records.map { it.file("$pkg.json") }
            // page sources next to the host win over the channel: -Ptinyui.<pkg>.dir, or the same key in local.properties
            val explicitDir = providers.gradleProperty("tinyui.$pkg.dir").orNull
            val sourceDir = (explicitDir ?: local.getProperty("tinyui.$pkg.dir"))?.let { rootProject.file(it) }
            val spec = this
            val task = if (sourceDir != null) {
                if (!File(sourceDir, "tinyui.config.json").isFile) throw GradleException("tinyui.$pkg.dir: $sourceDir is not a TinyUI page project (no tinyui.config.json)")
                tasks.register(taskName, EmbedTinyUIPackage::class.java) {
                    group = "tinyui"
                    description = "Builds the embedded package $pkg from $sourceDir"
                    packageName.set(pkg)
                    projectDir.set(sourceDir)
                    // a line left in local.properties must not put a local build into a release: that takes saying so on the command line
                    leftover.set(tinyui.channel.map { explicitDir == null && it == "production" }.orElse(false))
                    sources.from(fileTree(sourceDir) { exclude("node_modules/**", "dist/**", ".git/**", ".idea/**", ".local/**") })
                    // the CLI and the runtime packages it compiles against are inputs too, by version rather than by file
                    sources.from(fileTree(sourceDir) { include("node_modules/tinyui-*/package.json") })
                    outputDir.set(out)
                    record.set(recordFile)
                }
            } else {
                tasks.register(taskName, FetchTinyUIPackage::class.java) {
                    group = "tinyui"
                    description = "Resolves the embedded package $pkg from its channel"
                    updatesUrl.set(tinyui.updatesUrl)
                    app.set(tinyui.app)
                    channel.set(tinyui.channel)
                    hostVersion.set(tinyui.hostVersion)
                    packageName.set(pkg)
                    publicKey.set(spec.publicKey)
                    offline.set(gradle.startParameter.isOffline)
                    outputDir.set(out)
                    record.set(recordFile)
                }
            }
            resolveTasks[pkg] = task
            resolveAll.configure { dependsOn(task) }
        }

        // Compose resources take one directory per source set: the module's own resources and the packages, merged
        val resources = layout.buildDirectory.dir("tinyui/compose-resources")
        val merge = tasks.register("mergeTinyUIComposeResources", Sync::class.java) {
            group = "tinyui"
            description = "The module's Compose resources plus its embedded TinyUI packages under files/tinyui/"
            // a package directory left in the source tree would shadow what the build resolved: fail rather than pick one
            duplicatesStrategy = DuplicatesStrategy.FAIL
            from(layout.projectDirectory.dir("src/commonMain/composeResources"))
            into(resources)
        }
        tinyui.packages.all {
            val pkg = name
            merge.configure {
                from(resolveTasks.getValue(pkg).map { task ->
                    when (task) {
                        is EmbedTinyUIPackage -> task.outputDir
                        is FetchTinyUIPackage -> task.outputDir
                        else -> error("unexpected task ${task.path}")
                    }
                }) { into("files/tinyui/$pkg") }
            }
        }
        tinyui.embeddedDir = layout.dir(merge.map { File(it.destinationDir, "files/tinyui") })

        pluginManager.withPlugin("org.jetbrains.compose") {
            val compose = extensions.getByType(ComposeExtension::class.java)
            compose.extensions.getByType(ResourcesExtension::class.java)
                .customDirectory("commonMain", layout.dir(merge.map { it.destinationDir }))
            // Compose's assets copy never deletes what an earlier build produced: a removed page would stay in the APK
            tasks.matching { it.name.startsWith("copy") && it.name.endsWith("ComposeResourcesToAndroidAssets") }.configureEach {
                doFirst { outputs.files.forEach { it.deleteRecursively() } }
            }
        }

        val generate = tasks.register("generateTinyUIBuild", GenerateTinyUIBuild::class.java) {
            group = "tinyui"
            description = "Generates TinyUIBuild (host version, channel, package names)"
            generatedPackage.set(tinyui.generatedPackage)
            hostVersion.set(tinyui.hostVersion)
            channel.set(tinyui.channel.orElse(""))
            packageNames.set(provider { tinyui.packages.names.toList() })
            outputDir.set(layout.buildDirectory.dir("generated/tinyui/commonMain/kotlin"))
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.getByType(KotlinMultiplatformExtension::class.java)
                .sourceSets.getByName("commonMain").kotlin.srcDir(generate.map { it.outputDir })
        }
    }
}
