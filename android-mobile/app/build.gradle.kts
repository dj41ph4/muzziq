import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version dérivée du tag Git par la CI (-PmuzziqVersionName=... -PmuzziqVersionCode=...),
// même mécanisme que Movviz (.github/workflows/android-mobile-build.yml) — jamais figée
// en dur ici pour que l'écran "À propos" affiche la vraie version publiée.
val resolvedVersionName = (project.findProperty("muzziqVersionName") as String?)?.takeIf { it.isNotBlank() } ?: "0.1.0-dev"
val resolvedVersionCode = (project.findProperty("muzziqVersionCode") as String?)?.toIntOrNull() ?: 1

// Client ID Spotify (Authorization Code + PKCE, jamais de client_secret dans l'APK —
// PKCE n'en a structurellement pas besoin) : chaque utilisateur enregistre sa propre
// app sur developer.spotify.com (MuzziQ est self-hosted/perso, pas d'app Spotify
// partagée publiée) et colle son Client ID dans un fichier local non versionné, même
// mécanisme que keystore.properties ci-dessous. Vide par défaut : la capacité Spotify
// se déclare simplement absente (voir SpotifyAuthManager) plutôt que de planter.
val spotifyClientId = run {
    val props = Properties()
    val propsFile = rootProject.file("spotify.properties")
    if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }
    props.getProperty("clientId") ?: ""
}

android {
    namespace = "com.muzziq.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.muzziq.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (rootProject.file("keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Pas de bloc kotlinOptions : avec le Kotlin integre d'AGP 9, jvmTarget
    // suit automatiquement compileOptions.targetCompatibility (17 ci-dessus).

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room exporte le schéma JSON à chaque compilation (une version par bump de
// `version` dans MuzziQDatabase) — nécessaire pour que Room valide les
// migrations à la compilation (testMigration) plutôt que de découvrir un
// schéma cassé au premier redémarrage d'un vrai appareil.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)

    implementation(libs.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.datasource)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.palette.ktx)

    implementation(libs.datastore.preferences)
    // Custom Tabs — onglet sécurisé pour l'écran de connexion Spotify (PKCE mobile),
    // jamais une WebView maison (voir SpotifyAuthManager, commentaire de tête).
    implementation(libs.androidx.browser)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.guava)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}
