plugins {
    java
    `java-library`
}

group = "io.tenoro.${rootProject.name}"
description = "Infrastructure Layer"

dependencies {
    // Module dependencies
    api(project(":domain"))
    implementation(project(":api"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    implementation("org.springdoc:springdoc-openapi-starter-common:2.8.6")

    // Embedded database (in-memory by default)
    runtimeOnly("com.h2database:h2")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
