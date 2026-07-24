plugins {
    `java-library`
}

group = "io.tenoro.${rootProject.name}"
description = "API DTO module"

dependencies {
    // SpringDoc for @Schema annotations in DTOs
    implementation("org.springdoc:springdoc-openapi-starter-common:2.8.6")

    // Spring core / data commons for shared types
    implementation("org.springframework:spring-core")
    implementation("org.springframework.data:spring-data-commons")
}
