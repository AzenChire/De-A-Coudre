plugins {
    java
}

group = "fr.fixemy"
version = "1.0.0"
description = "Multi-arena Dé à Coudre (jump into the water) mini-game for Paper 26.3"

// Paper 26.3 requires Java 25.
val javaVersion = 25
// Pinned Paper API build for reproducible builds (see https://repo.papermc.io).
val paperApiVersion = "26.3.build.40-alpha"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // Unit tests only (never shipped in the plugin jar).
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = javaVersion
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf("version" to project.version, "description" to project.description)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
    }

    jar {
        archiveBaseName = "DeACoudre"
    }
}
