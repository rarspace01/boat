import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.Date
import java.util.Properties

plugins {
    java
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    id("eclipse")
    id("idea")
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.kt3k.coveralls") version "2.12.2"
    id("com.palantir.git-version") version "5.0.0"
}

repositories {
    mavenCentral()
}

val springBootVersion = "4.0.5"
val lombokVersion = "1.18.44"
val mockitoVersion = "5.23.0"
val kotlinVersion = "2.3.20"

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

    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.google.code.gson:gson:2.13.2")

    implementation("org.springframework.boot:spring-boot-properties-migrator:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-cache:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-security:$springBootVersion")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")
    implementation("commons-io:commons-io:2.21.0")
    implementation("commons-codec:commons-codec:1.21.0")
    implementation("commons-validator:commons-validator:1.10.1")

    implementation("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")

    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}

tasks.jar {
    archiveBaseName.set("boat")
    archiveVersion.set(getTagVersion())
}

fun getCheckedOutGitCommitHash(): String {
    val takeFromHash = 12
    return arrayOf("git", "rev-parse", "--verify", "--short", "HEAD")
            .run {
                Runtime.getRuntime().exec(this).inputStream.bufferedReader().readText().trim()
            }
            .take(takeFromHash)
}

fun getTagVersion(): String {
    return arrayOf("sh", "-c", "git fetch --all --tags>/dev/null&& git describe --tags --always --first-parent|tail -1")
            .run {
                Runtime.getRuntime().exec(this).inputStream.bufferedReader().readText().trim()
            }
}

tasks.register("createProperties") {
    dependsOn(tasks.processResources)
    doLast {
        val versionFile = layout.buildDirectory.file("resources/main/version.properties").get().asFile
        versionFile.parentFile.mkdirs()
        versionFile.bufferedWriter().use { w ->
            val p = Properties()
            p["version"] = "${tasks.jar.get().archiveVersion.get()}-${getCheckedOutGitCommitHash()}-${
                Date().toString()
            }"
            println("Version:${p["version"]}")
            p.store(w, null)
        }
    }
}

tasks.classes {
    dependsOn("createProperties")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("standardOut", "started", "passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

subprojects {
    tasks.withType<Test>().configureEach {
        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }
}