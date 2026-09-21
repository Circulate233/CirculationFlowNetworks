pluginManagement {
    repositories {
        maven("https://nexus.gtnewhorizons.com/repository/public/") {
            name = "GTNH Maven"
            content {
                includeGroup("com.gtnewhorizons")
                includeGroup("com.gtnewhorizons.retrofuturagradle")
            }
        }
        maven("https://maven.kikugie.dev/releases") {
            name = "KikuGie Releases"
        }
        maven("https://maven.kikugie.dev/snapshots") {
            name = "KikuGie Snapshots"
        }
        maven("https://maven.minecraftforge.net/") {
            name = "MinecraftForge Plugins"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged Plugins"
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        // On the settings classpath so that gradle/scripts/curseforge.gradle, which is applied as a
        // script plugin, can reference TaskPublishCurseForge (applied scripts do not see the
        // classpath contributed by a `plugins` block or by a project level buildscript block).
        classpath("net.darkhax.curseforgegradle:CurseForgeGradle:1.3.33")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.1-beta.2"
}

rootProject.name = rootDir.name

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(getRootProject()) {
        versions("1.12.2", "1.20.1", "1.21.1")
        vcsVersion = "1.12.2"
    }
}
