plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mivan.streamingsandbox"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mivan.streamingsandbox"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "WIDEVINE_LICENSE_URL",
            "\"https://proxy.uat.widevine.com/proxy?video_id=2015_tears&provider=widevine_test\""
        )
        buildConfigField(
            "String",
            "EPG_XML_GZ_URL",
            "\"https://iptv-epg.org/files/epg-mx.xml.gz\""
        )
        buildConfigField(
            "String",
            "EPG_CHANNEL_ALIASES",
            "\"" +
                    "adn 40=adn40;canal 22 nacional=canal 22" +
                    "azteca internacional=azteca internacional hd;" +
                    "foro tv=forotv;"+
                    "milenio television=milenio;" +
                    "multimedios bajio=multimedios;" +
                    "multimedios cdmx=multimedios;" +
                    "multimedios ciudad juarez=multimedios;" +
                    "multimedios guadalajara=multimedios;" +
                    "multimedios laguna=multimedios;" +
                    "multimedios monterrey=multimedios;" +
                    "multimedios puebla=multimedios;" +
                    "multimedios saltillo=multimedios" +
                    "\""
        )
    }

    buildTypes {
        debug {

        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.palette.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation)
    ksp(libs.hilt.compiler)

    // ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)

    // HTTP
    implementation(libs.okhttp)

    // Images
    implementation(libs.coil)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}