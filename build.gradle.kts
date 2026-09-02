import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // ADR 0002 D1: intellijIdeaCommunity() is deprecated by the Gradle plugin in favour
        // of intellijIdea(), which resolves the unified IU-coded distribution starting at
        // 2025.3. We intentionally keep using the deprecated Community-only helper here: it
        // is the only way to get a compile classpath that physically lacks Ultimate classes,
        // which is the enforcement mechanism for this project's CE-only constraint. Do not
        // "fix" this deprecation warning. See docs/adr/0002-build-and-platform-baseline.md.
        @Suppress("DEPRECATION")
        intellijIdeaCommunity("2025.2.6.3")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.6.3")
        }
    }
}

