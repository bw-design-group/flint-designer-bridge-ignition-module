plugins { `java-library` }

val ignitionTarget = rootProject.findProperty("ignitionTarget")?.toString() ?: "8.3"
val sdkVersion = if (ignitionTarget == "8.1") "8.1.44" else libs.versions.ignition.sdk.get()

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

sourceSets.main {
  java.srcDir(if (ignitionTarget == "8.1") "src/v81/java" else "src/v83/java")
  resources { srcDirs("src/main/resources") }
}

dependencies {
  api(project(":common"))

  compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:$sdkVersion")
  compileOnly("com.inductiveautomation.ignitionsdk:designer-api:$sdkVersion")

  // Bundle WebSocket library with the module
  "modlImplementation"(libs.java.websocket)
  // Gson comes from common module via api(project(":common"))

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.junit.jupiter)
  testImplementation(libs.gson)
  testImplementation(libs.java.websocket)
  testImplementation("com.inductiveautomation.ignitionsdk:ignition-common:$sdkVersion")
  testImplementation("com.inductiveautomation.ignitionsdk:designer-api:$sdkVersion")
}

tasks.withType<ProcessResources> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
