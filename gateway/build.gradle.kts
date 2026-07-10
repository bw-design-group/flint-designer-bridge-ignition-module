plugins { `java-library` }

val ignitionTarget = rootProject.findProperty("ignitionTarget")?.toString() ?: "8.3"
val sdkVersion = if (ignitionTarget == "8.1") "8.1.44" else libs.versions.ignition.sdk.get()

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

sourceSets.main { java.srcDir(if (ignitionTarget == "8.1") "src/v81/java" else "src/v83/java") }

dependencies {
  api(project(":common"))

  compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:$sdkVersion")
  compileOnly("com.inductiveautomation.ignitionsdk:gateway-api:$sdkVersion")
  compileOnly("com.inductiveautomation.ignitionsdk:perspective-gateway:$sdkVersion")
  compileOnly("com.inductiveautomation.ignitionsdk:perspective-common:$sdkVersion")

  // Raw-LSP WebSocket transport: compile the per-target Jetty shim against the same Jetty API the
  // platform ships (10 on 8.1, 12 ee10 on 8.3). compileOnly — nothing is bundled into the .modl.
  if (ignitionTarget == "8.1") {
    compileOnly(libs.jetty.ws.server.v81)
    compileOnly(libs.jetty.ws.api.v81)
  } else {
    compileOnly(libs.jetty.ws.server.v83)
    compileOnly(libs.jetty.ws.api.v83)
  }

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.junit.jupiter)
  testImplementation(libs.gson)
  testImplementation("com.inductiveautomation.ignitionsdk:ignition-common:$sdkVersion")
  testImplementation("com.inductiveautomation.ignitionsdk:gateway-api:$sdkVersion")
  testImplementation("com.inductiveautomation.ignitionsdk:perspective-gateway:$sdkVersion")
  testImplementation("com.inductiveautomation.ignitionsdk:perspective-common:$sdkVersion")
}
