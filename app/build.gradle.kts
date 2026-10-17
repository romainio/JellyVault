import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.FilterConfiguration
import com.google.protobuf.gradle.id
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.aboutLibraries)
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

val isCI = providers.environmentVariable("CI").orElse("false").map { it.toBoolean() }

// CI decodes the base64 SIGNING_KEY secret; a local release build points KEY_STORE_FILE at the keystore instead
val keyStoreFile = providers.environmentVariable("KEY_STORE_FILE").orElse("")
val shouldSign =
    isCI.zip(keyStoreFile) { isCI, storeFile -> isCI || storeFile.isNotBlank() }.zip(
        providers.environmentVariable("KEY_ALIAS").orElse("").map { it.isNotBlank() },
    ) { hasStore, hasKey ->
        hasStore && hasKey
    }
val ffmpegModuleExists =
    providers.provider { project.file("libs/lib-decoder-ffmpeg-release.aar").exists() }
val av1ModuleExists =
    providers.provider { project.file("libs/lib-decoder-av1-release.aar").exists() }

// See https://issuetracker.google.com/issues/402800800
val isBuildingBundle =
    providers.provider {
        gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
    }

kotlin {
    compilerOptions {
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
        jvmTarget = JvmTarget.JVM_11
        javaParameters = true

        // Workaround for https://github.com/google/ksp/issues/2964
        moduleName = "${project.group}_${project.name}"
    }
}

private fun Provider<String>.getInt() = get().toInt()

configure<ApplicationExtension> {
    namespace = "tv.jellyvault.app"
    compileSdk = libs.versions.compileSdk.getInt()

    defaultConfig {
        applicationId = "tv.jellyvault.app"
        minSdk = libs.versions.minSdk.getInt()
        targetSdk = libs.versions.targetSdk.getInt()
        // Explicit: the public repository has no tag history (the TV's previous build was code 60)
        versionCode = 102
        versionName = "1.2.0"
        testInstrumentationRunner = "tv.jellyvault.app.test.JellyVaultTestRunner"

        buildConfigField("long", "BUILD_TIME", System.currentTimeMillis().toString())
        // Optional fallback TMDB credential for your own builds; users can enter theirs under Settings › TMDB & IMDb ratings
        buildConfigField(
            "String",
            "TMDB_READ_TOKEN",
            "\"${localProperties.getProperty("tmdb.readToken", "")}\"",
        )
    }

    signingConfigs {
        if (shouldSign.get()) {
            create("ci") {
                storeFile =
                    keyStoreFile.get().takeIf { it.isNotBlank() }?.let { file(it) }
                        ?: file("ci.keystore").apply {
                            writeBytes(Base64.getDecoder().decode(System.getenv("SIGNING_KEY")))
                        }
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storePassword = System.getenv("KEY_STORE_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isDebuggable = false
            if (shouldSign.get()) {
                signingConfig = signingConfigs.getByName("ci")
            } else {
                val localPropertiesFile = project.rootProject.file("local.properties")
                if (localPropertiesFile.exists()) {
                    val properties = Properties()
                    properties.load(localPropertiesFile.inputStream())
                    val signingConfigName = properties["release.signing.config"]?.toString()
                    if (signingConfigName != null) {
                        signingConfig = signingConfigs.getByName(signingConfigName)
                    }
                }
            }
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }
    flavorDimensions += "version"
    productFlavors {
        val featureLeanback = "leanback"
        val featureUpdate = "UPDATING_ENABLED"

        fun ProductFlavor.setFeatureFlag(
            name: String,
            enabled: Boolean,
        ) {
            this.buildConfigField("boolean", name, "Boolean.parseBoolean(\"${enabled}\")")
        }
        create("default") {
            dimension = "version"
            isDefault = true
            manifestPlaceholders += mapOf(featureLeanback to false)
            // Sideloaded builds check the JellyVault GitHub releases for updates
            setFeatureFlag(featureUpdate, true)
        }
        create("appstore") {
            dimension = "version"
            manifestPlaceholders += mapOf(featureLeanback to true)
            setFeatureFlag(featureUpdate, false)
        }
        create("firetv") {
            dimension = "version"
            manifestPlaceholders += mapOf(featureLeanback to true)
            setFeatureFlag(featureUpdate, false)
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    splits {
        abi {
            // Disable split abis when building bundles
            isEnable = !isBuildingBundle.get()

            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    packaging {
        jniLibs {
            // Work around because libass-android packages libc++_shared.so too
            pickFirsts += "lib/*/libc++_shared.so"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Coroutine stack-trace recovery wraps exceptions that expose a (String, Throwable)
                // constructor in a copy on every suspension point, which breaks `cause` chain
                // assertions in tests (e.g. TmdbClientTest). Recovery only helps readability of
                // stack traces, so it is safe to disable for unit tests.
                it.systemProperty("kotlinx.coroutines.stacktrace.recovery", "false")
            }
        }
    }

    lint {
        disable.add("MissingTranslation")
    }
    androidResources {
        generateLocaleConfig = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs
            .map { it as com.android.build.api.variant.impl.VariantOutputImpl }
            .forEach { output ->
                val abi =
                    output
                        .getFilter(FilterConfiguration.FilterType.ABI)
                        .let { if (it != null) "-${it.identifier}" else "" }
                val outputFileName =
                    "JellyVault-${variant.flavorName}-${variant.buildType}-${output.versionName.get()}-${output.versionCode.get()}$abi.apk"
                output.outputFileName = outputFileName
            }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.protobuf.kotlin.lite.get().version}"
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("java") {
                    option("lite")
                }
            }
            it.builtins {
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}
aboutLibraries {
    collect {
        configPath = file("config")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.ass.media)

    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.cachecontrol)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.jellyfin.core)
    implementation(libs.jellyfin.api)
    implementation(libs.jellyfin.api.okhttp)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.testing)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.runner)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.timber)
    implementation(libs.slf4j2.timber)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.multiplatform.markdown.renderer)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.programguide)
    implementation(libs.acra.http)
    implementation(libs.acra.dialog)
    implementation(libs.acra.limiter)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.kache)
    implementation(libs.kache.file)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    if (ffmpegModuleExists.get()) {
        logger.info("Using local ffmpeg decoder")
        implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
    } else {
        logger.warn("Media3 ffmpeg decoder was NOT found")
    }
    if (av1ModuleExists.get()) {
        logger.info("Using local av1 decoder")
        implementation(files("libs/lib-decoder-av1-release.aar"))
    } else {
        logger.warn("Media3 av1 decoder was NOT found")
    }
    // libMPV is not bundled: the stub keeps the MPV backend selectable but playback falls back to ExoPlayer
    implementation(project(":mpv-stub"))

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
