plugins {
    java
}

val pluginVersion = project.version.toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    implementation(project(":omnipet-core"))
    compileOnly(libs.paper.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val compatibilityPaperApiVersion = providers.gradleProperty("compatibilityPaperApiVersion")
    .orElse(providers.gradleProperty("paperApiVersion").orElse("1.21-R0.1-SNAPSHOT"))
val compatibilityJavaVersion = providers.gradleProperty("compatibilityJavaVersion").map(String::toInt).orElse(21)
val compatibilityCompileClasspath = configurations.create("compatibilityCompileClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    resolutionStrategy.deactivateDependencyLocking()
}

dependencies {
    compatibilityCompileClasspath(project(":omnipet-core"))
    compatibilityCompileClasspath("io.papermc.paper:paper-api:${compatibilityPaperApiVersion.get()}")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaCompile>("compileCompatibilityJava") {
    group = "verification"
    description = "Compiles the Paper boundary against the requested compatibility API."
    source(sourceSets.main.get().allJava)
    classpath = compatibilityCompileClasspath
    destinationDirectory.set(layout.buildDirectory.dir("classes/compatibility/${compatibilityPaperApiVersion.get()}"))
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(compatibilityJavaVersion.map(JavaLanguageVersion::of))
    })
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named("check") {
    dependsOn("compileCompatibilityJava")
}

tasks.jar {
    dependsOn(":omnipet-core:jar")
    archiveBaseName.set("OmniPet")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes["Implementation-Title"] = "OmniPet"
    manifest.attributes["Implementation-Version"] = pluginVersion
    exclude("META-INF/maven/**")

    from(provider {
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}
