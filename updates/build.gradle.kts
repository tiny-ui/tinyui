plugins {
    id("tinyui.kmp.library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.binaryCompatibilityValidator)
}

kotlin {
    android {
        namespace = "app.tinyui.updates"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":compose"))
            implementation(libs.quickjs.kmp)
            api(libs.okio)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(artifactId = "tinyui-updates")

    pom {
        name.set("TinyUI Updates")
        description.set("Hot updates for TinyUI packages: verification, storage, selection and rollback (docs/updates.md).")
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
