@file:Suppress("UNCHECKED_CAST")

import com.gtnewhorizons.retrofuturagradle.mcp.InjectTagsTask
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.lang.Binding
import groovy.lang.GroovyObject
import groovy.lang.GroovyShell
import groovy.text.SimpleTemplateEngine
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.Copy
import java.util.Properties

plugins {
    java
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.2.10"
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.3"
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.2" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.141" apply false
    id("com.matthewprenger.cursegradle") version "1.4.0" apply false
    id("com.modrinth.minotaur") version "2.+" apply false
    id("org.jetbrains.changelog") version "2.5.0"
}

val currentPlatform = (findProperty("platform") ?: "rfg").toString()
val isLegacyRfg = currentPlatform == "rfg"
val sharedResourcesDir = rootProject.file("src/main/resources")
val localResourcesDir = file("src/main/resources")
val mainResourceRoots = listOf(localResourcesDir, sharedResourcesDir)
    .distinctBy { it.absolutePath }
val activeResourcesDir = if (localResourcesDir.exists()) localResourcesDir else sharedResourcesDir
val generatedComponentAtlasDir = layout.buildDirectory.dir("generated/sources/componentAtlas/main/java")
val rootDependenciesFile = rootProject.file("dependencies.gradle")
val localDependenciesFile = file("dependencies.gradle")

fun resolvePropertyValue(key: String): Any? {
    val value = findProperty(key)
    return if (value is String) interpolate(value) else value
}

fun interpolate(value: String): String {
    if (value.startsWith($$"${{") && value.endsWith("}}")) {
        val expression = value.substring(3, value.length - 2)
        val binding = Binding()
        project.properties.forEach { (k, v) -> binding.setProperty(k, v) }
        binding.setProperty("it", project)
        return GroovyShell(javaClass.classLoader, binding).evaluate(expression).toString()
    }
    if (value.contains($$"${")) {
        return SimpleTemplateEngine().createTemplate(value).make(project.properties).toString()
    }
    return value
}

fun propertyString(key: String): String = resolvePropertyValue(key)?.toString()
    ?: throw GradleException("Property $key is not defined!")

fun propertyBool(key: String): Boolean = propertyString(key).toBoolean()

fun propertyStringList(key: String, delimiter: String = " "): List<String> =
    propertyString(key).split(delimiter).filter { it.isNotEmpty() }

fun hasJavaSources(dir: File): Boolean = dir.exists() && dir.walkTopDown().any { it.isFile && it.extension == "java" }

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
        appendLine("        event.register(\"${escapeJavaString(sprite)}\");")
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

fun escapeJavaString(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            else -> append(ch)
        }
    }
}


fun mixinEnvToBlockName(envName: String): String = when (envName.uppercase()) {
    "DEFAULT", "MAIN" -> "mixins"
    "CLIENT" -> "client"
    "SERVER" -> "server"
    else -> "mixins"
}

data class MixinRegistration(val alias: String, val block: String, val className: String)

fun File.toMixinClassName(packageRoot: File): String =
    relativeTo(packageRoot).path.removeSuffix(".$extension").replace('\\', '.').replace('/', '.')

fun collectMixinRegistrations(): List<MixinRegistration> {
    val mixinPackageRoot = propertyString("mixin_package")
    val packagePath = mixinPackageRoot.replace('.', File.separatorChar)
    val annotationPattern = Regex("@(?:[\\w.]+\\.)?MixinEnvironment\\((.*?)\\)", setOf(RegexOption.DOT_MATCHES_ALL))
    val aliasPattern = Regex("value\\s*=\\s*\"([^\"]+)\"|\"([^\"]+)\"")
    val typePattern = Regex("type\\s*=\\s*(?:[\\w.]+\\.)?(DEFAULT|MAIN|CLIENT|SERVER)")
    val ignorePattern = Regex("@(?:[\\w.]+\\.)?MixinIgnore\\b")
    val sourceRoots = listOf(
        file("src/main/java"),
        rootProject.file("src/main/java"),
        file("src/main/kotlin"),
        rootProject.file("src/main/kotlin")
    )

    return sourceRoots
        .asSequence()
        .map { File(it, packagePath) }
        .filter { it.exists() }
        .flatMap { packageRoot ->
            packageRoot.walkTopDown()
                .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
                .mapNotNull { sourceFile ->
                    val sourceText = sourceFile.readText()
                    if (ignorePattern.containsMatchIn(sourceText)) {
                        null
                    } else {
                        val args = annotationPattern.find(sourceText)?.groupValues?.get(1).orEmpty()
                        val aliasMatch = aliasPattern.find(args)
                        val alias = aliasMatch?.groups?.get(1)?.value ?: aliasMatch?.groups?.get(2)?.value ?: "default"
                        val env = typePattern.find(args)?.groupValues?.get(1) ?: "DEFAULT"
                        MixinRegistration(alias, mixinEnvToBlockName(env), sourceFile.toMixinClassName(packageRoot))
                    }
                }
        }
        .distinctBy { Triple(it.alias, it.block, it.className) }
        .sortedBy { it.className }
        .toList()
}

fun createDefaultMixinConfig(): MutableMap<String, Any> = mutableMapOf(
    "package" to propertyString("mixin_package"),
    "required" to true,
    "refmap" to propertyString("mixin_refmap"),
    "minVersion" to "0.8.5",
    "compatibilityLevel" to mixinCompatibilityLevel,
    "mixins" to mutableListOf<String>(),
    "server" to mutableListOf<String>(),
    "client" to mutableListOf<String>()
)

fun resolveMixinConfigFile(alias: String): File {
    val configs = propertyStringList("mixin_configs")
    val configName = when (alias) {
        "default" -> configs.first()
        in configs -> alias
        else -> throw GradleException("Mixin alias $alias is not present in mixin_configs")
    }
    return File(activeResourcesDir, "mixins.$configName.json")
}

fun assertProperty(propertyName: String) {
    val property = resolvePropertyValue(propertyName)?.toString()
        ?: throw GradleException("Property $propertyName is not defined!")
    if (property.isEmpty()) {
        throw GradleException("Property $propertyName is empty!")
    }
}

fun assertSubProperties(propertyName: String, vararg subPropertyNames: String) {
    assertProperty(propertyName)
    if (propertyBool(propertyName)) {
        subPropertyNames.forEach(::assertProperty)
    }
}

fun setDefaultProperty(propertyName: String, warn: Boolean, defaultValue: Any) {
    val property = resolvePropertyValue(propertyName)?.toString()
    val exists = !property.isNullOrEmpty()
    if (!exists && warn) {
        logger.log(LogLevel.WARN, "Property $propertyName is not defined or empty!")
    }
    if (!exists) {
        project.extensions.extraProperties.set(propertyName, defaultValue.toString())
    }
}

fun assertEnvironmentVariable(propertyName: String) {
    val property = System.getenv(propertyName)
        ?: throw GradleException("System Environment Variable $propertyName is not defined!")
    if (property.isEmpty()) {
        throw GradleException("Property $propertyName is empty!")
    }
}

if (isLegacyRfg) {
    apply(plugin = "com.gtnewhorizons.retrofuturagradle")
}

assertProperty("mod_version")
assertProperty("root_package")
assertProperty("mod_id")
assertProperty("mod_name")

assertSubProperties("use_tags", "tag_class_name")
assertSubProperties("use_access_transformer", "access_transformer_locations")
assertSubProperties("use_mixins", "mixin_booter_version", "mixin_refmap")
assertSubProperties("is_coremod", "coremod_includes_mod", "coremod_plugin_class_name")
assertSubProperties("use_asset_mover", "asset_mover_version")

setDefaultProperty("use_modern_java_syntax", warn = false, defaultValue = false)
setDefaultProperty("generate_sources_jar", warn = true, defaultValue = false)
setDefaultProperty("generate_javadocs_jar", warn = true, defaultValue = false)
setDefaultProperty("mapping_channel", warn = true, defaultValue = "stable")
setDefaultProperty("mapping_version", warn = true, defaultValue = "39")
setDefaultProperty("use_dependency_at_files", warn = true, defaultValue = true)
setDefaultProperty("minecraft_username", warn = true, defaultValue = "Developer")
setDefaultProperty("extra_jvm_args", warn = false, defaultValue = "")
setDefaultProperty("extra_tweak_classes", warn = false, defaultValue = "")
setDefaultProperty("change_minecraft_sources", warn = false, defaultValue = false)
setDefaultProperty("minecraft_version_range", warn = false, defaultValue = "")
setDefaultProperty("loader_version_range", warn = false, defaultValue = "")
setDefaultProperty("resource_pack_format", warn = false, defaultValue = "3")
setDefaultProperty(
    "mixin_package",
    warn = false,
    defaultValue = propertyString("root_package") + "." + propertyString("mod_id") + ".mixins"
)

version = propertyString("mod_version")
group = propertyString("root_package")


val configuredJavaVersion = (findProperty("java_version")?.toString()
    ?: if (propertyBool("use_modern_java_syntax")) "16" else "8").toInt()

val configuredRunJavaVersion = (findProperty("run_java_version")?.toString()
    ?: configuredJavaVersion.toString()).toInt()

val runtimeJavaLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(if (isLegacyRfg) 8 else configuredRunJavaVersion))
    vendor.set(JvmVendorSpec.AZUL)
}

val mixinCompatibilityLevel = if (isLegacyRfg) "JAVA_8" else "JAVA_17"
val mixinDebugJvmArgs = listOf(
    "-Dmixin.hotSwap=true",
    "-Dmixin.checks.interfaces=true",
    "-Dmixin.debug.export=true"
)
val mixinSourcePath = propertyString("mixin_package").replace('.', '/')
val hasMixinSources = sequenceOf(
    file("src/main/java/$mixinSourcePath"),
    rootProject.file("src/main/java/$mixinSourcePath")
).any(::hasJavaSources)

base {
    archivesName.set(propertyString("mod_id") + "-" + propertyString("minecraft_version"))
}

if (isLegacyRfg) {
    tasks.named("decompressDecompiledSources") {
        enabled = !propertyBool("change_minecraft_sources")
    }
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
        vendor.set(JvmVendorSpec.AZUL)
    }
    if (propertyBool("generate_sources_jar")) {
        withSourcesJar()
    }
    if (propertyBool("generate_javadocs_jar")) {
        withJavadocJar()
    }
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.withType<ProcessResources>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val embed by configurations.creating
configurations.named(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME) {
    extendsFrom(embed)
}

if (isLegacyRfg) {
    val minecraftExtension = extensions.getByName("minecraft") as GroovyObject
    (minecraftExtension.getProperty("mcVersion") as Property<String>)
        .set((findProperty("minecraft_version") ?: "1.12.2").toString())
    (minecraftExtension.getProperty("mcpMappingChannel") as Property<String>)
        .set(propertyString("mapping_channel"))
    (minecraftExtension.getProperty("mcpMappingVersion") as Property<String>)
        .set(propertyString("mapping_version"))
    (minecraftExtension.getProperty("useDependencyAccessTransformers") as Property<Boolean>)
        .set(propertyBool("use_dependency_at_files"))
    (minecraftExtension.getProperty("username") as Property<String>)
        .set(propertyString("minecraft_username"))
    (minecraftExtension.getProperty("extraTweakClasses") as ListProperty<String>)
        .addAll(propertyStringList("extra_tweak_classes"))

    run {
        val args = mutableListOf("-ea:$group")
        if (propertyBool("use_mixins")) {
            args += mixinDebugJvmArgs
        }

        (minecraftExtension.getProperty("extraRunJvmArguments") as ListProperty<String>)
            .addAll(args)
        (minecraftExtension.getProperty("extraRunJvmArguments") as ListProperty<String>)
            .addAll(propertyStringList("extra_jvm_args"))

        if (propertyBool("use_tags")) {
            val tagsFile = rootProject.file("tags.properties")
            if (tagsFile.exists()) {
                val props = Properties().apply {
                    tagsFile.inputStream().use(::load)
                }
                if (props.isNotEmpty()) {
                    val mapped = props.entries.associate { it.key.toString() to interpolate(it.value.toString()) }
                    (minecraftExtension.getProperty("injectedTags") as MapProperty<String, String>)
                        .set(mapped)
                }
            }
        }
    }
}

val platformScript = rootProject.file("gradle/scripts/platform-$currentPlatform.gradle")
if (platformScript.exists()) {
    apply(from = platformScript)
}

if (currentPlatform == "legacyforge" && propertyBool("use_mixins") && hasMixinSources) {
    val mainSourceSet = the<JavaPluginExtension>().sourceSets.getByName("main")
    val mixinExtension = extensions.getByName("mixin") as GroovyObject
    mixinExtension.invokeMethod("add", arrayOf(mainSourceSet, propertyString("mixin_refmap")))
    propertyStringList("mixin_configs").forEach { mixinConfig ->
        mixinExtension.invokeMethod("config", arrayOf("mixins.$mixinConfig.json"))
    }
}

the<JavaPluginExtension>().sourceSets.named("main") {
    java.srcDir(generatedComponentAtlasDir)
    resources.setSrcDirs(mainResourceRoots)
}

val sharedTestJavaDir = rootProject.file("src/test/java")
val localTestJavaDir = file("src/test/java")
val sharedTestResourcesDir = rootProject.file("src/test/resources")
val localTestResourcesDir = file("src/test/resources")

// Keep test sources explicit so version-specific regression tests can live in the repo.
the<JavaPluginExtension>().sourceSets.matching { it.name == "test" }.all {
    java.setSrcDirs(
        listOf(localTestJavaDir, sharedTestJavaDir)
            .distinctBy { it.absolutePath }
            .filter { it.exists() }
    )
    resources.setSrcDirs(
        listOf(localTestResourcesDir, sharedTestResourcesDir)
            .distinctBy { it.absolutePath }
            .filter { it.exists() }
    )
}

val generatedComponentAtlasPackage = "com.circulation.circulation_networks.gui.component.base"
val generatedComponentAtlasFile = generatedComponentAtlasDir.map {
    it.file(generatedComponentAtlasPackage.replace('.', '/') + "/GeneratedComponentAtlasRegistration.java").asFile
}
val atlasResourceRoots = mainResourceRoots.filter { it.exists() }
val atlasComponentRelativePath = "assets/${propertyString("mod_id")}/textures/gui/component"
val componentAtlasInputDirs = atlasResourceRoots.map { File(it, atlasComponentRelativePath) }.filter { it.exists() }

repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "CurseMaven"
                url = uri("https://cfa2.cursemaven.com")
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
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }

    flatDir {
        dirs("lib")
    }
    maven {
        url = uri("https://maven.aliyun.com/nexus/content/groups/public/")
    }
    maven {
        url = uri("https://maven.aliyun.com/nexus/content/repositories/jcenter")
    }
    maven {
        url = uri("https://maven.cleanroommc.com")
    }
    maven {
        name = "MinecraftForge"
        url = uri("https://maven.minecraftforge.net")
    }
    maven {
        name = "ModMaven" //EnderCore
        url  = uri("https://modmaven.dev/")
    }
    maven {
        name = "NeoForged Releases"
        url = uri("https://maven.neoforged.net/releases")
    }
    maven {
        url = uri("https://maven.blamejared.com/")
    }
    maven {
        name = "Curios"
        url = uri("https://maven.theillusivec4.top/")
    }
    maven {
        name = "Covers1624 Maven"
        url = uri("https://maven.covers1624.net/")
    }
    maven {
        url = uri("https://repo.spongepowered.org/maven")
        content {
            includeGroup("org.spongepowered")
        }
    }
    maven {
        name = "GeckoLib"
        url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    }
    maven {
        name = "OvermindDL1 Maven"
        url = uri("https://gregtech.overminddl1.com/")
        mavenContent {
            excludeGroup("net.minecraftforge") // missing the `universal` artefact
        }
    }
    maven {
        name = "Zeitheron Maven"
        url = uri("https://maven.zeith.org")
    }
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
        content {
            includeGroup("com.github.bsideup.jabel")
        }
    }
    mavenLocal()
}

if (rootDependenciesFile.exists()) {
    apply(from = rootDependenciesFile)
}
if (localDependenciesFile.exists() && localDependenciesFile != rootDependenciesFile) {
    apply(from = localDependenciesFile)
}

dependencies {
    if (isLegacyRfg) {
        if (propertyBool("use_modern_java_syntax")) {
            annotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:1.0.1")
            annotationProcessor("net.java.dev.jna:jna-platform:5.13.0")
            compileOnly("com.github.bsideup.jabel:jabel-javac-plugin:1.0.1") {
                isTransitive = false
            }
            add("patchedMinecraft", "me.eigenraven.java8unsupported:java-8-unsupported-shim:1.0.0")
            add("testAnnotationProcessor", "com.github.bsideup.jabel:jabel-javac-plugin:1.0.1")
            add("testCompileOnly", "com.github.bsideup.jabel:jabel-javac-plugin:1.0.1") {
                isTransitive = false
            }
        }


        if (propertyBool("use_asset_mover")) {
            add("implementation", "com.cleanroommc:assetmover:${propertyString("asset_mover_version")}")
        }

        val modUtils = (project as GroovyObject).getProperty("modUtils") as ModUtils
        val mixin = modUtils.enableMixins(
            "zone.rong:mixinbooter:${propertyString("mixin_booter_version")}",
            propertyString("mixin_refmap")
        )
        val mixinApiDependency = project.dependencies.create(mixin) as ExternalModuleDependency
        mixinApiDependency.isTransitive = false
        val mixinAnnotationProcessorDependency = project.dependencies.create(mixin) as ExternalModuleDependency
        mixinAnnotationProcessorDependency.isTransitive = false

        add("api", mixinApiDependency)
        annotationProcessor("org.ow2.asm:asm-debug-all:5.2")
        annotationProcessor("com.google.guava:guava:32.1.2-jre")
        annotationProcessor("com.google.code.gson:gson:2.8.9")
        add("annotationProcessor", mixinAnnotationProcessorDependency)

    }

    if (propertyBool("use_mixins")) {
        compileOnly("org.spongepowered:mixin:0.8.5")
        annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    }

    if (propertyBool("enable_junit_testing")) {
        testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation("it.unimi.dsi:fastutil:8.5.9")
    }

    compileOnlyApi("org.jetbrains:annotations:24.1.0")
    annotationProcessor("org.jetbrains:annotations:24.1.0")
}

if (isLegacyRfg && propertyBool("use_access_transformer")) {
    propertyStringList("access_transformer_locations").forEach { location ->
        var fileLocation = file("$projectDir/src/main/resources/$location")
        if (!fileLocation.exists()) {
            fileLocation = rootProject.file("src/main/resources/$location")
        }

        if (!fileLocation.exists()) {
            throw GradleException("Access Transformer file [$fileLocation] does not exist!")
        }

        tasks.named("deobfuscateMergedJarToSrg") {
            val accessTransformerFiles = (this as GroovyObject)
                .getProperty("accessTransformerFiles") as ConfigurableFileCollection
            accessTransformerFiles.from(fileLocation)
        }
        tasks.named("srgifyBinpatchedJar") {
            val accessTransformerFiles = (this as GroovyObject)
                .getProperty("accessTransformerFiles") as ConfigurableFileCollection
            accessTransformerFiles.from(fileLocation)
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateMixinJson)

    val expansionMap = mutableMapOf(
        "mod_id" to propertyString("mod_id"),
        "mod_name" to propertyString("mod_name"),
        "mod_version" to propertyString("mod_version"),
        "minecraft_version" to propertyString("minecraft_version"),
        "minecraft_version_range" to propertyString("minecraft_version_range"),
        "loader_version_range" to propertyString("loader_version_range"),
        "resource_pack_format" to propertyString("resource_pack_format"),
        "mod_description" to propertyString("mod_description"),
        "mod_authors" to propertyStringList("mod_authors", ",").joinToString(", "),
        "mod_credits" to propertyString("mod_credits"),
        "mod_url" to propertyString("mod_url"),
        "mod_update_json" to propertyString("mod_update_json"),
        "mod_logo_path" to propertyString("mod_logo_path"),
        "mixin_refmap" to propertyString("mixin_refmap"),
        "mixin_package" to propertyString("mixin_package")
    )
    if (currentPlatform in listOf("forgegradle", "legacyforge")) {
        expansionMap["forge_version_range"] = propertyString("forge_version_range")
    }
    if (currentPlatform == "neoforge") {
        expansionMap["neo_version_range"] = propertyString("neo_version_range")
    }
    expansionMap["mixin_configs"] = propertyStringList("mixin_configs").joinToString(" ")

    expansionMap.forEach { (key, value) -> inputs.property(key, value) }

    val filterList = mutableListOf("mcmod.info", "pack.mcmeta", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")
    filterList += propertyStringList("mixin_configs").map { "mixins.$it.json" }

    when (currentPlatform) {
        "rfg" -> exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml")
        "forgegradle", "legacyforge" -> exclude("mcmod.info", "META-INF/neoforge.mods.toml")
        "neoforge" -> exclude("mcmod.info", "META-INF/mods.toml")
    }

    filesMatching(filterList) {
        expand(expansionMap)
    }

    if (propertyBool("use_access_transformer")) {
        rename("(.+_at.cfg)", "META-INF/$1")
    }
}

val syncLegacyForgeResourcesToClasses = if (currentPlatform == "legacyforge") {
    tasks.register<Copy>("syncLegacyForgeResourcesToClasses") {
        description = ""
        dependsOn(tasks.named("compileJava"))
        from(tasks.named<ProcessResources>("processResources"))
        into(tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
} else {
    null
}

tasks.named<Jar>("jar") {
    manifest {
        val attributeMap = mutableMapOf<String, Any>()
        if (propertyBool("is_coremod")) {
            attributeMap["FMLCorePlugin"] = propertyString("coremod_plugin_class_name")
            if (propertyBool("coremod_includes_mod")) {
                attributeMap["FMLCorePluginContainsFMLMod"] = true
                val currentTasks = gradle.startParameter.taskNames
                if (currentTasks.isNotEmpty() && currentTasks.first() in listOf(
                        "build",
                        "prepareObfModsFolder",
                        "runObfClient"
                    )
                ) {
                    attributeMap["ForceLoadAsMod"] = true
                }
            }
        }
        if (propertyBool("use_access_transformer")) {
            attributeMap["FMLAT"] = propertyString("access_transformer_locations")
        }
        if (propertyBool("use_mixins") && !isLegacyRfg) {
            attributeMap["MixinConfigs"] = propertyStringList("mixin_configs").joinToString(",") { "mixins.$it.json" }
        }
        attributes(attributeMap)
    }
    from(provider { embed.files.map { if (it.isDirectory) it else zipTree(it) } })
}

if (isLegacyRfg) {
    tasks.named<JavaCompile>("compileTestJava") {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
}

tasks.withType<JavaExec>().configureEach {
    if (syncLegacyForgeResourcesToClasses != null && name in setOf("runClient", "runServer", "runData")) {
        dependsOn(syncLegacyForgeResourcesToClasses)
    }
    if (name in setOf("runClient", "runServer", "runData")) {
        javaLauncher.set(runtimeJavaLauncher)
    }
    if (!isLegacyRfg && propertyBool("use_mixins") && name in setOf("runClient", "runServer")) {
        jvmArgs(mixinDebugJvmArgs)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(if (isLegacyRfg) 8 else configuredJavaVersion))
    })
    if (propertyBool("show_testing_output")) {
        testLogging {
            showStandardStreams = true
        }
    }
}

val generateMixinJson = tasks.register("generateMixinJson") {
    description = ""
    group = "cleanroom helpers"
    onlyIf {
        propertyBool("use_mixins") && propertyBool("generate_mixins_json")
    }
    doLast {
        val registrationsByFile = mutableMapOf<File, MutableMap<String, MutableList<String>>>()

        if (!isLegacyRfg) {
            collectMixinRegistrations().forEach { registration ->
                val mixinFile = resolveMixinConfigFile(registration.alias)
                val blockMap = registrationsByFile.getOrPut(mixinFile) {
                    mutableMapOf(
                        "mixins" to mutableListOf(),
                        "server" to mutableListOf(),
                        "client" to mutableListOf()
                    )
                }
                blockMap.getValue(registration.block).add(registration.className)
            }
        }

        propertyStringList("mixin_configs").forEach { mixinConfig ->
            val mixinFile = File(activeResourcesDir, "mixins.$mixinConfig.json")
            val mixinFileExists = mixinFile.exists()
            val existingConfig: MutableMap<String, Any?> = if (mixinFileExists) {
                @Suppress("UNCHECKED_CAST")
                (JsonSlurper().parse(mixinFile) as Map<String, Any?>).toMutableMap()
            } else {
                createDefaultMixinConfig().toMutableMap()
            }

            existingConfig.putIfAbsent("package", propertyString("mixin_package"))
            existingConfig.putIfAbsent("required", true)
            existingConfig.putIfAbsent("refmap", propertyString("mixin_refmap"))
            existingConfig.putIfAbsent("minVersion", "0.8.5")
            existingConfig["compatibilityLevel"] = mixinCompatibilityLevel
            existingConfig.putIfAbsent("mixins", mutableListOf<String>())
            existingConfig.putIfAbsent("server", mutableListOf<String>())
            existingConfig.putIfAbsent("client", mutableListOf<String>())

            if (!mixinFileExists) {
                val discoveredEntries = registrationsByFile[mixinFile].orEmpty()
                listOf("mixins", "server", "client").forEach { blockName ->
                    val existingEntries = (existingConfig[blockName] as? List<*>)
                        ?.mapNotNull { it?.toString() }
                        .orEmpty()
                    val mergedEntries = (existingEntries + discoveredEntries[blockName].orEmpty())
                        .distinct()
                        .sorted()
                    existingConfig[blockName] = mergedEntries
                }
            }

            mixinFile.parentFile.mkdirs()
            mixinFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(existingConfig)) + System.lineSeparator())
        }
    }
}

val generateComponentAtlasRegistration = tasks.register("generateComponentAtlasRegistration") {
    description = ""
    group = "cleanroom helpers"

    inputs.files(componentAtlasInputDirs)
    outputs.file(generatedComponentAtlasFile)

    doLast {
        val file = generatedComponentAtlasFile.get()
        val staleImpl = File(file.parentFile, "GeneratedComponentAtlasRegistrationImpl.java")
        if (staleImpl.exists()) {
            staleImpl.delete()
        }
        writeIfChanged(
            file,
            buildGeneratedComponentAtlasRegistrationSource(
                generatedComponentAtlasPackage,
                collectPngResourceNames(atlasResourceRoots, atlasComponentRelativePath)
            )
        )
    }
}

tasks.named("compileJava") {
    dependsOn(generateComponentAtlasRegistration)
}

if (syncLegacyForgeResourcesToClasses != null) {
    tasks.named("classes") {
        dependsOn(syncLegacyForgeResourcesToClasses)
    }
}

tasks.matching { it.name == "kspKotlin" }.configureEach {
    dependsOn(generateComponentAtlasRegistration)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (isLegacyRfg && propertyBool("use_modern_java_syntax")) {
        if (name in listOf("compileMcLauncherJava", "compilePatchedMcJava")) {
            return@configureEach
        }
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        options.release.set(8)
        javaCompiler.set(javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(16))
            vendor.set(JvmVendorSpec.AZUL)
        })
    }
}

if (isLegacyRfg) {
    // Kotlin plugin creates compile*Kotlin tasks for RFG source sets that need
    // the same task dependencies as their Java counterparts.
    val rfgKotlinDeps = mapOf(
        "compileMcLauncherKotlin" to "createMcLauncherFiles",
        "compilePatchedMcKotlin" to "decompressDecompiledSources",
        "compileInjectedTagsKotlin" to "injectTags"
    )
    rfgKotlinDeps.forEach { (kotlinTask, dep) ->
        tasks.matching { it.name == kotlinTask }.configureEach {
            dependsOn(dep)
        }
    }
}

// KSP prepareEmptyKspDir must run before any task using KSP output directories
val prepareKsp = tasks.matching { it.name == "prepareEmptyKspDir" }
tasks.withType<AbstractCompile>().configureEach { dependsOn(prepareKsp) }
tasks.matching { it.name.contains("Kotlin") && it.name.startsWith("compile") }.configureEach { dependsOn(prepareKsp) }
tasks.withType<Jar>().configureEach { dependsOn(prepareKsp) }
tasks.withType<AbstractCopyTask>().configureEach { dependsOn(prepareKsp) }

val cleanroomAfterSync = tasks.register("cleanroomAfterSync") {
    description = ""
    group = "cleanroom helpers"
    dependsOn(generateMixinJson)
    dependsOn(generateComponentAtlasRegistration)
    if (isLegacyRfg) {
        dependsOn("injectTags")
    }
}

if (project != rootProject) {
    val aggregateVersionArtifacts = run {
        val taskKey = "aggregateVersionArtifactsProvider"
        val extra = rootProject.extensions.extraProperties
        if (extra.has(taskKey)) {
            @Suppress("UNCHECKED_CAST")
            extra.get(taskKey) as TaskProvider<Copy>
        } else {
            rootProject.tasks.register<Copy>("aggregateVersionArtifacts") {
                group = "build"
                description = "Collect versioned jars into the root build/libs directory."

                into(rootProject.layout.buildDirectory.dir("libs"))
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE

                rootProject.subprojects.forEach { versionProject ->
                    from(versionProject.layout.buildDirectory.dir("libs")) {
                        include("*.jar")
                    }
                    from(versionProject.layout.buildDirectory.dir("devlibs")) {
                        include("*.jar")
                        rename { fileName ->
                            if (fileName.endsWith("-dev.jar")) {
                                fileName
                            } else {
                                fileName.removeSuffix(".jar") + "-dev.jar"
                            }
                        }
                    }
                }
            }.also { extra.set(taskKey, it) }
        }
    }

    tasks.matching { it.name == "assemble" || it.name == "build" }.configureEach {
        finalizedBy(aggregateVersionArtifacts)
    }
}

if (isLegacyRfg && propertyBool("use_modern_java_syntax")) {
    tasks.withType<Javadoc>().configureEach {
        (options as? CoreJavadocOptions)?.source = "17"
    }
}

if (isLegacyRfg) {
    tasks.named<InjectTagsTask>("injectTags") {
        onlyIf {
            propertyBool("use_tags") && !tags.get().isEmpty()
        }
        outputClassName.set(propertyString("tag_class_name"))
    }

    tasks.named("prepareObfModsFolder") {
        finalizedBy("prioritizeCoremods")
    }
}

tasks.register("prioritizeCoremods") {
    description = ""
    if (isLegacyRfg) {
        dependsOn("prepareObfModsFolder")
    }
    doLast {
        fileTree("run/obfuscated").forEach {
            if (it.isFile && Regex("(mixinbooter|configanytime)(-)([0-9])+\\.+([0-9])+(.jar)").matches(it.name)) {
                it.renameTo(File(it.parentFile, "!${it.name}"))
            }
        }
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("stonecutterMerge") }.configureEach {
        val suffix = name.removePrefix("stonecutterMerge")
        listOf(
            "stonecutterGenerate$suffix",
            "compile${suffix}Java", "compile${suffix}Kotlin",
            "process${suffix}Resources"
        ).forEach { taskName ->
            tasks.findByName(taskName)?.mustRunAfter(this)
        }
    }
}

if (isLegacyRfg) {
    apply(from = rootProject.file("gradle/scripts/publishing.gradle"))
}
apply(from = rootProject.file("gradle/scripts/extra.gradle"))
