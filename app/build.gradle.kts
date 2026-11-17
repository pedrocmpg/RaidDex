import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.raiddex.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.raiddex.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":data"))
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
}

// R8: lista fechada de permissões. Verifica o manifesto já mesclado (inclui o que as bibliotecas injetam).
val allowedPermissions = setOf(
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.INTERNET",
)

abstract class CheckPermissionsTask : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val allowed: SetProperty<String>

    @TaskAction
    fun check() {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest.get().asFile)
        val found = mutableSetOf<String>()
        for (tag in listOf("uses-permission", "uses-permission-sdk-23")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                found += (nodes.item(i) as org.w3c.dom.Element)
                    .getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            }
        }
        val extra = found - allowed.get()
        if (extra.isNotEmpty()) {
            throw GradleException("R8: permissões fora da lista fechada no manifesto mesclado: $extra")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<CheckPermissionsTask>("checkPermissions$name") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowed.set(allowedPermissions)
        }
        tasks.named("check") { dependsOn(task) }
    }
}

// Nenhuma dependência de acessibilidade, VPN ou root pode entrar no projeto.
val forbiddenDependencyHints = listOf("accessibility", "vpn", "rootbeer", "libsu", "topjohnwu")
tasks.register("checkForbiddenDependencies") {
    doLast {
        val bad = configurations.filter { it.isCanBeResolved && it.name.endsWith("RuntimeClasspath") }
            .flatMap { c -> c.incoming.resolutionResult.allComponents.map { it.id.displayName } }
            .filter { id -> forbiddenDependencyHints.any { id.lowercase().contains(it) } }
            .distinct()
        if (bad.isNotEmpty()) throw GradleException("Dependências proibidas (R2/R4): $bad")
    }
}
tasks.named("check") { dependsOn("checkForbiddenDependencies") }
