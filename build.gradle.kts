import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.Date
import java.util.Properties

plugins {
    java
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("eclipse")
    id("idea")
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.kt3k.coveralls") version "2.12.2"
    id("com.palantir.git-version") version "5.1.0"
    kotlin("plugin.lombok") version "2.4.21"
}

repositories {
    // mirror first, Gradle doesn't fall back to the next repository on errors like HTTP 429 (see settings.gradle)
    maven {
        name = "googleMavenCentralMirror"
        url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
    }
    mavenCentral()
}

val springBootVersion = "4.1.2"
val lombokVersion = "1.18.48"
val mockitoVersion = "5.24.0"
val kotlinVersion = "2.4.21"

dependencies {
    // tag::jetty[]
    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion") {
        // exclude(module = "spring-boot-starter-tomcat")
    }
    //implementation("org.springframework.boot:spring-boot-starter-jetty:$springBootVersion")
    // end::jetty[]

    // tag::actuator[]
    implementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
    // end::actuator[]

    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.google.code.gson:gson:2.14.0")

    implementation("org.springframework.boot:spring-boot-properties-migrator:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-cache:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-security:$springBootVersion")
    implementation("com.github.ben-manes.caffeine:caffeine:3.3.1")
    implementation("commons-io:commons-io:2.22.0")
    implementation("commons-codec:commons-codec:1.23.0")
    implementation("commons-validator:commons-validator:1.11.0")

    implementation("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")

    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    testImplementation(kotlin("test"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}

// the version is the latest tag; only CI fetches the tags first (the release tag is pushed during the run), a local
// build uses the tags it has and works offline
val gitVersionProvider = providers.exec {
    commandLine(
        "sh", "-c",
        "if [ -n \"\$CI\" ]; then git fetch --tags --quiet || true; fi; git describe --tags --always --first-parent | tail -1"
    )
}

val gitHashProvider = providers.exec {
    commandLine("git", "rev-parse", "--verify", "--short", "HEAD")
}

tasks.jar {
    archiveBaseName.set("boat")
    archiveVersion.set(gitVersionProvider.standardOutput.asText.map { it.trim() })
}

tasks.register("createProperties") {
    dependsOn(tasks.processResources)
    val versionProvider = gitVersionProvider.standardOutput.asText
    val hashProvider = gitHashProvider.standardOutput.asText
    val outputDir = layout.buildDirectory.dir("resources/main")

    doLast {
        val versionFile = outputDir.get().file("version.properties").asFile
        versionFile.parentFile.mkdirs()
        versionFile.bufferedWriter().use { w ->
            val p = Properties()
            val version = versionProvider.get().trim()
            val hash = hashProvider.get().trim().take(12)
            p["version"] = "$version-$hash-${Date()}"
            println("Version:${p["version"]}")
            p.store(w, null)
        }
    }
}

tasks.classes {
    dependsOn("createProperties")
}

tasks.test {
    // tests tagged "live" need the internet (search engines, TMDB, TVDB, trackers) and run with ./gradlew liveTest
    useJUnitPlatform {
        excludeTags("live")
    }
    // the dummy settings of the offline tests, a boat.cfg of the developer is not used (environment variables still win)
    systemProperty("boat.config", file("src/test/resources/boat.test.cfg").absolutePath)
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    testLogging {
        events("standardOut", "started", "passed", "skipped", "failed")
        showStandardStreams = true
    }
}

val liveTest by tasks.registering(Test::class) {
    description = "Runs the tests which talk to the real search engines, metadata services and trackers."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("live")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    testLogging {
        events("started", "passed", "skipped", "failed")
    }
    shouldRunAfter(tasks.test)
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}
