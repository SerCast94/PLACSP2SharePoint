# PLACSP2SharePoint

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://adoptium.net/)
[![Windows](https://img.shields.io/badge/Platform-Windows-blue?logo=windows)]()
[![License](https://img.shields.io/badge/License-EUPL--1.2-green)](licenses/EUPL-1.2%20EN.txt)

Herramienta para descargar datos de la **Plataforma de Contratación del Sector Público (PLACSP)**, convertirlos a Excel y subirlos automáticamente a SharePoint.

## 🎯 Funcionalidades

- **Descarga automática** de ficheros ZIP desde el portal de datos abiertos de Hacienda
- **Conversión** de datos ATOM/XML a formato Excel (.xlsx)
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

1. Copie `.env.example` a `.env`
2. Configure las credenciales de Azure AD:

```ini
SHAREPOINT_TENANT_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
SHAREPOINT_CLIENT_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
SHAREPOINT_CLIENT_SECRET=tu_secreto_aqui
SHAREPOINT_URL=https://tenant.sharepoint.com/sites/MiSitio
SHAREPOINT_LIBRARY=Documentos compartidos/MiCarpeta
```

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
javac -encoding Cp1252 -cp "lib\*" -d target\classes ^
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

```bash
cd docker
docker-compose up -d
```

Variables de entorno requeridas en el contenedor:
- `SHAREPOINT_TENANT_ID`
- `SHAREPOINT_CLIENT_ID`
- `SHAREPOINT_CLIENT_SECRET`
- `SHAREPOINT_URL`
- `SHAREPOINT_LIBRARY`

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

**Fuente de datos**: [Portal de Datos Abiertos - Ministerio de Hacienda](https://www.hacienda.gob.es/es-ES/GobiernoAbierto/Datos%20Abiertos/Paginas/Catalogodatosabiertos.aspx)
