plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.khaiit.kremote.core.network"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}