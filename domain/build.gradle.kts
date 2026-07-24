plugins {
    java
}

group = "io.tenoro.${rootProject.name}"
description = "Domain Layer"

dependencies {
    // Pure domain logic - no external framework dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
