import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    // Lee app/google-services.json, que no está en el repositorio: en el CI sale del secreto GOOGLE_SERVICES_JSON_BASE64
    alias(libs.plugins.google.services)
}

// Clave de la cuenta de servicio padelprat-fcm, que solo puede enviar mensajes de FCM (ver docs/plan-reservas-amigo.md).
// El workflow de releases pasa la ruta con la propiedad padelpratFcmServiceAccount; en local va en local.properties como
// padelprat.fcmServiceAccount. Sin ella la app compila igual, pero no avisa al amigo de los cambios
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { load(it.reader()) }
}
val fcmServiceAccount: Map<*, *> = (
    providers.gradleProperty("padelpratFcmServiceAccount").orNull
        ?: localProperties.getProperty("padelprat.fcmServiceAccount")
    )
    ?.let { path -> JsonSlurper().parseText(providers.fileContents(layout.projectDirectory.file(path)).asText.get()) }
    .let { it as? Map<*, *> }
    .orEmpty()

fun buildConfigString(value: Any?): String =
    "\"" + value?.toString().orEmpty().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

android {
    namespace = "com.dgalan.padelprat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dgalan.padelprat"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.3.1"

        // Web de reservas del club (Syltek). Ver docs/syltek-api.md
        buildConfigField("String", "BASE_URL", "\"https://prat.syltek.com/\"")
        buildConfigField("String", "FCM_PROJECT_ID", buildConfigString(fcmServiceAccount["project_id"]))
        buildConfigField("String", "FCM_CLIENT_EMAIL", buildConfigString(fcmServiceAccount["client_email"]))
        buildConfigField("String", "FCM_PRIVATE_KEY", buildConfigString(fcmServiceAccount["private_key"]))
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    debugImplementation(libs.androidx.compose.ui.tooling)
    ktlintRuleset(libs.compose.rules.ktlint)
}
