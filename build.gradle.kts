import net.darkhax.curseforgegradle.TaskPublishCurseForge
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import java.io.File

plugins {
    java
    `java-library`
    id("net.neoforged.moddev") version "2.0.141" apply false
    id("net.darkhax.curseforgegradle") version "1.3.33" apply false
}

fun requiredString(name: String): String =
    findProperty(name)?.toString() ?: throw GradleException("Missing Gradle property: $name")

fun optionalInt(name: String): Int? =
    findProperty(name)?.toString()?.toInt()

// Comma separated because entries such as "Java 25" contain spaces.
fun optionalStringList(name: String, fallback: List<String> = emptyList()): List<String> =
    (findProperty(name)?.toString() ?: "")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { fallback }

fun File.collectPngResourceNames(): List<String> {
    if (!exists()) {
        return emptyList()
    }
    return walkTopDown()
        .filter { it.isFile && it.extension == "png" }
        .map { it.relativeTo(this).invariantSeparatorsPath.removeSuffix(".png") }
        .distinct()
        .sorted()
        .toList()
}

fun collectPngResourceNames(resourceRoots: List<File>, relativePath: String): List<String> =
    resourceRoots.asSequence()
        .map { File(it, relativePath) }
        .filter { it.exists() }
        .flatMap { it.collectPngResourceNames().asSequence() }
        .distinct()
        .sorted()
        .toList()

fun buildGeneratedComponentAtlasRegistrationSource(
    packageName: String,
    sprites: List<String>
): String = buildString {
    appendLine("package $packageName;")
    appendLine()
    appendLine("public final class GeneratedComponentAtlasRegistration {")
    appendLine("    private GeneratedComponentAtlasRegistration() {")
    appendLine("    }")
    appendLine()
    appendLine("    public static void register(RegisterComponentSpritesEvent event) {")
    for (sprite in sprites) {
        appendLine("        event.register(\"$sprite\");")
    }
    appendLine("    }")
    appendLine("}")
}

fun writeIfChanged(target: File, content: String) {
    target.parentFile.mkdirs()
    if (!target.exists() || target.readText() != content) {
        target.writeText(content)
    }
}

group = requiredString("root_package")
version = requiredString("mod_version")

val isVersionProject = project != rootProject && findProperty("minecraft_version") != null

if (isVersionProject) {
    val modId = requiredString("mod_id")
    val modName = requiredString("mod_name")
    val modVersion = requiredString("mod_version")
    val modAuthors = requiredString("mod_authors")
    val modDescription = requiredString("mod_description")
    val minecraftVersion = requiredString("minecraft_version")
    val minecraftVersionRange = requiredString("minecraft_version_range")
    val loaderVersionRange = requiredString("loader_version_range")
    val neoVersionRange = requiredString("neo_version_range")
    val resourcePackFormat = requiredString("resource_pack_format")
    val configuredJavaVersion = optionalInt("java_version") ?: 25
    val configuredRunJavaVersion = optionalInt("run_java_version") ?: configuredJavaVersion
    val mixinConfigFile = "mixins.$modId.json"

    val sharedJavaDir = rootProject.file("src/main/java")
    val sharedResourcesDir = rootProject.file("src/main/resources")
    val localJavaDir = file("src/main/java")
    val localResourcesDir = file("src/main/resources")
    val javaRoots = listOf(sharedJavaDir, localJavaDir)
        .distinctBy { it.absolutePath }
        .filter { it.exists() }
    val resourceRoots = listOf(sharedResourcesDir, localResourcesDir)
        .distinctBy { it.absolutePath }
        .filter { it.exists() }
    val generatedComponentAtlasDir = layout.buildDirectory.dir("generated/sources/componentAtlas/main/java")

    apply(plugin = "net.neoforged.moddev")

    base {
        archivesName.set("$modId-$minecraftVersion")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
            vendor.set(JvmVendorSpec.AZUL)
        }
    }

    val runtimeJavaLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(configuredRunJavaVersion))
        vendor.set(JvmVendorSpec.AZUL)
    }

    the<JavaPluginExtension>().sourceSets.named("main") {
        java.setSrcDirs(javaRoots + generatedComponentAtlasDir.get().asFile)
        resources.setSrcDirs(resourceRoots)
    }

    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven("https://cfa2.cursemaven.com") {
                    name = "CurseMaven"
                    metadataSources {
                        mavenPom()
                        artifact()
                        ignoreGradleMetadataRedirection()
                    }
                }
            }
            filter {
                includeGroup("curse.maven")
            }
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged Releases"
            content {
                includeGroupByRegex("net\\.neoforged(\\..+)?")
            }
        }
        maven("https://repo.spongepowered.org/maven") {
            name = "SpongePowered"
            content {
                includeGroup("org.spongepowered")
            }
        }
        maven("https://maven.theillusivec4.top/") {
            name = "Curios"
        }
    }

    dependencies {
        compileOnlyApi("org.jetbrains:annotations:24.1.0")
        annotationProcessor("org.jetbrains:annotations:24.1.0")
        compileOnly("org.spongepowered:mixin:0.8.5")
        annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    }

    val rootDependenciesFile = rootProject.file("dependencies.gradle")
    if (rootDependenciesFile.exists()) {
        apply(from = rootDependenciesFile)
    }

    val localDependenciesFile = file("dependencies.gradle")
    if (localDependenciesFile.exists() && localDependenciesFile != rootDependenciesFile) {
        apply(from = localDependenciesFile)
    }

    apply(from = rootProject.file("gradle/scripts/platform-neoforge.gradle"))

    val generatedComponentAtlasPackage = "com.circulation.circulation_networks.gui.component.base"
    val generatedComponentAtlasFile = generatedComponentAtlasDir.map {
        it.file(generatedComponentAtlasPackage.replace('.', '/') + "/GeneratedComponentAtlasRegistration.java").asFile
    }
    val atlasComponentRelativePath = "assets/$modId/textures/gui/component"
    val componentAtlasInputDirs = resourceRoots
        .map { File(it, atlasComponentRelativePath) }
        .filter { it.exists() }

    val generateComponentAtlasRegistration = tasks.register("generateComponentAtlasRegistration") {
        group = "build setup"
        inputs.files(componentAtlasInputDirs)
        outputs.file(generatedComponentAtlasFile)

        doLast {
            writeIfChanged(
                generatedComponentAtlasFile.get(),
                buildGeneratedComponentAtlasRegistrationSource(
                    generatedComponentAtlasPackage,
                    collectPngResourceNames(resourceRoots, atlasComponentRelativePath)
                )
            )
        }
    }

    tasks.withType<Jar>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    tasks.withType<ProcessResources>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    tasks.withType<JavaExec>().configureEach {
        javaLauncher.set(runtimeJavaLauncher)
    }
    tasks.withType<Test>().configureEach {
        javaLauncher.set(runtimeJavaLauncher)
    }
    tasks.named("compileJava") {
        dependsOn(generateComponentAtlasRegistration)
    }
    val collectVersionBuildArtifacts =
        rootProject.tasks.findByName("collectVersionBuildArtifacts")?.let {
            rootProject.tasks.named(it.name)
        } ?: rootProject.tasks.register("collectVersionBuildArtifacts") {
            group = "build"
            description = "Collect built child version artifacts into the root libs directory."
        }
    val syncBuildArtifactsToRoot = rootProject.tasks.register<Copy>(
        "copy${project.path.replace(':', '_').replace('.', '_').replace('-', '_')}BuildArtifactsToRoot"
    ) {
        group = "build"
        description = "Copy ${project.path} build artifacts into the root libs directory."
        dependsOn(tasks.named("jar"))
        into(rootProject.layout.buildDirectory.dir("libs"))
        from(layout.buildDirectory.dir("libs")) {
            include("*.jar")
        }
    }
    collectVersionBuildArtifacts.configure {
        dependsOn(syncBuildArtifactsToRoot)
    }
    tasks.named("build") {
        finalizedBy(syncBuildArtifactsToRoot)
    }

    tasks.named<ProcessResources>("processResources") {
        val expansionMap = mapOf(
            "mod_id" to modId,
            "mod_name" to modName,
            "mod_version" to modVersion,
            "mod_authors" to modAuthors.split(',').joinToString(", ") { it.trim() },
            "mod_description" to modDescription,
            "minecraft_version_range" to minecraftVersionRange,
            "loader_version_range" to loaderVersionRange,
            "neo_version_range" to neoVersionRange,
            "resource_pack_format" to resourcePackFormat
        )

        inputs.properties(expansionMap)
        exclude("mcmod.info", "META-INF/mods.toml")
        filesMatching(listOf("pack.mcmeta", "META-INF/neoforge.mods.toml", mixinConfigFile)) {
            expand(expansionMap)
        }
    }

    // CurseForge publishing. Opt-in: this only reaches the network when invoked as
    // ./gradlew :<mc version>:publishCurseForge with CURSEFORGE_TOKEN set.
    // Everything it uploads comes from versions/<mc version>/gradle.properties.
    tasks.register<TaskPublishCurseForge>("publishCurseForge") {
        group = "publishing"
        description = "Uploads $modName $modVersion for Minecraft $minecraftVersion to CurseForge."

        val releaseJar = project.tasks.named<Jar>("jar")
        val curseforgeProjectId = requiredString("curseforge_project_id")
        val gameVersions = optionalStringList("curseforge_game_versions", listOf(minecraftVersion))
        val modLoaders = optionalStringList("curseforge_mod_loaders", listOf("NeoForge"))
        val javaVersions = optionalStringList("curseforge_java_versions")
        val requiredRelations = optionalStringList("curseforge_relations_required")
        val optionalRelations = optionalStringList("curseforge_relations_optional")
        val incompatibleRelations = optionalStringList("curseforge_relations_incompatible")

        val declaredReleaseType = findProperty("release_type")?.toString()?.trim()?.lowercase().orEmpty()
        val releaseType = declaredReleaseType.ifEmpty {
            when {
                modVersion.contains("alpha", ignoreCase = true) -> "alpha"
                modVersion.contains("beta", ignoreCase = true) ||
                    modVersion.contains("pre", ignoreCase = true) ||
                    modVersion.contains("rc", ignoreCase = true) -> "beta"
                else -> "release"
            }
        }
        if (releaseType !in listOf("alpha", "beta", "release")) {
            throw GradleException("release_type must be one of alpha, beta, release: $releaseType")
        }

        val changelogPath = findProperty("curseforge_changelog_file")?.toString()?.trim()
        val changelogFile = changelogPath?.takeIf { it.isNotEmpty() }?.let { project.file(it) }
        if (changelogFile != null && !changelogFile.isFile) {
            throw GradleException("curseforge_changelog_file does not exist: $changelogFile")
        }

        dependsOn(releaseJar)
        apiToken = project.providers.environmentVariable("CURSEFORGE_TOKEN")
        debugMode = findProperty("curseforge_debug")?.toString()?.toBoolean() ?: false
        // Every value below is declared here, so the plugin must not guess any of them.
        disableVersionDetection()

        val artifact = upload(curseforgeProjectId, releaseJar.get())
        artifact.releaseType = releaseType
        artifact.changelogType = "markdown"
        if (changelogFile != null) {
            artifact.changelog = changelogFile
        }
        gameVersions.forEach { artifact.addGameVersion(it) }
        modLoaders.forEach { artifact.addModLoader(it) }
        javaVersions.forEach { artifact.addJavaVersion(it) }
        artifact.addEnvironment("Client", "Server")
        requiredRelations.forEach { artifact.addRequirement(it) }
        optionalRelations.forEach { artifact.addOptional(it) }
        incompatibleRelations.forEach { artifact.addIncompatibility(it) }

        doFirst {
            if (!project.providers.environmentVariable("CURSEFORGE_TOKEN").isPresent) {
                throw GradleException("CURSEFORGE_TOKEN is required to publish to CurseForge.")
            }
        }
    }
}
