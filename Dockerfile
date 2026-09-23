# syntax=docker/dockerfile:1

# =============================================================================
# Build stage — компиляция Kotlin/Spring Boot приложения
# =============================================================================
FROM gradle:jdk21 AS builder
WORKDIR /app

# Копируем build-файлы для кэширования зависимостей
COPY build.gradle.kts settings.gradle.kts ./

# Скачиваем зависимости (кэшируемый слой)
RUN gradle dependencies --no-daemon || true

# Копируем исходники
COPY src ./src

# Собираем jar (без тестов для скорости)
RUN gradle bootJar -x test --no-daemon && \
    mv build/libs/*.jar app.jar

# =============================================================================
# Production stage — минимальный JRE
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS production
WORKDIR /app

# Создаём non-root пользователя
RUN useradd -m -u 1001 appuser

# Копируем jar из builder
COPY --from=builder --chown=appuser:appuser /app/app.jar ./app.jar

# Переключаемся на non-root пользователя
USER appuser

# Порт приложения
EXPOSE 8080

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Запуск
ENTRYPOINT ["java", "-jar", "app.jar"]