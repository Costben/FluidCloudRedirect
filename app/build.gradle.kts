plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cc.csxcl.fluidredirect"
    compileSdk = 37

    defaultConfig {
        applicationId = "cc.csxcl.fluidredirect"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "**/*.kotlin_builtins"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    implementation("androidx.activity:activity-compose:1.13.0")
    val couiVersion = providers.gradleProperty("couiVersion").get()
    implementation("io.github.suqi8.coui.kmp:coui-ui:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-preference:$couiVersion")
    implementation("io.github.suqi8.coui.kmp:coui-icons:$couiVersion")
}
