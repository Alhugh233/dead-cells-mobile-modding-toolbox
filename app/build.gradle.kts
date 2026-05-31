plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "com.deadcells.modding"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootProject.projectDir}/app/keystore/release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "DCMMT"
            keyAlias = System.getenv("KEY_ALIAS") ?: "dcmmt"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "DCMMT"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
            signingConfig = if (file("${rootProject.projectDir}/app/keystore/release.jks").exists())
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
    implementation("io.github.libxposed:service:101.0.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.1")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.1")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0-beta01")
    implementation("androidx.activity:activity-compose:1.13.0")
}
