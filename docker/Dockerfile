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

# Copiar código fuente
COPY src/ ./src/
COPY pom.xml ./

# Crear directorios necesarios
RUN mkdir -p target/classes descargas descargas/atom descargas/excel logs

# Compilar las clases model (encoding Windows-1252)
RUN javac -encoding UTF-8 -d target/classes -cp "lib/*" \
    src/main/java/es/age/dgpe/placsp/risp/parser/model/*.java

# Compilar el resto de clases (encoding UTF-8)
RUN javac -encoding UTF-8 -d target/classes -cp "lib/*:target/classes" \
    src/main/java/es/age/dgpe/placsp/risp/parser/utils/*.java \
    src/main/java/es/age/dgpe/placsp/risp/parser/utils/genericode/*.java \
    src/main/java/es/age/dgpe/placsp/risp/parser/cli/*.java \
    src/main/java/es/age/dgpe/placsp/risp/parser/uploader/*.java \
    src/main/java/es/age/dgpe/placsp/risp/parser/downloader/*.java \
    src/main/java/es/age/dgpe/placsp/risp/parser/converter/*.java \
    src/main/java/es/age/dgpe/placsp/risp/parser/workflow/*.java

# Copiar recursos
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

# Script de entrada
COPY docker/docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Comando por defecto
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["run"]
