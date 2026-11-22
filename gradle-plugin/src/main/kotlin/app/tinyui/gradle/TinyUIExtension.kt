package app.tinyui.gradle

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

/** One package the host embeds; its name is the package name (`tinyui.config.json`'s `name`). */
abstract class TinyUIPackageSpec @Inject constructor(private val name: String) : Named {
    override fun getName(): String = name

    /** The package's `publicKey` from its `tinyui.config.json`: what a version resolved from a channel must be signed with. */
    abstract val publicKey: Property<String>
}

/**
 * ```
 * tinyui {
 *     app = "shop"
 *     hostVersion = "3"
 *     channel = providers.gradleProperty("tinyui.channel")
 *     generatedPackage = "com.example.shop.tinyui"
 *     packages { register("login") { publicKey = "BN5p…" } }
 * }
 * ```
 * docs/updates.md §1.4.
 */
abstract class TinyUIExtension {
    /** The updates instance; the hosted one unless set. */
    abstract val updatesUrl: Property<String>

    /** The host's app id on the updates instance. */
    abstract val app: Property<String>

    /** What the host promises its pages (docs/updates.md §4.1). The only place a host writes it: Kotlin reads the generated `TinyUIBuild.HOST_VERSION`. */
    abstract val hostVersion: Property<String>

    /**
     * The channel the App built here checks at run time, and so the one its packages are embedded from; `production` is
     * strict (docs/updates.md §1.4). Kotlin reads the generated `TinyUIBuild.CHANNEL`, so the two are one value.
     */
    abstract val channel: Property<String>

    /** The Kotlin package of the generated `TinyUIBuild`. */
    abstract val generatedPackage: Property<String>

    abstract val packages: NamedDomainObjectContainer<TinyUIPackageSpec>

    /** Where this build's embedded packages are, one directory per package; depends on resolving them. For the host's `PackageCheck` test. */
    lateinit var embeddedDir: Provider<Directory>
        internal set
}
