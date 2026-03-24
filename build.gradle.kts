// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // Asegúrate de tener la dependencia de Google Services aquí
        classpath("com.google.gms:google-services:4.4.0")
    }
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Este plugin es necesario para la anotación @Parcelize
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.23" apply false
}