plugins { `java-library` }

val ignitionTarget = rootProject.findProperty("ignitionTarget")?.toString() ?: "8.3"
val sdkVersion = if (ignitionTarget == "8.1") "8.1.44" else libs.versions.ignition.sdk.get()

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

sourceSets.main { java.srcDir(if (ignitionTarget == "8.1") "src/v81/java" else "src/v83/java") }

dependencies {
  compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:$sdkVersion")
  // Bundle Gson with the module using modlApi (Ignition plugin configuration)
  "modlApi"(libs.gson)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.junit.jupiter)
  testImplementation(libs.gson)
}
