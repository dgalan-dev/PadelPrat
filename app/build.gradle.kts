plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.dgalan.padelprat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dgalan.padelprat"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        // Web de reservas del club (Syltek). Ver docs/syltek-api.md
        buildConfigField("String", "BASE_URL", "\"https://prat.syltek.com/\"")
    }

    // Firma de las releases: solo en el workflow de releases (.github/workflows/release.yml), que pasa la clave con
    // las propiedades padelpratStoreFile y padelpratStorePassword. Sin ellas, la release sale sin firmar
    val releaseStoreFile = providers.gradleProperty("padelpratStoreFile").orNull
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("padelpratStorePassword").get()
                keyAlias = "padelprat"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 sobre toda la app. Limitarlo a unos paquetes (packageScope, como traía la plantilla) rompía el arranque:
            // R8 cambiaba la visibilidad de clases de Kotlin que OkHttp, fuera de ese ámbito, necesita
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        checkDependencies = true
    }
}

ktlint {
    version.set(libs.versions.ktlint)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.datastore)
    implementation(libs.tink.android)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.jsoup)
    debugImplementation(libs.androidx.compose.ui.tooling)
    ktlintRuleset(libs.compose.rules.ktlint)
}
