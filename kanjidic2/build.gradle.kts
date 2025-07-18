plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.detekt)
    id("com.boswelja.jmdict.generator")
    id("com.boswelja.publish")
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilations.create("benchmark") {
            associateWith(this@jvm.compilations.getByName("main"))
        }
    }
    androidLibrary {
        namespace = "com.boswelja.jmdict"
        compileSdk = 36
        minSdk = 23

        withDeviceTest {}

        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.components.resources)
            implementation(compose.runtime)
            implementation(libs.kotlinx.serialization.xml)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("jvmBenchmark").dependencies {
            implementation(libs.kotlinx.benchmark.runtime)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)

            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt.yml")
    basePath = rootDir.absolutePath
}

jmDict {
    packageName = "com.boswelja.jmdict"
}

benchmark {
    targets {
        register("jvmBenchmark")
    }
}

publish {
    description = "Pre-packaged Japanese-Multilingual dictionary for all your Kotlin Multiplatform needs!"
    repositoryUrl = "https://github.com/kmpdict/jmdict-kmp"
    license = "CC-BY-SA-4.0"
}

afterEvaluate {
    tasks.withType(org.gradle.jvm.tasks.Jar::class) {
        if (archiveClassifier.get() == "sources") {
            dependsOn("generateJmDictDataClasses")
            dependsOn("generateJmDictMetadataObject")
        }
    }
}
