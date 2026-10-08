plugins {
    id("com.android.dynamic-feature")
}

// The gambatte libretro core, alone, as an on-demand feature module. It has no code: just
// src/main/jniLibs/arm64-v8a/libgambatte_libretro_android.so, which scripts/fetch-cores.sh fetches.
android {
    namespace = "dev.titanslot.core.gambatte"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    // Feature modules carry the base module's flavours.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("direct") { dimension = "distribution" }
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(project(":app"))
}
