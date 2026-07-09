plugins {
  alias(libs.plugins.ignition.module)
  id("com.diffplug.spotless") version "6.25.0"
}

val ignitionTarget = rootProject.findProperty("ignitionTarget")?.toString() ?: "8.3"

// Auto-append a build-id (YYMMddHH) so every build artifact carries a distinct version
// component. Tagged builds are computed from `git describe --tags --long --dirty`; if
// HEAD is exactly on a tag the tag value is used as-is (no -SNAPSHOT), otherwise the
// nearest tag + "-SNAPSHOT" is used. Either way, ".YYMMddHH" is appended to the value
// used in `moduleVersion`.
// Format fits comfortably in int32 (max 99123123).
val now = java.time.LocalDateTime.now()
val buildId = now.format(java.time.format.DateTimeFormatter.ofPattern("YYMMddHH")).toInt()

fun getVersionFromGit(): String {
  return try {
    val process =
        ProcessBuilder("git", "describe", "--tags", "--long", "--dirty")
            .redirectErrorStream(true)
            .start()
    process.waitFor()
    if (process.exitValue() == 0) {
      val describe = process.inputStream.bufferedReader().readText().trim()
      val parts = describe.split("-")
      val tag = parts[0].removePrefix("v")
      val distance = parts.getOrNull(1)?.toIntOrNull() ?: 0
      val isDirty = describe.endsWith("-dirty")
      when {
        distance == 0 && !isDirty -> tag
        else -> "$tag-SNAPSHOT"
      }
    } else {
      findProperty("version")?.toString() ?: "0.0.0-SNAPSHOT"
    }
  } catch (e: Exception) {
    findProperty("version")?.toString() ?: "0.0.0-SNAPSHOT"
  }
}

val baseVersion = getVersionFromGit()
val cleanVersion = baseVersion.replace("-SNAPSHOT", "")
val versionWithBuildId =
    if (baseVersion.endsWith("-SNAPSHOT")) {
      "$cleanVersion.$buildId-SNAPSHOT"
    } else {
      "$cleanVersion.$buildId"
    }

version = versionWithBuildId

println(
    "Flint Designer Bridge Module Version: $versionWithBuildId (base: $baseVersion, build: $buildId)")

allprojects {
  apply(plugin = "com.diffplug.spotless")

  spotless {
    java {
      target("src/*/java/**/*.java")
      googleJavaFormat("1.15.0").aosp()
      removeUnusedImports()
      trimTrailingWhitespace()
      endWithNewline()
    }
    kotlinGradle {
      target("*.gradle.kts")
      ktfmt()
      trimTrailingWhitespace()
      endWithNewline()
    }
  }
  version = rootProject.version
}

subprojects {
  apply(plugin = "jacoco")

  tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.withType<JacocoReport>())
  }

  tasks.withType<JacocoReport> {
    dependsOn(tasks.withType<Test>())
    reports {
      xml.required.set(true)
      html.required.set(true)
    }
  }
}

ignitionModule {
  name.set("Flint Designer Bridge")
  fileName.set("Flint-Designer-Bridge.modl")
  id.set("dev.bwdesigngroup.flint.FlintDesignerBridge")
  license.set("LICENSE.txt")
  moduleVersion.set("${project.version}")
  moduleDescription.set(
      "Enables VS Code Flint extension to connect to running Designer instances for script execution.")
  requiredIgnitionVersion.set(if (ignitionTarget == "8.1") "8.1.44" else "8.3.1")

  projectScopes.putAll(mapOf(":gateway" to "G", ":designer" to "D", ":common" to "GD"))

  moduleDependencies.putAll(mapOf("com.inductiveautomation.perspective" to "G"))

  hooks.putAll(
      mapOf(
          "dev.bwdesigngroup.flint.gateway.FlintGatewayHook" to "G",
          "dev.bwdesigngroup.flint.designer.FlintDesignerHook" to "D"))

  applyInductiveArtifactRepo.set(true)
  skipModlSigning.set(!findProperty("signModule").toString().toBoolean())
}

tasks.register("deepClean") {
  dependsOn(allprojects.map { "${it.path}:clean" })
  description = "Executes clean tasks and removes build caches."
  doLast { delete(file(".gradle")) }
}

tasks.withType<io.ia.sdk.gradle.modl.task.Deploy>().configureEach {
  hostGateway = project.findProperty("hostGateway")?.toString() ?: ""
}
