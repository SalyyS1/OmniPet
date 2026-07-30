import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
}

group = "io.github.salyvn"
version = providers.gradleProperty("pluginVersion").orElse("3.0.0-SNAPSHOT").get()
// Compile against the oldest supported Paper API so one artifact covers the 1.21.x line.
val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("1.21-R0.1-SNAPSHOT")
val compatibilityPaperApiVersion = providers.gradleProperty("compatibilityPaperApiVersion").orElse(paperApiVersion)
val compatibilityJavaVersion = providers.gradleProperty("compatibilityJavaVersion").map(String::toInt).orElse(21)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("com.mojang:datafixerupper:8.0.16")
    compileOnly("io.lumine:MythicLib-dist:1.7.1-SNAPSHOT")
    compileOnly("net.Indyuce:MMOItems-API:6.10.1-SNAPSHOT")

	implementation("com.github.nahkd123:tinyexpr:8464648b3e")

	testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
	testImplementation("org.yaml:snakeyaml:2.4")
	testRuntimeOnly("com.mojang:datafixerupper:8.0.16")
}

val compatibilityCompileClasspath = configurations.create("compatibilityCompileClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    resolutionStrategy.deactivateDependencyLocking()
}

dependencies {
    compatibilityCompileClasspath("io.papermc.paper:paper-api:${compatibilityPaperApiVersion.get()}")
    compatibilityCompileClasspath("com.mojang:datafixerupper:8.0.16")
    compatibilityCompileClasspath("io.lumine:MythicLib-dist:1.7.1-SNAPSHOT")
    compatibilityCompileClasspath("net.Indyuce:MMOItems-API:6.10.1-SNAPSHOT")
    compatibilityCompileClasspath("com.github.nahkd123:tinyexpr:8464648b3e")
    compatibilityCompileClasspath("org.jetbrains:annotations:26.0.2")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("target"))
}

tasks.register<JavaCompile>("compileCompatibilityJava") {
    group = "verification"
    description = "Compiles OmniPet against -PcompatibilityPaperApiVersion without changing dependency locks."
    source(sourceSets.main.get().allJava)
    classpath = compatibilityCompileClasspath
    destinationDirectory.set(layout.buildDirectory.dir("classes/compatibility/${compatibilityPaperApiVersion.get()}"))
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(compatibilityJavaVersion.map(JavaLanguageVersion::of))
    })
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

val checkBranding = tasks.register("checkBranding") {
    group = "verification"
    description = "Rejects stale owned PassivePet identifiers from Java and YAML resources."
    doLast {
        val banned = listOf("PassivePet", "passivepet", "io.github.nahkd123.comm")
        val candidates = fileTree("src/main") {
            include("**/*.java", "**/*.yml", "**/*.yaml")
        }
        val stale = candidates.flatMap { file ->
            if (file.name in setOf("PetItemKeys.java", "MMOItemsHook.java", "OmniPetCommands.java")) return@flatMap emptyList()
            val lines = file.readLines(Charsets.UTF_8)
            lines.mapIndexedNotNull { index, line ->
                if (banned.any(line::contains)) "${file}:${index + 1}: $line" else null
            }
        }
        check(stale.isEmpty()) {
            "Stale owned branding identifiers found:\n${stale.joinToString("\n")}"
        }
    }
}

tasks.named("check") {
    dependsOn(checkBranding)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("OmniPet")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes["Implementation-Title"] = "OmniPet"
    manifest.attributes["Implementation-Version"] = project.version.toString()

    // Only TinyExpr is bundled; Paper and optional integrations are server-provided.
    from(provider {
        configurations.runtimeClasspath.get()
            .filter { it.name.startsWith("tinyexpr-") }
            .map { zipTree(it) }
    })
}

tasks.register<Copy>("copyReleaseArtifact") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into(layout.buildDirectory.dir("release"))
}

tasks.named("build") {
    finalizedBy("copyReleaseArtifact")
}
