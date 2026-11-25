plugins {
    id("com.android.application") version "9.2.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
    id("com.google.devtools.ksp") version "2.3.6"
}

android {
    namespace = "app.hourstracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.hourstracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.single().outputFileName.set("HourThreads.apk")
    }
}

ksp { arg("room.schemaLocation", file("schemas").path) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.1")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
}

val verifyOfflineDependencies = tasks.register("verifyOfflineDependencies") {
    group = "verification"
    doLast {
        val forbidden = configurations.getByName("debugRuntimeClasspath").incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
            .filter {
                it.group.startsWith("com.google.android.gms") ||
                    it.group.startsWith("com.google.firebase") ||
                    it.group == "com.android.billingclient" ||
                    it.group == "com.android.installreferrer" ||
                    it.group == "com.google.android.play"
            }
        check(forbidden.isEmpty()) { "Unexpected Google services dependencies: $forbidden" }
    }
}
tasks.named("check") { dependsOn(verifyOfflineDependencies) }
