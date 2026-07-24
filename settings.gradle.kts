rootProject.name = "templates"

include("api", "domain", "infra")

// Version management - read from gradle.properties first, fallback to default
gradle.beforeProject {
    val versionFromProps = project.findProperty("version") as String?
    version = versionFromProps ?: "0.0.1-SNAPSHOT"
}
