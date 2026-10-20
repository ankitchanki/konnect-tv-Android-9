import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing credentials live outside the build file, so the keystore password is never in source.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "io.konnecttv"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.konnecttv"
        // Android 9+: older phones use solid floating surfaces instead of RenderEffect blur.
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Include x86_64 so the same build can be verified on Android 9 emulators.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildFeatures { compose = true }

    signingConfigs {
        create("release") {
            val store = keystoreProperties.getProperty("storeFile")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Unsigned when keystore.properties is missing, which is what a fresh clone gets.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/**", "/META-INF/*.version",
                // BouncyCastle data for post-quantum and cert-path messages; only X.509 signing is used.
                "org/bouncycastle/**/*.properties",
            )
        }
    }

    lint { abortOnError = true }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

/** Puts the Glowlauncher APK in the app's assets, so turning it on needs no download. */
abstract class BundleGlowlauncher : DefaultTask() {
    @get:InputDirectory abstract val apkDir: DirectoryProperty
    @get:OutputDirectory abstract val outDir: DirectoryProperty

    @TaskAction fun copy() {
        val apk = apkDir.asFileTree.matching { include("*.apk") }.singleFile
        apk.copyTo(outDir.file("glowlauncher.apk").get().asFile, overwrite = true)
    }
}

val bundleGlowlauncher = tasks.register<BundleGlowlauncher>("bundleGlowlauncher") {
    dependsOn(":glowlauncher:assembleRelease")
    apkDir.set(rootProject.layout.projectDirectory.dir("glowlauncher/build/outputs/apk/release"))
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(bundleGlowlauncher, BundleGlowlauncher::outDir) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.haze)

    implementation(libs.libadb)
    implementation(libs.conscrypt.android)
    implementation(libs.bcpkix)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
