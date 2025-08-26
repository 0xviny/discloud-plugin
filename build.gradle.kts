plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
    kotlin("plugin.serialization") version "1.9.10"
}

group = "com.discloud-plugin"
version = "1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        implementation("org.json:json:20250517")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        val changeNotes = """
    Initial version
    - Added Discloud tool window to list and manage applications
    - Display app name, status (online/offline), and RAM in the tool window
    - Action buttons: Start, Restart, Stop, Backup and Logs with icons
    - Refresh button to reload the app list
    - Integrated Discloud API client for listing apps, start/restart/stop, downloading backups and viewing logs
    - API token handling: prompts the user if not configured and saves it in Settings
    - Selection validations and user-friendly error/warning messages
    - Commit Discloud: upload artifacts to an existing app via the commit endpoint
    - Added multi-language support for commits: Java (.jar), Go, Rust, Python, PHP and Ruby
    - Automatic runtime/project detection (go.mod, Cargo.toml, requirements.txt, composer.json, Gemfile, pom.xml, build.gradle, file extensions)
    - Attempts to build Go (go build) and Rust (cargo build --release) projects when applicable and uploads the produced binary
    - Fallback to creating and uploading a ZIP of the source code when build is not possible
    - Creates artifacts (JAR, zipped binary, or project ZIP) and uploads via a reusable multipart upload (includes runtime field when applicable)
    - Uses ProgressManager / Task for long running operations and SwingUtilities.invokeLater for UI updates
    - "Upload to Discloud": packages the entire project as a ZIP and calls the /upload endpoint (creates the project on Discloud)
    - Upload only runs if a discloud.config file exists at the project root
    - Smart exclusions when zipping the project (.git, node_modules, venv, __pycache__, target, build, .idea, .gradle)
    - IO and zip utilities to generate consistent packages
    - Uses background threads (ApplicationManager.executeOnPooledThread and AppExecutorUtil) to avoid blocking the UI
    - User feedback via JOptionPane / Messages with results and friendly error messages
    - Robust HTTP response handling and safe JSON parsing for messages displayed to the user
""".trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
