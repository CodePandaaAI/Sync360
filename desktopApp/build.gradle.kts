import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_23)
    }
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.components.resources)
    
    // Koin Dependency Injection
    implementation(libs.koin.core)
}

compose.desktop {
    application {
        mainClass = "com.liftley.sync360.MainKt"
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            $$"-splash:$APPDIR/resources/sync360-splash.png"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Sync360"
            packageVersion = "0.3.0"
            appResourcesRootDir.set(
                project.layout.projectDirectory.dir("packaging/app-resources")
            )

            macOS {
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
                shortcut = true
                menu = true
                menuGroup = "Sync360"
                upgradeUuid = "7f48cc4d-365c-4f59-96e6-3a2f6d794847"
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
            }
        }
    }
}
