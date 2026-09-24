plugins {
    id("tinyui.kmp.library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "app.tinyui.sample.shared"
        // AGP 的 KMP 库插件默认不处理 res / assets，Compose resources 在 Android 走 assets，必须打开
        androidResources { enable = true }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":compose"))
            implementation(project(":updates"))
            implementation(libs.okio)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val updateHostSnapshot = providers.gradleProperty("tinyui.updateHostSnapshot")

// JS 侧：pnpm 编三个包 → tinyui build 把每个示例包的页面编成字节码 → 只把 .bin 与清单打成 Compose 资源（运行时随库）
// 两个包演示多包模型（docs/updates.md §0）：各自的 JS 工程、密钥、资源子目录
val jsPackages = mapOf("sample" to rootDir.resolve("sample/js"), "sample-extra" to rootDir.resolve("sample/js-extra"))
val cliOut = layout.buildDirectory.dir("tinyui-cli")
val quickjsKmpDir = (gradle as ExtensionAware).extra.properties["quickjs-kmp.dir"] as File?
val qjsc: Provider<String> = providers.gradleProperty("tinyui.qjsc")
    .orElse(providers.environmentVariable("TINYUI_QJSC"))
    .orElse(providers.provider { quickjsKmpDir?.resolve("library/build/native/host-tools/bin/qjsc-kmp")?.absolutePath })

val buildJsPackages by tasks.registering(Exec::class) {
    group = "build"
    description = "pnpm build for packages/*"
    workingDir = rootDir
    commandLine("pnpm", "run", "build")
    inputs.files(fileTree(rootDir.resolve("packages")) { include("*/src/**", "*/package.json", "*/tsconfig.json") })
    inputs.file(rootDir.resolve("tsconfig.base.json"))
    outputs.dirs(rootDir.resolve("packages/core/dist"), rootDir.resolve("packages/native/dist"), rootDir.resolve("packages/cli/dist"))
}

val buildTinyUIPages by tasks.registering {
    group = "build"
    description = "tinyui build for every sample package"
}
val packageBuilds = jsPackages.map { (pkg, jsRoot) ->
    val name = "buildTinyUIPages" + pkg.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
    tasks.register<Exec>(name) {
        group = "build"
        description = "tinyui build for ${jsRoot.relativeTo(rootDir)}"
        dependsOn(buildJsPackages)
        if (quickjsKmpDir != null) dependsOn(gradle.includedBuild("quickjs-kmp").task(":library:buildHostTools"))
        workingDir = jsRoot
        inputs.dir(jsRoot.resolve("src"))
        inputs.file(jsRoot.resolve("tinyui.config.json"))
        inputs.files(buildJsPackages.map { it.outputs.files })
        inputs.property("qjsc", qjsc.orElse(""))
        val out = cliOut.map { it.dir(pkg) }
        outputs.dir(out)
        val args = mutableListOf("node", rootDir.resolve("packages/cli/dist/bin.js").path, "build", "--root", jsRoot.path, "--out", out.get().asFile.path)
        qjsc.orNull?.let { args += listOf("--qjsc", it) }
        commandLine(args)
    }.also { buildTinyUIPages.configure { dependsOn(it) } }
}

// one resource subdirectory per package (docs/updates.md §3): files/tinyui/<pkg>/
val tinyUIResourcesRoot = layout.buildDirectory.dir("tinyui-resources")
val collectTinyUIResources by tasks.registering(Sync::class) {
    // maps ride along for the debug-only failure screen (docs/build-chain.md); -Ptinyui.maps=false leaves them out
    jsPackages.keys.forEachIndexed { i, pkg ->
        from(packageBuilds[i]) {
            into("files/tinyui/$pkg")
            include("pages/**/*.bin", "manifest.json")
            if (providers.gradleProperty("tinyui.maps").orNull != "false") include("pages/**/*.js.map")
        }
    }
    // the whole root is synced, so a package directory from an earlier layout does not linger
    into(tinyUIResourcesRoot)
}

// Compose's assets copy never deletes what an earlier build produced: a removed page would stay in the APK
tasks.matching { it.name.startsWith("copy") && it.name.endsWith("ComposeResourcesToAndroidAssets") }.configureEach {
    doFirst { outputs.files.forEach { it.deleteRecursively() } }
}

compose.resources {
    packageOfResClass = "app.tinyui.sample.res"
    customDirectory("commonMain", layout.dir(collectTinyUIResources.map { it.destinationDir }))
}

// HostSnapshotTest compares against tinyui-host/ here, -Ptinyui.updateHostSnapshot makes it write instead;
// EmbeddedPackageTest reads the packages this build embeds
tasks.withType<Test>().configureEach {
    inputs.files(layout.projectDirectory.dir("tinyui-host")).withPropertyName("hostSnapshots")
    updateHostSnapshot.orNull?.let { systemProperty("tinyui.updateHostSnapshot", "true") }
    dependsOn(collectTinyUIResources)
    inputs.dir(tinyUIResourcesRoot).withPropertyName("embeddedPackages")
    val embedded = tinyUIResourcesRoot.map { it.dir("files/tinyui").asFile.path }
    doFirst { systemProperty("tinyui.embedded", embedded.get()) }
}
