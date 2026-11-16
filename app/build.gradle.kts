import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nadim.bikepart"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nadim.bikepart"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    sourceSets.getByName("main").assets.directories.add("$projectDir/schemas")

    val releaseStoreFile = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
    if (releaseStoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("com.microsoft.fluentui:fluentui_core:0.3.11")
    implementation("com.microsoft.fluentui:fluentui_controls:0.3.3")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation(libs.google.play.services.auth)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.zxing.core)
    implementation(libs.icu4j)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Runtime contrast exports carry the exact active source/dependency fingerprint.
abstract class ExportThemeFingerprint : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection
    @get:Input abstract val sourcePaths: ListProperty<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun export() {
        val digest = MessageDigest.getInstance("SHA-256")
        sourcePaths.get().forEach { path ->
            digest.update((path + "\n").toByteArray())
            digest.update(sources.files.single { it.path.endsWith(path) }.readBytes())
            digest.update("\n".toByteArray())
        }
        outputDirectory.get().file("theme-source-sha256.txt").asFile.apply {
            parentFile.mkdirs()
            writeText(digest.digest().joinToString("") { "%02x".format(it) })
        }
    }
}
val themeFingerprintAssets = layout.buildDirectory.dir("generated/themeFingerprintAssets")
val exportThemeFingerprint = tasks.register<ExportThemeFingerprint>("exportThemeFingerprint") {
    val paths = Regex("\"([^\"]+)\"").findAll(rootProject.file("tools/theme/source-files.json").readText()).map { it.groupValues[1] }.toList()
    sourcePaths.set(paths)
    sources.from(paths.map { rootProject.file(it) })
    outputDirectory.set(themeFingerprintAssets)
}
androidComponents.onVariants { variant ->
    variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(
        exportThemeFingerprint, ExportThemeFingerprint::outputDirectory)
}
