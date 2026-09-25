plugins {
    id("tinyui.kmp.library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.binaryCompatibilityValidator)
}

// TinyUI.version: the tinyui-core version, which publish.yml requires to equal the tag; a composite
// build in a host thus reports the same version as the Maven artifact, so host snapshots agree
val generateVersion by tasks.registering {
    val coreManifest = rootProject.layout.projectDirectory.file("packages/core/package.json")
    val version = providers.fileContents(coreManifest).asText.map { Regex("\"version\":\\s*\"([^\"]+)\"").find(it)!!.groupValues[1] }
    val out = layout.buildDirectory.dir("generated/tinyui-version")
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        out.get().file("app/tinyui/Version.kt").asFile.apply { parentFile.mkdirs() }
            .writeText("package app.tinyui\n\ninternal const val VERSION = \"${version.get()}\"\n")
    }
}

kotlin {
    android {
        namespace = "app.tinyui"
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateVersion)
        }
        commonMain.dependencies {
            api(libs.quickjs.kmp)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.okio)
            api(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.okio.fakefilesystem)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(artifactId = "tinyui")

    pom {
        name.set("TinyUI")
        description.set("JS-driven declarative UI for Compose Multiplatform, powered by QuickJS.")
        url.set("https://github.com/tiny-ui/tinyui")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("HarlonWang")
                name.set("Harlon Wang")
            }
        }
        scm {
            url.set("https://github.com/tiny-ui/tinyui")
            connection.set("scm:git:https://github.com/tiny-ui/tinyui.git")
        }
    }
}

apiValidation {
    klib { enabled = true }
}

// Android host test 要加载宿主编译的 libquickjs_kmp：composite 时取 quickjs-kmp 的 buildNativeHostJni 产物，
// 否则读 TINYUI_QUICKJS_HOST_JNI（CI 从 quickjs-kmp 源码现编，见 .github/workflows/build.yml）
val quickjsKmpDir = (gradle as ExtensionAware).extra.properties["quickjs-kmp.dir"] as File?
val hostJniDir: Provider<String> = providers.environmentVariable("TINYUI_QUICKJS_HOST_JNI")
    .orElse(providers.provider { quickjsKmpDir?.resolve("library/build/native/host-jni/lib")?.absolutePath })
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    if (quickjsKmpDir != null) dependsOn(gradle.includedBuild("quickjs-kmp").task(":library:buildNativeHostJni"))
    hostJniDir.orNull?.let { systemProperty("java.library.path", it) }
    // BundleSmokeTest loads the sample's bytecode when it exists: never a stale one from before a rebuild in the same run
    if (findProject(":sample:shared") != null) mustRunAfter(":sample:shared:buildTinyUIPages")
}
