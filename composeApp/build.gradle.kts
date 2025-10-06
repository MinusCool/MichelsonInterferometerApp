import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Gunakan JDK 17
    jvmToolchain(17)

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Target iOS
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // Desktop target
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
            implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
        }
    }
}

android {
    namespace = "org.example.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.example.project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false // 🔧 Nonaktifkan ProGuard di Android
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}
tasks.matching { it.name.contains("proguard", ignoreCase = true) }.configureEach {
    enabled = false
}
compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        // 👇 Tambahan penting untuk mem-bypass ProGuard
        buildTypes.release.proguard {
            isEnabled.set(false) // pastikan proguard off
        }

        // 👇 Override path JAR agar tidak mencari hasil ProGuard
        fromFiles(
            file("build/compose/jars/main/composeApp-desktop.jar")
        )

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "InterferometerApp"
            packageVersion = "1.0.0"
            description = "MyApp description"
            vendor = "MyCompany"

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
        }
    }
}

// 🚫 Jaga-jaga: pastikan ProGuard benar-benar tidak dijalankan
gradle.taskGraph.whenReady {
    allTasks.filter { it.name.contains("proguard", ignoreCase = true) }.forEach {
        it.enabled = false
        println("⚠️ Disabled task: ${it.name}")
    }
}

