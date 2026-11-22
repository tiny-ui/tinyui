// The host-side Gradle plugin `app.tinyui` (docs/updates.md §1.4): resolves a host's embedded packages at build time.
// Its own build, so that a host can include it from source next to the library (settings `pluginManagement { includeBuild(…) }`).
plugins {
    `kotlin-dsl`
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

dependencies {
    // only the extension types the plugin wires into; the host brings the plugins themselves
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.compose)
    testImplementation(libs.kotlin.test)
}

// A host runs Gradle on whatever JDK it has; the plugin must not ask for the one that happened to build it.
// (0.14.0-alpha05 was built on 25 and so refused every older JVM.) 17 is what Gradle 9 itself needs.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<JavaCompile>().configureEach { options.release = 17 }
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        // also only the JDK 17 API, when a newer JDK compiles this
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

gradlePlugin {
    plugins {
        create("tinyui") {
            id = "app.tinyui"
            implementationClass = "app.tinyui.gradle.TinyUIPlugin"
            displayName = "TinyUI"
            description = "Resolves the TinyUI packages a host App embeds, from a delivery channel or from page sources."
        }
    }
}

tasks.test { useJUnitPlatform() }

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(artifactId = "tinyui-gradle-plugin")

    pom {
        name.set("TinyUI Gradle Plugin")
        description.set("Resolves the TinyUI packages a host App embeds at build time (docs/updates.md §1.4).")
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
