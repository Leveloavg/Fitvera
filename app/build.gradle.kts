plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.fitvera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fitvera"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Health Connect
    // Fíjate en el "maps.android"
    implementation ("com.airbnb.android:lottie:6.1.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha04")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.hbb20:ccp:2.7.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

    // AndroidX & Utils
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")

    // UI Components
    implementation("com.github.prolificinteractive:material-calendarview:2.0.1")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.drawerlayout)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-database-ktx")

    // Google Services
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.android.gms:play-services-fitness:21.1.0")

    // Maps
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation("org.osmdroid:osmdroid-android:6.1.14") {
        exclude(group = "com.android.support")
    }

    // Charts & Media
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.github.AnyChart:AnyChart-Android:1.1.2") {
        exclude(group = "com.android.support")
    }
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.androidx.gridlayout)
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.cloudinary:cloudinary-android:2.1.0")
    implementation(libs.androidx.media3.common.ktx)

    // Compose
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Configuración Global de resolución
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "junit" && requested.name == "junit") {
            useVersion("4.13.2")
        }
        if (requested.group == "org.hamcrest" && requested.name.startsWith("hamcrest")) {
            useVersion("2.2")
        }
    }
    exclude(group = "com.android.support", module = "support-compat")
    exclude(group = "com.android.support", module = "support-core-utils")
}