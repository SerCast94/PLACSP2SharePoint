# Imagen base con OpenJDK 21
FROM eclipse-temurin:21-jdk-alpine

# Metadatos
LABEL maintainer="PlacspWorkflow"
LABEL description="Descarga y conversión de datos PLACSP a Excel con subida a SharePoint"
LABEL version="2.0"

# Instalar dependencias del sistema (incluyendo cron para ejecución programada)
RUN apk add --no-cache curl unzip dcron tzdata

# Crear directorio de trabajo
WORKDIR /app

# Copiar dependencias primero (mejor cache de Docker)
COPY lib/ ./lib/


# Copiar código fuente y recursos
COPY src/ ./src/
COPY src/main/resources/gc/ ./gc/
COPY pom.xml ./

# Crear directorios necesarios
RUN mkdir -p target/classes descargas descargas/atom descargas/excel logs

# Compilar todo el código fuente Java en un solo paso (evita problemas de dependencias)
RUN find src/main/java -name "*.java" > sources.txt \
    && javac -encoding UTF-8 -d target/classes -cp "lib/*:./gc" @sources.txt

# Copiar recursos de src/main/resources a target/classes (incluyendo jaxb.index, etc.)
RUN cp -r src/main/resources/* target/classes/ 2>/dev/null || true

# Copiar recursos adicionales del build local si existen
COPY target/classes/gc/ ./target/classes/gc/
COPY target/classes/templates/ ./target/classes/templates/
COPY target/classes/*.properties ./target/classes/
COPY target/classes/*.xml ./target/classes/

# Volúmenes para persistencia
# - descargas/atom: ATOMs extraídos (acumulativo)
# - descargas/excel: Excel generados (temporal, se eliminan tras subir)
# - logs: Archivo de log con rotación 30 días
VOLUME ["/app/descargas", "/app/logs"]

# Variables de entorno
ENV JAVA_OPTS=""
ENV TZ=Europe/Madrid

# Copiar archivo .env de ejemplo para referencia
COPY .env.example ./.env.example

# Copiar script CLI para Linux
COPY placsp-cli.sh /app/placsp-cli.sh
RUN chmod +x /app/placsp-cli.sh

# Copiar script de entrada correctamente
COPY docker/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Comando por defecto
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["run"]
