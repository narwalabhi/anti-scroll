import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Firebase without the Google Services plugin (docs/LLD.md, Cloud copy): the project's identifiers come
// from app/google-services.json when it's there; without it the cloud copy does nothing.
@Suppress("UNCHECKED_CAST")
// Read through providers, so the configuration cache notices when the file changes.
val firebase: Map<String, String> = providers.fileContents(layout.projectDirectory.file("google-services.json"))
    .asText.orNull?.let { text ->
    val root = groovy.json.JsonSlurper().parseText(text) as Map<String, Any>
    val project = root["project_info"] as Map<String, Any>
    val client = (root["client"] as List<Map<String, Any>>).first { c ->
        ((c["client_info"] as Map<String, Any>)["android_client_info"] as Map<String, Any>)["package_name"] == "com.abhishek.antiscroll"
    }
    mapOf(
        "projectId" to project["project_id"] as String,
        "appId" to (client["client_info"] as Map<String, Any>)["mobilesdk_app_id"] as String,
        "apiKey" to ((client["api_key"] as List<Map<String, Any>>).first()["current_key"] as String),
        // The web client (type 3) is what Google sign-in asks for; it appears once Google sign-in is on.
        "webClientId" to ((client["oauth_client"] as? List<Map<String, Any>>).orEmpty()
            .firstOrNull { (it["client_type"] as Number).toInt() == 3 }?.get("client_id") as? String).orEmpty(),
    )
} ?: emptyMap()

// The release key lives outside the project (docs/LLD.md, Sharing with friends); without it, no signed release.
val keystoreProperties = Properties().apply {
    val file = File(System.getProperty("user.home"), ".antiscroll/keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.abhishek.antiscroll"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.abhishek.antiscroll"
        minSdk = 26
        targetSdk = 37
        versionCode = 19
        versionName = "0.1.18"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebase["projectId"].orEmpty()}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase["appId"].orEmpty()}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebase["apiKey"].orEmpty()}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${firebase["webClientId"].orEmpty()}\"")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Its own app ID, so a debug build installs next to the release app without touching its data.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    // Android's org.json is a stub in plain unit tests; the real one lets tests parse instagram_rules.json.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}