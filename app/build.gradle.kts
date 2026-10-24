import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val supabaseProperties = Properties().apply {
    val propertiesFile = rootProject.file("supabase.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

// Llave de subida a Google Play. keystore.properties no se versiona; sin él, el
// bundle de release se genera sin firmar.
val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun String.toBuildConfigLiteral(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "cl.iglesia.gestion"
    // Google Play exige targetSdk 36 (Android 16) desde el 31 de agosto de 2026.
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.iglesia.gestion"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"
        buildConfigField(
            "String",
            "SUPABASE_URL",
            (supabaseProperties.getProperty("supabase.url") ?: "").toBuildConfigLiteral(),
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            (supabaseProperties.getProperty("supabase.anon_key") ?: "").toBuildConfigLiteral(),
        )
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Sin R8: supabase-kt y kotlinx.serialization necesitan reglas propias que aún no están probadas.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
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

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.1"))
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation(files("libs/json-20230227.jar"))

    debugImplementation("androidx.compose.ui:ui-tooling")
}
