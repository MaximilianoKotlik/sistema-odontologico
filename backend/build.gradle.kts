plugins {
    java
    // Cobertura de tests (objetivo orientativo 80 % en domain y application, knowledge-base/08)
    jacoco
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ar.sistemaodontologico"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val springdocVersion = "2.8.17"
val archunitVersion = "1.4.2"
val libphonenumberVersion = "8.13.55"
val uuidGeneratorVersion = "5.1.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    // Normalización de teléfonos a E.164 (C-02, design D5)
    implementation("com.googlecode.libphonenumber:libphonenumber:$libphonenumberVersion")
    // Identificadores UUIDv7 generados en la aplicación (C-02, design D3)
    implementation("com.fasterxml.uuid:java-uuid-generator:$uuidGeneratorVersion")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        csv.required = true
        html.required = true
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    // El test del contrato escribe aquí el OpenAPI que consume el frontend (design D7)
    systemProperty("openapi.output", layout.buildDirectory.file("openapi/openapi.json").get().asFile.absolutePath)
}
