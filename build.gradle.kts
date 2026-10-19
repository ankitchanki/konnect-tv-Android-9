buildscript {
    dependencies {
        // AGP 9 carries its own Kotlin; this makes the `kotlin { compilerOptions { } }` DSL resolvable.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
