# PLACSP2SharePoint

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-EUPL--1.2-green)](licenses/EUPL-1.2%20EN.txt)

Herramienta para descargar datos de la **Plataforma de Contratación del Sector Público (PLACSP)**, convertirlos a Excel y subirlos automáticamente a SharePoint.

## 📋 Tabla de contenidos
- [Funcionalidades](#-funcionalidades)
- [Estructura del proyecto](#-estructura-del-proyecto)
- [Requisitos previos](#-requisitos-previos)
- [Configuración inicial](#-configuración-inicial)
- [Compilación manual](#-compilación-manual)
- [Despliegue con Docker](#-despliegue-con-docker)
  - [1. Preparar el archivo .env](#1-preparar-el-archivo-env)
  - [2. Compilar antes de construir la imagen](#2-compilar-antes-de-construir-la-imagen)
  - [3. Levantar el contenedor](#3-levantar-el-contenedor)
  - [4. Ejecución bajo demanda](#4-ejecución-bajo-demanda)
  - [5. Personalizar el cron](#5-personalizar-el-cron)
- [Monitoreo y logs](#-monitoreo-y-logs)
- [Solución de problemas comunes](#-solución-de-problemas-comunes)
- [Variables de entorno](#-variables-de-entorno)
- [Licencia](#-licencia)

## 🎯 Funcionalidades

- **Descarga inteligente** de ficheros ZIP desde el portal de datos abiertos de Hacienda
  - Si la carpeta `atom/` está vacía: descarga los últimos N meses (configurable)
  - Si ya hay datos: descarga solo el último ZIP (incremental)
- **Conversión** de datos ATOM/XML a formato Excel (.xlsx)
  - Combina ATOMs de múltiples meses en un único Excel
  - Elimina duplicados automáticamente
- **Limpieza automática** de ATOMs antiguos (configurable por meses)
- **Subida automática** a SharePoint mediante Microsoft Graph API
- **Logging** con rotación automática de 30 días

## 📁 Estructura del proyecto

```text
PLACSP2SharePoint/
├── compilar.sh           # Compilar el proyecto
├── placsp-cli.sh         # CLI para conversión manual ZIP→Excel
├── .env                  # Configuración (credenciales SharePoint)
├── .env.example          # Plantilla de configuración
│
├── src/main/java/es/age/dgpe/placsp/risp/parser/
│   ├── workflow/         # Orquestador principal (PlacspWorkflow.java)
│   ├── downloader/       # Descarga de archivos (FileDownloader, WebScraper)
│   ├── converter/        # Conversión ATOM → Excel
│   ├── cli/              # CLI para conversión manual (AtomToExcelCLI)
│   ├── uploader/         # Subida a SharePoint (GraphSharePointUploader)
│   ├── model/            # Modelos de datos CODICE/PLACSP
│   └── utils/            # Utilidades (Config, PlacspLogger, Genericode)
│
├── src/main/resources/
│   ├── gc/               # Catálogos Genericode (códigos CODICE)
│   ├── templates/        # Plantilla Excel base
│   └── open-placsp.properties # Configuración de la aplicación
│
├── target/classes/       # Archivos compilados (.class)
├── lib/                  # Dependencias JAR (POI, CODICE, Graph, etc.)
├── descargas/            # Archivos descargados temporalmente
├── logs/                 # Log de operaciones (placsp.log)
├── docker/               # Archivos para containerización
│   ├── Dockerfile
│   └── docker-compose.yml
└── licenses/             # Licencias de componentes
```

## 🔧 Requisitos previos

- **Java JDK 21** (puede ser [Azul Zulu](https://www.azul.com/downloads/) o [Eclipse Temurin](https://adoptium.net/))
- **Git** para clonar el repositorio
- **Docker** y **Docker Compose** (para despliegue con contenedores)
- **Conexión a Internet** para descargar dependencias y acceder a las APIs

## ⚙️ Configuración inicial

### 1. Clonar el repositorio y acceder a la rama `develop`
```bash
git clone https://github.com/SerCast94/PLACSP2SharePoint.git
cd PLACSP2SharePoint
```

### 2. Crear el archivo .env (¡importante!)
El programa y el contenedor Docker leen la configuración desde un archivo .env en la raíz del proyecto.

**Atención:** Asegúrate de que sea un archivo, no un directorio. Si ejecutaste `mkdir .env` por error, aparecerá el error `cat: .env: Is a directory`. Para solucionarlo:

```bash
# Si existe una carpeta .env, elimínala o renómbrala
rm -rf .env                # si está vacía
# o bien
mv .env .env_backup        # para conservarla
```

Copia el ejemplo y edítalo:

```bash
cp .env.example .env
nano .env
```

Configura al menos los valores de SharePoint (tenant, client id, secret, url, librería). Consulta la sección [Variables de entorno](#-variables-de-entorno) para más detalles.

## 🔨 Compilación manual
Si necesitas modificar el código fuente o compilarlo localmente (por ejemplo, para probar cambios antes de dockerizar):

```bash
# Dar permisos de ejecución
chmod +x compilar.sh

# Ejecutar el script de compilación
./compilar.sh
```

Esto generará los archivos .class dentro de `target/classes/` y copiará los recursos necesarios. La compilación utiliza las librerías de la carpeta `lib/`.

**Nota:** El script `compilar.sh` incluido en el repositorio ya está adaptado para Linux. Si encuentras errores de compilación, verifica que la carpeta `lib` contenga todos los JAR necesarios.

## 🐳 Despliegue con Docker

### 1. Preparar el archivo .env
El archivo `.env` debe existir en la raíz del proyecto (justo donde está la carpeta `docker/`). Verifica que esté correctamente configurado (especialmente las credenciales de SharePoint). Puedes comprobarlo con:

```bash
cat .env
```

### 2. Compilar antes de construir la imagen
La imagen Docker espera encontrar las clases compiladas en `target/classes/`. Por tanto, es necesario compilar primero:

```bash
./compilar.sh
```

Si la compilación falla, revisa los errores (por ejemplo, classpath mal formado). El script ya incluye correcciones para entornos Linux.

### 3. Levantar el contenedor
Accede a la carpeta `docker` y lanza el servicio programado (cron):

```bash
cd docker
docker compose up -d
```

Esto construirá la imagen (si no existe) y levantará el contenedor `placsp-workflow` con el cron en segundo plano.

Para ver los logs en tiempo real:

```bash
docker logs -f placsp-workflow
```

Para comprobar que el contenedor está corriendo:

```bash
docker ps
```

### 4. Ejecución bajo demanda
El `docker-compose.yml` incluye un perfil manual para ejecutar el workflow una sola vez. Úsalo así:

```bash
docker compose --profile manual up placsp-ondemand
```

Este contenedor se ejecutará, realizará el trabajo y terminará.

### 5. Personalizar el cron
La frecuencia de ejecución programada se define con la variable `CRON_SCHEDULE` en el `docker-compose.yml`. Para cambiarla:

```bash
cd docker
nano docker-compose.yml
```

Busca la línea:

```yaml
      - CRON_SCHEDULE=12 8 * * *
```

Cámbiala según la sintaxis cron estándar (minuto hora día-del-mes mes día-de-la-semana). Por ejemplo, para ejecutar todos los días a las 10:30:

```yaml
      - CRON_SCHEDULE=30 10 * * *
```

Guarda el archivo y reinicia el contenedor para aplicar los cambios:

```bash
docker compose up -d
```

## 📊 Monitoreo y logs

Logs del contenedor (salida estándar):

```bash
docker logs -f placsp-workflow
```

Archivos de log persistentes (se guardan en `../logs` desde la carpeta `docker/`):

```bash
cd ../logs
ls -la
tail -f placsp.log   # o el nombre que genere tu app
```

Entrar al contenedor para inspeccionar procesos o archivos:

```bash
docker exec -it placsp-workflow /bin/sh
```

Una vez dentro puedes ejecutar `ps aux`, `ls -la /app`, etc.

Uso de recursos:

```bash
docker stats placsp-workflow
```

## 🐛 Solución de problemas comunes

### Error `cat: .env: Is a directory`
Causa: Existe una carpeta llamada `.env` en lugar de un archivo.
Solución:

```bash
rm -rf .env                # si está vacía
# o bien
mv .env .env_backup        # para conservarla
# luego crear el archivo real
cp .env.example .env
nano .env
```

### Error de permisos de Docker: `permission denied while trying to connect to the Docker daemon socket`
Causa: El usuario no está en el grupo docker.
Solución:

```bash
sudo usermod -aG docker $USER
# Cerrar sesión y volver a entrar, o ejecutar:
newgrp docker
```

### La compilación falla con `invalid flag` o errores de classpath
Causa: El script `compilar.sh` original no era compatible con Linux.
Solución: Usa la versión mejorada que se incluye en el repositorio (o la que se proporciona en este README). Asegúrate de que la carpeta `lib/` contiene todos los JAR necesarios.

### El contenedor no se inicia porque falta el archivo .env
Causa: El volumen montado espera encontrar `../.env` (relativo a `docker/`).
Solución: Verifica que el archivo `.env` existe en la raíz del proyecto (un nivel arriba de `docker/`). Puedes comprobarlo con `ls -la ../.env`.

### El cron no se ejecuta a la hora esperada
Causa: La zona horaria del contenedor no coincide con la local.
Solución: Asegúrate de que la variable `TZ=Europe/Madrid` está definida en el `docker-compose.yml` y en el `.env`. Reinicia el contenedor tras el cambio.

### Error de compilación: `error: invalid flag: src/main/java/...` (archivos pegados)
Causa: El archivo `sources.txt` se generó con separadores incorrectos.
Solución: El script `compilar.sh` actualizado ya corrige este problema. Si aún persiste, asegúrate de que el script utiliza `-print` en lugar de `-print0`.

## 🌐 Variables de entorno

| Variable | Descripción | Requerido | Valor por defecto |
|----------|-------------|-----------|-------------------|
| PLACSP_URL_CONTRATANTE | URL página licitaciones contratante | ❌ | (interno) |
| PLACSP_URL_AGREGACION | URL página plataformas agregadas | ❌ | (interno) |
| MESES_HISTORICO | Meses de histórico a mantener | ❌ | 5 |
| SHAREPOINT_TENANT_ID | ID del tenant Azure AD | ✅ | - |
| SHAREPOINT_CLIENT_ID | ID de la aplicación (client) | ✅ | - |
| SHAREPOINT_CLIENT_SECRET | Secreto de la aplicación | ✅ | - |
| SHAREPOINT_URL | URL del sitio SharePoint (ej: https://tenant.sharepoint.com/sites/MiSitio) | ✅ | - |
| SHAREPOINT_LIBRARY | Carpeta destino en SharePoint | ✅ | - |
| SHAREPOINT_DRIVE_NAMES | Nombres de las unidades (separados por ;) | ❌ | Documentos compartidos;Documents;Shared Documents |
| CLI_INCLUIR_EMP | Incluir hoja EMP en el Excel | ❌ | false |
| CLI_INCLUIR_CPM | Incluir hoja CPM en el Excel | ❌ | false |
| MAX_LOG_DAYS | Días de log a conservar | ❌ | 30 |
| CRON_SCHEDULE | Horario cron (formato estándar) | ❌ | 0 2 * * * (2:00 AM) |
| TZ | Zona horaria del contenedor | ❌ | Europe/Madrid |
| JAVA_OPTS | Opciones JVM (ej: -Xmx4g) | ❌ | - |

## 📝 Licencia

Este proyecto utiliza componentes bajo diversas licencias open source.
Consulte la carpeta `licenses/` para más detalles.

---

**Fuente de datos**: [Portal de Datos Abiertos - Ministerio de Hacienda](https://www.hacienda.gob.es/es-ES/GobiernoAbierto/Datos%20Abiertos/Paginas/licitaciones_plataforma_contratacion.aspx)
