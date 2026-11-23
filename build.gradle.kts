import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.util.jar.Manifest

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
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
    ksp("care.better.pf4j:pf4j-kotlin-symbol-processing:${property("pf4jKspVersion")}")

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
    dependsOn("kspKotlin")
    manifest { from("src/main/resources/MANIFEST.MF") }
    from(configurations.runtimeClasspath.get().map { zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC", "META-INF/MANIFEST.MF")
    }
    from(layout.buildDirectory.dir("generated/ksp/main/resources"))
    from("src/main/resources") {
        include("logo.*")
        into("META-INF/resources")
    }
}
