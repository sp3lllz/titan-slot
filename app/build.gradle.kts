import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing, kept out of the repository. Point at a properties file holding
//   storeFile, storePassword, keyAlias, keyPassword
// with -Ptitanslot.signing=/path/to/signing.properties, or put one at ./keystore.properties.
// Without either, release builds come out unsigned.
val releaseSigning: Properties? = ((findProperty("titanslot.signing") as String?)?.let(::file)
    ?: rootProject.file("keystore.properties"))
    .takeIf { it.isFile }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "dev.titanslot"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.titanslot"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        // The Titan 2 Elite is arm64, and so are the libretro cores.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Where the emulator cores come from. Neither flavour has them in the base APK: the setup
    // wizard installs them (dev.titanslot.core.Delivery, one per flavour).
    flavorDimensions += "distribution"
    productFlavors {
        // Google Play. Play doesn't allow downloading native code from anywhere else, so each
        // core is an on-demand feature module (:core_gambatte, :core_mgba) that Play delivers.
        // Upload the bundle: ./gradlew bundlePlayRelease
        create("play") { dimension = "distribution" }
        // Sideloaded APKs (./gradlew assembleDirectRelease): setup downloads the cores from the
        // libretro buildbot instead.
        create("direct") { dimension = "distribution" }
    }

    dynamicFeatures += setOf(":core_gambatte", ":core_mgba")

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // LibretroDroid calls back into Kotlin from JNI and its lifecycle observers are
            // found by reflection, so shrinking would break it. The cores are most of the size.
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Versions are pinned to the newest set that works with AGP 8.13 (AndroidX 2026
        // releases want AGP 9), and the app only targets the Titan's arm64 SoC.
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "ChromeOsAbiSupport",
            // AAPT2 only finds the adaptive launcher icon under mipmap-anydpi-v26.
            "ObsoleteSdkInt",
        )
    }

    packaging {
        // Cores are dlopen()ed by path, so they must be extracted to nativeLibraryDir.
        jniLibs { useLegacyPackaging = true }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("com.github.Swordfish90:LibretroDroid:0.14.0")

    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    "playImplementation"("com.google.android.play:feature-delivery:2.1.0")

    testImplementation("junit:junit:4.13.2")
}
