pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    maven { url = uri("https://nexus.inductiveautomation.com/repository/public") }
  }
}

rootProject.name = "flint-designer-bridge"

include(":", ":gateway", ":designer", ":common")
