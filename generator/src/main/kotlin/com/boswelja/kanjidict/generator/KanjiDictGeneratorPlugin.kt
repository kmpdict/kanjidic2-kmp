package com.boswelja.kanjidict.generator

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

internal const val ExtensionName: String = "kanjiDict"

interface KanjiDictExtension {

    /**
     * The URL for the full JMDict archive. Defaults to `https://www.edrdg.org/kanjidic/kanjidic2.xml.gz`.
     */
    val kanjiDictUrl: Property<URI>

    /**
     * The package name for the generated sources.
     */
    val packageName: Property<String>

    /**
     * Whether additional metadata, such as entry count, date information, and changelog should be
     * captured. When set to `true`, a `data class Metadata` is generated alongside kanjidict content.
     * Defaults to `true`.
     */
    val generateMetadata: Property<Boolean>
}

class KanjiDictGeneratorPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Create the Gradle extension for configuration
        val config = target.extensions.create(
            ExtensionName,
            KanjiDictExtension::class.java
        )
        config.generateMetadata.convention(true)
        config.kanjiDictUrl.convention(URI("https://www.edrdg.org/kanjidic/kanjidic2.xml.gz"))

        val targetGeneratedSourcesDir = target.layout.buildDirectory.dir("generated/kanjidict/kotlin")
        val targetKanjiDictResDir = target.layout.buildDirectory.dir("generated/kanjidict/composeResources/")
        val kanjiDictFile = target.layout.buildDirectory.file("resources/kanjidict/kanjidict.xml")
        val relNotesFile = target.layout.buildDirectory.file("resources/kanjidict/changelog.xml")
        val dtdFile = target.layout.buildDirectory.file("resources/kanjidict/dtd.xml")
        val metadataFile = target.layout.buildDirectory.file("resources/kanjidict/metadata.properties")

        // Register the download task
        val downloadKanjiDic2Task = target.tasks.register(
            "downloadKanjiDict",
            DownloadKanjiDic2Task::class.java
        ) {
            requireProperty(config::kanjiDictUrl, "https://www.edrdg.org/kanjidic/kanjidic2.xml.gz")

            it.kanjiDic2Url.set(config.kanjiDictUrl)
            it.outputKanjiDict.set(kanjiDictFile)
            it.outputDtd.set(dtdFile)
            it.outputReleaseNotes.set(relNotesFile)
            it.outputMetadata.set(metadataFile)
        }

        // Register the generation tasks
        val generateDataClassTask = target.tasks.register(
            "generateKanjiDictDataClasses",
            GenerateDataClassesTask::class.java
        ) {
            requireProperty(config::packageName, "\"com.my.package\"")

            it.dependsOn(downloadKanjiDic2Task)

            it.outputDirectory.set(targetGeneratedSourcesDir)
            it.packageName.set(config.packageName)
            it.dtdFile.set(downloadKanjiDic2Task.get().outputDtd)
        }
        val generateMetadataTask = target.tasks.register(
            "generateKanjiDictMetadataObject",
            GenerateMetadataObjectTask::class.java
        ) {
            requireProperty(config::packageName, "\"com.my.package\"")

            it.dependsOn(downloadKanjiDic2Task)

            it.outputDirectory.set(targetGeneratedSourcesDir)
            it.packageName.set(config.packageName)
            it.metadataFile.set(downloadKanjiDic2Task.get().outputMetadata)
        }

        // Configure Compose resources
        target.extensions.findByType(ComposeExtension::class.java)?.extensions?.findByType(ResourcesExtension::class.java)?.apply {
            val copyResourcesTask = target.tasks.register(
                "copyKanjiDictResource",
                CopyComposeResourcesTask::class.java
            ) {
                it.dependsOn(downloadKanjiDic2Task)
                it.jmDictFile.set(kanjiDictFile)
                it.outputDirectory.set(targetKanjiDictResDir)
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
