import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("com.diffplug.spotless")
}

dependencies {
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        // Marketplace description is sourced from README.md between the marker comments
        description =
            providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"
                with(it.lines()) {
                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("README.md is missing the plugin description section:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
                }
            }

        changeNotes =
            provider {
                with(changelog) {
                    renderItem(
                        (getOrNull(project.version.toString()) ?: getUnreleased())
                            .withHeader(false)
                            .withEmptySections(false),
                        org.jetbrains.changelog.Changelog.OutputType.HTML,
                    )
                }
            }
    }
}

// pin JVM 21
kotlin {
    jvmToolchain(21)
}

// ktlint-backed formatting: `./gradlew spotlessApply` to fix, `spotlessCheck` (wired into `check`) to verify.
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
