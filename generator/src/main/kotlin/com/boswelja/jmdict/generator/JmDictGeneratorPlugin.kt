package com.boswelja.jmdict.generator

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.jvm.tasks.Jar
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import kotlin.reflect.KProperty0

internal const val ExtensionName: String = "jmDict"

interface JmDictExtension {

    /**
     * The URL for the full JMDict archive. Defaults to `ftp://ftp.edrdg.org/pub/Nihongo/JMdict.gz`.
     */
    val jmDictUrl: Property<URI>

    /**
     * The package name for the generated sources.
     */
    val packageName: Property<String>

    /**
     * Whether additional metadata, such as entry count, date information, and changelog should be
     * captured. When set to `true`, a `data class Metadata` is generated alongside jmdict content.
     * Defaults to `true`.
     */
    val generateMetadata: Property<Boolean>
}

class JmDictGeneratorPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Create the Gradle extension for configuration
        val config = target.extensions.create(
            ExtensionName,
            JmDictExtension::class.java
        )
        config.generateMetadata.convention(true)
        config.jmDictUrl.convention(URI("ftp://ftp.edrdg.org/pub/Nihongo/JMdict.gz"))

        val targetGeneratedSourcesDir = target.layout.buildDirectory.dir("generated/jmdict/kotlin")
        val targetJmDictResDir = target.layout.buildDirectory.dir("generated/jmdict/composeResources/")
        val jmDictFile = target.layout.buildDirectory.file("resources/jmdict/jmdict.xml")
        val relNotesFile = target.layout.buildDirectory.file("resources/jmdict/changelog.xml")
        val dtdFile = target.layout.buildDirectory.file("resources/jmdict/dtd.xml")
        val metadataFile = target.layout.buildDirectory.file("resources/jmdict/metadata.properties")

        // Register the download task
        val downloadJmDictTask = target.tasks.register(
            "downloadJmDict",
            DownloadJmDictTask::class.java
        ) {
            requireProperty(config::jmDictUrl, "ftp://ftp.edrdg.org/pub/Nihongo/JMdict.gz")

            it.jmDictUrl.set(config.jmDictUrl)
            it.outputJmDict.set(jmDictFile)
            it.outputDtd.set(dtdFile)
            it.outputReleaseNotes.set(relNotesFile)
            it.outputMetadata.set(metadataFile)
        }

        // Register the generation tasks
        val generateDataClassTask = target.tasks.register(
            "generateJmDictDataClasses",
            GenerateDataClassesTask::class.java
        ) {
            requireProperty(config::packageName, "\"com.my.package\"")

            it.dependsOn(downloadJmDictTask)

            it.outputDirectory.set(targetGeneratedSourcesDir)
            it.packageName.set(config.packageName)
            it.dtdFile.set(downloadJmDictTask.get().outputDtd)
        }
        val generateMetadataTask = target.tasks.register(
            "generateJmDictMetadataObject",
            GenerateMetadataObjectTask::class.java
        ) {
            requireProperty(config::packageName, "\"com.my.package\"")

            it.dependsOn(downloadJmDictTask)

            it.outputDirectory.set(targetGeneratedSourcesDir)
            it.packageName.set(config.packageName)
            it.metadataFile.set(downloadJmDictTask.get().outputMetadata)
        }

        // Configure Compose resources
        target.extensions.findByType(ComposeExtension::class.java)?.extensions?.findByType(ResourcesExtension::class.java)?.apply {
            val copyResourcesTask = target.tasks.register(
                "copyJmDictResource",
                CopyComposeResourcesTask::class.java
            ) {
                it.dependsOn(downloadJmDictTask)
                it.jmDictFile.set(jmDictFile)
                it.outputDirectory.set(targetJmDictResDir)
            }
            customDirectory(
                sourceSetName = "commonMain",
                directoryProvider = copyResourcesTask.map { it.outputDirectory.get() }
            )
        }

        // Configure KMP projects
        target.extensions.findByType(KotlinMultiplatformExtension::class.java)?.apply {
            // Add generation task as a dependency for build tasks
            target.tasks.withType(KotlinCompile::class.java).configureEach {
                if (config.generateMetadata.get()) {
                    it.dependsOn(generateMetadataTask)
                }
                it.dependsOn(generateDataClassTask)
            }

            // Add generation task as a dependency for source jar tasks
            target.tasks.withType(Jar::class.java).configureEach {
                if (it.archiveClassifier.get() == "sources") {
                    if (config.generateMetadata.get()) {
                        it.dependsOn(generateMetadataTask)
                    }
                    it.dependsOn(generateDataClassTask)
                }
            }

            // Add the generated source dir to the common source set
            sourceSets.commonMain.configure {
                it.kotlin.srcDir(targetGeneratedSourcesDir)
            }
        }
    }
}

private fun requireProperty(property: KProperty0<Property<*>>, exampleValue: String) {
    require(property.get().isPresent) {
        """$ExtensionName.${property.name} must be specified.
               |$ExtensionName {
               |    ${property.name} = $exampleValue
               |}""".trimMargin()
    }
}
