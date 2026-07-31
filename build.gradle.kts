import java.util.zip.ZipFile

plugins {
    base
}

group = "io.github.salyvn"
version = providers.gradleProperty("pluginVersion").orElse("3.0.0-SNAPSHOT").get()
val distributionJar = layout.projectDirectory.file("omnipet-paper/build/libs/OmniPet-$version.jar")
val repositoryRoot = layout.projectDirectory.asFile.toPath()

allprojects {
    group = rootProject.group
    version = rootProject.version
}

val checkCoreBoundary = tasks.register("checkCoreBoundary") {
    group = "verification"
    description = "Rejects Paper, Bukkit, and optional vendor linkage from omnipet-core."
    doLast {
        val bannedImports = listOf(
            "import org.bukkit.",
            "import io.papermc.",
            "import io.lumine.",
            "import net.Indyuce.",
            "import com.ticxo.",
            "import com.mythiccraft."
        )
        val violations = fileTree("omnipet-core/src") { include("**/*.java") }.flatMap { file ->
            file.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line ->
                if (bannedImports.any(line.trimStart()::startsWith)) "${file}:${index + 1}: $line" else null
            }
        }
        check(violations.isEmpty()) { "Core platform/vendor imports found:\n${violations.joinToString("\n")}" }
    }
}

val checkGradleOnly = tasks.register("checkGradleOnly") {
    group = "verification"
    description = "Rejects Maven metadata, wrappers, and target workflow artifacts."
    doLast {
        val ignoredDirectories = setOf(".git", ".gradle", "build")
        val forbidden = repositoryRoot.toFile()
            .walkTopDown()
            .onEnter { directory -> directory == repositoryRoot.toFile() || directory.name !in ignoredDirectories }
            .filter { candidate ->
                candidate != repositoryRoot.toFile() && candidate.name !in ignoredDirectories &&
                        (candidate.name == "pom.xml" || candidate.name == "mvnw" || candidate.name == "mvnw.cmd" ||
                                candidate.name == "dependency-reduced-pom.xml" ||
                                (candidate.isDirectory && (candidate.name == ".mvn" || candidate.name == "target")))
            }
            .toList()
        check(forbidden.isEmpty()) { "Gradle-only policy violation: ${forbidden.joinToString()}" }
    }
}

val checkBranding = tasks.register("checkBranding") {
    group = "verification"
    description = "Rejects stale owned branding from the authoritative modules."
    doLast {
        val violations = fileTree(rootDir) {
            include("omnipet-core/src/**/*.java", "omnipet-paper/src/**/*.java", "omnipet-paper/src/**/*.yml")
        }.flatMap { file ->
            file.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line ->
                if (line.contains("PassivePet") || line.contains("io.github.nahkd123.comm")) {
                    "${file}:${index + 1}: $line"
                } else null
            }
        }
        check(violations.isEmpty()) { "Stale branding found:\n${violations.joinToString("\n")}" }
    }
}

val checkDistributionArtifact = tasks.register("checkDistributionArtifact") {
    group = "verification"
    description = "Verifies the single installable OmniPet distribution JAR."
    dependsOn(":omnipet-paper:jar")
    doLast {
        val expected = distributionJar.asFile
        check(expected.isFile) { "Distribution JAR not found: $expected" }
        ZipFile(expected).use { jar ->
            check(jar.getEntry("paper-plugin.yml") != null) { "paper-plugin.yml missing from distribution" }
            val forbiddenPrefixes = listOf(
                "org/bukkit/",
                "io/papermc/",
                "io/lumine/",
                "net/Indyuce/",
                "META-INF/maven/"
            )
            val forbidden = jar.entries().asSequence().map { it.name }
                .filter { name -> forbiddenPrefixes.any(name::startsWith) }.toList()
            check(forbidden.isEmpty()) { "Forbidden server/vendor/Maven metadata bundled in distribution: ${forbidden.take(10)}" }
        }
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
    dependsOn(checkCoreBoundary, checkGradleOnly, checkBranding, checkDistributionArtifact)
}

tasks.register<Sync>("copyReleaseArtifact") {
    dependsOn(checkDistributionArtifact)
    from(distributionJar)
    into(layout.buildDirectory.dir("release"))
}

tasks.named("assemble") {
    dependsOn(":omnipet-paper:assemble")
}

tasks.named("build") {
    dependsOn(":omnipet-core:build", ":omnipet-paper:build", "copyReleaseArtifact")
}

tasks.named<Delete>("clean") {
    dependsOn(subprojects.map { "${it.path}:clean" })
}
