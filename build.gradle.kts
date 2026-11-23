import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.util.jar.Manifest
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.github.devhovai.gameyfin"
version = Manifest(file("src/main/resources/MANIFEST.MF").inputStream()).mainAttributes.getValue("Plugin-Version")

repositories {
    mavenLocal()
    mavenCentral()
}

val javaVersion = providers.gradleProperty("javaVersion").get()
val kotlinLanguageVersion = KotlinVersion.fromVersion(providers.gradleProperty("kotlinLanguageVersion").get())

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}
kotlin.compilerOptions {
    languageVersion = kotlinLanguageVersion
    apiVersion = kotlinLanguageVersion
    jvmTarget = JvmTarget.fromTarget(javaVersion)
    progressiveMode = true
    freeCompilerArgs.add("-Xjsr305=strict")
}

val pluginApiVersion = providers.gradleProperty("pluginApiVersion").get()

dependencies {
    // Provided by the Gameyfin host at runtime.
    compileOnly(kotlin("stdlib"))
    compileOnly("org.gameyfin:plugin-api:$pluginApiVersion")

    // Bundled into the plugin JAR.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("serializationJsonVersion")}") {
        exclude(group = "org.jetbrains.kotlin")
    }

    testImplementation(kotlin("stdlib"))
    testImplementation("org.gameyfin:plugin-api:$pluginApiVersion")
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:${property("junitVersion")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
configurations.named("runtimeClasspath") { exclude(group = "org.slf4j") }

tasks.test { useJUnitPlatform() }

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
    archiveBaseName.set("gameyfin-store-extras")
    manifest { from("src/main/resources/MANIFEST.MF") }
    from(configurations.runtimeClasspath.get().map { zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC", "META-INF/MANIFEST.MF")
    }
    from("src/main/resources") {
        include("logo.*")
        into("META-INF/resources")
    }
}

// The extension index must be inside the JAR, or Gameyfin shows the plugin as running but never calls it.
// It is a checked-in resource (not generated), so a cached or incremental build can never drop it.
tasks.jar {
    doLast {
        val jar = archiveFile.get().asFile
        val index = ZipFile(jar).use { it.getEntry("META-INF/extensions.idx") }
        check(index != null) { "$jar has no META-INF/extensions.idx" }
    }
}
