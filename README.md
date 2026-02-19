# PLACSP2SharePoint

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://adoptium.net/)
[![Windows](https://img.shields.io/badge/Platform-Windows-blue?logo=windows)]()
[![License](https://img.shields.io/badge/License-EUPL--1.2-green)](licenses/EUPL-1.2%20EN.txt)

Herramienta para descargar datos de la **Plataforma de Contratación del Sector Público (PLACSP)**, convertirlos a Excel y subirlos automáticamente a SharePoint.

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

## 📁 Estructura del Proyecto

```
PLACSP2SharePoint/
├── run.bat                     # Script principal - ejecuta todo el workflow
├── placsp-cli.bat              # CLI para conversión manual ZIP→Excel
├── .env                        # Configuración (credenciales SharePoint)
├── .env.example                # Plantilla de configuración
│
├── src/main/java/es/age/dgpe/placsp/risp/parser/
│   ├── workflow/               # Orquestador principal (PlacspWorkflow.java)
│   ├── downloader/             # Descarga de archivos (FileDownloader, WebScraper)
│   ├── converter/              # Conversión ATOM → Excel
│   ├── cli/                    # CLI para conversión manual (AtomToExcelCLI)
│   ├── uploader/               # Subida a SharePoint (GraphSharePointUploader)
│   ├── model/                  # Modelos de datos CODICE/PLACSP
│   └── utils/                  # Utilidades (Config, PlacspLogger, Genericode)
│
├── src/main/resources/
│   ├── gc/                     # Catálogos Genericode (códigos CODICE)
│   ├── templates/              # Plantilla Excel base
│   └── open-placsp.properties  # Configuración de la aplicación
│
├── target/classes/             # Archivos compilados (.class)
├── lib/                        # Dependencias JAR (POI, CODICE, Graph, etc.)
├── descargas/                  # Archivos descargados temporalmente
├── logs/                       # Log de operaciones (placsp.log)
├── docker/                     # Archivos para containerización
└── licenses/                   # Licencias de componentes
```

## ⚙️ Configuración

Toda la configuración del programa se centraliza en el archivo `.env`. Esto permite modificar el comportamiento sin recompilar el código.

### Archivo .env

1. Copie `.env.example` a `.env`
2. Configure las variables según sus necesidades:

```ini
# ============================================================
# 1. URLs DE DESCARGA
# ============================================================
PLACSP_URL_CONTRATANTE=https://www.hacienda.gob.es/es-es/gobiernoabierto/datos%20abiertos/paginas/licitacionescontratante.aspx
PLACSP_URL_AGREGACION=https://www.hacienda.gob.es/es-es/gobiernoabierto/datos%20abiertos/paginas/licitacionesagregacion.aspx

# ============================================================
# 2. PATRONES DE BÚSQUEDA (regex)
# ============================================================
ZIP_LINK_PATTERN=_(\\d{6})\\.zip$
ANYO_MES_PATTERN=_(\\d{6})\\.
FECHA_COMPLETA_PATTERN=_(\\d{8})_

# ============================================================
# 3. DIRECTORIOS
# ============================================================
DOWNLOAD_DIR=descargas
ATOM_DIR=descargas/atom
EXCEL_OUTPUT_DIR=descargas/excel

# ============================================================
# 4. CONFIGURACIÓN DE DESCARGA
# ============================================================
MESES_HISTORICO=5              # Meses de histórico a mantener
HTTP_CONNECT_TIMEOUT=30000     # Timeout conexión (ms)
HTTP_READ_TIMEOUT=60000        # Timeout lectura (ms)
DOWNLOAD_BUFFER_SIZE=8192      # Buffer de descarga (bytes)
DOWNLOAD_PROGRESS_INTERVAL_MB=10  # Intervalo progreso

# ============================================================
# 5. CONFIGURACIÓN DEL CONVERSOR CLI
# ============================================================
CLI_COMMAND=placsp-cli.bat
CLI_DOS_TABLAS=true            # Separar licitaciones y resultados
CLI_INCLUIR_EMP=false          # Incluir Encargos Medios Propios
CLI_INCLUIR_CPM=false          # Incluir Consultas Preliminares

# ============================================================
# 6. LOGGING
# ============================================================
LOG_DIR=logs
LOG_FILE=placsp.log
MAX_LOG_DAYS=30                # Días a mantener en el log

# ============================================================
# 7. SSL
# ============================================================
SSL_DISABLE_VALIDATION=true    # Solo para pruebas

# ============================================================
# 8. NOMBRES DE ARCHIVOS EXCEL
# ============================================================
EXCEL_NAME_PERF_CONTRAT=licPerfContratPLACSP
EXCEL_NAME_AGREGADAS=licPlatafAgregadas

# ============================================================
# 9. SHAREPOINT (Microsoft Graph API)
# ============================================================
SHAREPOINT_TENANT_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
SHAREPOINT_CLIENT_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
SHAREPOINT_CLIENT_SECRET=tu_secreto_aqui
SHAREPOINT_URL=https://tenant.sharepoint.com/sites/MiSitio
SHAREPOINT_LIBRARY=Colaboración
SHAREPOINT_DRIVE_NAMES=Documentos compartidos;Documents;Shared Documents
```

### Variables principales

| Variable | Descripción | Valor por defecto |
|----------|-------------|-------------------|
| `MESES_HISTORICO` | Meses de datos a mantener | `5` |
| `CLI_INCLUIR_EMP` | Incluir hoja EMP en Excel | `false` |
| `CLI_INCLUIR_CPM` | Incluir hoja CPM en Excel | `false` |
| `MAX_LOG_DAYS` | Días de log a conservar | `30` |
| `SSL_DISABLE_VALIDATION` | Desactivar validación SSL | `true` |

### Configuración de Azure AD

La aplicación requiere una App Registration en Azure AD con:
- **Tipo**: Aplicación (no delegada)
- **Permisos**: `Sites.Selected` (Microsoft Graph)
- **Secreto**: Client Secret configurado

## 🚀 Ejecución

### Workflow completo (Descarga → Conversión → Subida)

```cmd
run.bat
```

### Solo conversión manual (ZIP → Excel)

```cmd
placsp-cli.bat --in archivo.zip --out salida.xlsx
```

Opciones del CLI:
- `--in <path>` - Fichero ATOM o ZIP de entrada
- `--out <path.xlsx>` - Fichero Excel de salida
- `--dos-tablas` - Separar licitaciones y resultados en dos hojas
- `--sin-emp` - No incluir hoja de Encargos a Medios Propios
- `--sin-cpm` - No incluir hoja de Consultas Preliminares

## 🔧 Compilación

Si modifica el código fuente, recompile con:

```cmd
:: Compilar modelos (encoding Windows-1252)
javac -encoding UTF-8 -cp "lib\*" -d target\classes ^
    src\main\java\es\age\dgpe\placsp\risp\parser\model\*.java

:: Compilar resto (encoding UTF-8)
javac -encoding UTF-8 -cp "lib\*;target\classes" -d target\classes ^
    src\main\java\es\age\dgpe\placsp\risp\parser\utils\*.java ^
    src\main\java\es\age\dgpe\placsp\risp\parser\utils\genericode\*.java ^
    src\main\java\es\age\dgpe\placsp\risp\parser\cli\*.java ^
    src\main\java\es\age\dgpe\placsp\risp\parser\downloader\*.java ^
    src\main\java\es\age\dgpe\placsp\risp\parser\converter\*.java ^
    src\main\java\es\age\dgpe\placsp\risp\parser\uploader\*.java ^
    src\main\java\es\age\dgpe\placsp\risp\parser\workflow\*.java
```

## 📊 Logging

El sistema genera un log en `logs/placsp.log` con:
- Descargas (archivo, URL, tamaño, éxito/error)
- Subidas (archivo, destino SharePoint, tamaño, éxito/error)
- Errores con stack trace

El log mantiene automáticamente solo las entradas de los últimos 30 días.

## 🐳 Docker

### Ejecución con Docker Compose

```bash
cd docker

# Ejecución programada (cron - todos los días a las 2 AM)
docker-compose up -d

# Ejecución bajo demanda
docker-compose --profile manual up placsp-ondemand
```

### Variables de entorno

| Variable | Descripción | Requerido |
|----------|-------------|-----------|
| `PLACSP_URL_CONTRATANTE` | URL página licitaciones contratante | ❌ (tiene default) |
| `PLACSP_URL_AGREGACION` | URL página plataformas agregadas | ❌ (tiene default) |
| `MESES_HISTORICO` | Meses de histórico a mantener | ❌ (default: 5) |
| `SHAREPOINT_TENANT_ID` | ID del tenant Azure AD | ✅ |
| `SHAREPOINT_CLIENT_ID` | ID de la aplicación | ✅ |
| `SHAREPOINT_CLIENT_SECRET` | Secreto de la aplicación | ✅ |
| `SHAREPOINT_URL` | URL del sitio SharePoint | ✅ |
| `SHAREPOINT_LIBRARY` | Carpeta destino en SharePoint | ✅ |
| `CLI_INCLUIR_EMP` | Incluir hoja EMP | ❌ (default: false) |
| `CLI_INCLUIR_CPM` | Incluir hoja CPM | ❌ (default: false) |
| `MAX_LOG_DAYS` | Días de log a conservar | ❌ (default: 30) |
| `CRON_SCHEDULE` | Horario cron (Docker) | ❌ (default: `0 2 * * *`) |
| `TZ` | Zona horaria | ❌ (default: `Europe/Madrid`) |
| `JAVA_OPTS` | Opciones JVM (ej: `-Xmx4g`) | ❌ |

### Volúmenes

- `/app/.env` - Archivo de configuración (montaje obligatorio)
- `/app/descargas` - Datos descargados y ATOMs extraídos
- `/app/logs` - Archivos de log

## 📋 Requisitos

- **Java**: JDK 21+ ([Azul Zulu](https://www.azul.com/downloads/) o [Eclipse Temurin](https://adoptium.net/))
- **Sistema**: Windows 10/11 (o Docker para Linux)
- **Azure AD**: App Registration con permisos `Sites.Selected`

## 📦 Dependencias principales

| Librería | Versión | Uso |
|----------|---------|-----|
| Apache POI | 5.x | Generación de Excel |
| JSoup | 1.x | Web scraping |
| Microsoft Graph SDK | 5.x | API SharePoint |
| CODICE | 2.8.0 | Modelos de contratación pública |
| JAXB | 2.3.x | Parsing XML ATOM |

## 📝 Licencia

Este proyecto utiliza componentes bajo diversas licencias open source.
Consulte la carpeta `licenses/` para más detalles.

---

**Fuente de datos**: [Portal de Datos Abiertos - Ministerio de Hacienda](https://www.hacienda.gob.es/es-ES/GobiernoAbierto/Datos%20Abiertos/Paginas/licitaciones_plataforma_contratacion.aspx)
