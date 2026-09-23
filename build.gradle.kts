plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    // Линтер Kotlin (проверка стиля кода)
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.alfahack"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Отказоустойчивость: circuit breaker (Resilience4j) + rate limiting (Bucket4j)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.4.0")
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    // Перезагрузка конфигурации без рестарта (@RefreshScope)
    implementation("org.springframework.cloud:spring-cloud-context:4.2.4")

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// =============================================================================
// ktlint — линтер Kotlin (проверка стиля кода)
// Блокирует сборку при нарушениях стиля (и локально, и в CI).
// Автоформатирование: ./gradlew ktlintFormat
// =============================================================================
ktlint {
    // Версия ktlint (ядро)
    version.set("1.8.0")
    // Блокировать сборку при нарушениях (строгий режим)
    ignoreFailures.set(false)
    // Не проверять сгенерированные файлы
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

// ktlintCheck выполняется при каждой сборке (./gradlew build)
tasks.named("check") {
    dependsOn("ktlintCheck")
}
