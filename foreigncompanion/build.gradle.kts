plugins { alias(libs.plugins.android.application) }
val fixtureStore = layout.buildDirectory.file("test-signing/foreign.jks")
abstract class GenerateForeignKey : DefaultTask() {
    @get:OutputFile abstract val keystore: RegularFileProperty
    @TaskAction fun generate() {
        val file = keystore.get().asFile
        file.parentFile.mkdirs()
        if (!file.exists()) {
            val process = ProcessBuilder("${System.getProperty("java.home")}/bin/keytool", "-genkeypair", "-keystore", file.path,
                "-storepass", "fixture-only", "-keypass", "fixture-only", "-alias", "fixture", "-keyalg", "RSA", "-validity", "10000",
                "-dname", "CN=Foreign Companion Test Fixture", "-noprompt").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }
        }
    }
}
val generateFixtureKey = tasks.register<GenerateForeignKey>("generateFixtureKey") { keystore.set(fixtureStore) }
android {
    namespace="io.konnecttv.fixture"
    compileSdk=37
    defaultConfig { applicationId="io.konnecttv.glowlauncher"; minSdk=28; targetSdk=36; versionCode=1; versionName="foreign-test-only" }
    signingConfigs { create("fixture") { storeFile=fixtureStore.get().asFile; storePassword="fixture-only"; keyAlias="fixture"; keyPassword="fixture-only" } }
    buildTypes { debug { signingConfig=signingConfigs.getByName("fixture") } }
}
tasks.matching { it.name=="validateSigningDebug" }.configureEach { dependsOn(generateFixtureKey) }
