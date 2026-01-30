package es.age.dgpe.placsp.risp.parser.workflow;

import es.age.dgpe.placsp.risp.parser.downloader.FileDownloader;
import es.age.dgpe.placsp.risp.parser.downloader.WebScraper;
import es.age.dgpe.placsp.risp.parser.converter.AtomToExcelConverter;
import es.age.dgpe.placsp.risp.parser.uploader.OAuth2TokenHelper;
import es.age.dgpe.placsp.risp.parser.uploader.GraphHelper;
import es.age.dgpe.placsp.risp.parser.uploader.GraphSharePointUploader;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Orquestador principal del flujo de trabajo PLACSP.
 * Coordina las operaciones de descarga y conversión de datos de contratación pública.
 * 
 * Flujo:
 * 1. Extrae enlaces de archivos ZIP desde páginas web gubernamentales
 * 2. Descarga los archivos ZIP
 * 3. Convierte los ZIP a formato Excel usando el CLI
 * 4. Muestra resumen de archivos generados
 */
public class PlacspWorkflow {

    private final WebScraper webScraper;
    private final FileDownloader fileDownloader;
    private final AtomToExcelConverter converter;

    static {
        // Desactivar validación SSL para pruebas
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Cache de variables del .env cargadas con UTF-8
    private static Map<String, String> envConfig = null;

    /**
     * Carga las variables del archivo .env con codificación UTF-8.
     * Necesario porque el batch de Windows no maneja bien UTF-8 con tildes.
     */
    private static void loadEnvFile() {
        if (envConfig != null) return;
        envConfig = new HashMap<>();
        try {
            java.io.File envFile = new java.io.File(".env");
            if (envFile.exists()) {
                java.util.List<String> lines = Files.readAllLines(envFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int idx = line.indexOf('=');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        envConfig.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error cargando .env: " + e.getMessage());
        }
    }

    /**
     * Obtiene una variable de configuración, primero del .env (UTF-8), luego de System.getenv.
     */
    private static String getConfig(String key) {
        loadEnvFile();
        String value = envConfig.get(key);
        if (value != null) return value;
        return System.getenv(key);
    }

    /**
     * Constructor que inicializa los componentes necesarios.
     */
    public PlacspWorkflow() {
        this.webScraper = new WebScraper();
        this.fileDownloader = new FileDownloader();
        this.converter = new AtomToExcelConverter();
    }

    /**
     * Ejecuta el proceso completo: descarga y conversión.
     * 
     * @param urls URLs de las páginas web donde buscar enlaces de descarga
     * @throws IOException si hay error en las operaciones de I/O
     */
    public void ejecutar(String[] urls) throws IOException {
        String downloadDir = "descargas";
        String excelDir = "descargas/excel";
        
        long startTime = System.currentTimeMillis();
        
        // Iniciar sesión de logging
        PlacspLogger.startSession();
        PlacspLogger.info("URLs a procesar: " + urls.length);
        
        System.out.println("========================================");
        System.out.println("PROCESO COMPLETO: Descargar y Convertir a Excel");
        System.out.println("========================================\n");
        
        // Crear directorios
        Files.createDirectories(Paths.get(downloadDir));
        Files.createDirectories(Paths.get(excelDir));
        
        // Fase 1: Descargar archivos
        System.out.println("[FASE 1] Descargando archivos ZIP...");
        descargarArchivos(urls, downloadDir);
        
        // Fase 2: Convertir ZIP a Excel (el CLI maneja la descompresión automáticamente)
        System.out.println("\n[FASE 2] Convirtiendo archivos ZIP a Excel...");
        converter.convertirTodosZipAExcel(downloadDir, excelDir);
        
        // Limpiar archivos ZIP después de convertir
        System.out.println("\n[LIMPIEZA] Eliminando archivos ZIP...");
        eliminarArchivosZip(downloadDir);
        
        // Fase 3: Subir a SharePoint (opcional)
        System.out.println("\n[FASE 3] Subiendo archivos a SharePoint...");
        subirASharePoint(excelDir);
        
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        
        // Finalizar sesión de logging
        PlacspLogger.endSession(duration);
        PlacspLogger.close();
        
        System.out.println("\n========================================");
        System.out.println("PROCESO COMPLETADO EN " + duration + " segundos");
        System.out.println("========================================");
        
        // Resumen final
        converter.mostrarResumen(excelDir);
    }

    /**
     * Descarga archivos ZIP de las URLs especificadas.
     * 
     * @param urls URLs de las páginas web donde buscar enlaces
     * @param downloadDir Directorio de descarga
     * @throws IOException si hay error en las operaciones
     */
    private void descargarArchivos(String[] urls, String downloadDir) throws IOException {
        for (String url : urls) {
            System.out.println("  Buscando en: " + url);
            String enlace = webScraper.extraerPrimerEnlaceAnyoMes(url);
            
            if (enlace != null) {
                System.out.println("  Enlace encontrado: " + enlace);
                
                // Extraer nombre original del archivo de la URL
                String nombreOriginal = webScraper.extraerNombreArchivo(enlace);
                String nombreArchivo = downloadDir + "/" + nombreOriginal;
                
                System.out.println("  Descargando: " + nombreOriginal);
                fileDownloader.descargarArchivo(enlace, nombreArchivo);
            } else {
                System.out.println("  No se encontró enlace AÑOMES.");
            }
        }
    }

    /**
     * Sube los archivos Excel a SharePoint.
     * 
     * @param excelDir Directorio con los archivos Excel a subir
     */
    private void subirASharePoint(String excelDir) {
        try {
            // Leer credenciales para OAuth2 (usando getConfig para UTF-8)
            String tenantId = getConfig("SHAREPOINT_TENANT_ID");
            String clientId = getConfig("SHAREPOINT_CLIENT_ID");
            String clientSecret = getConfig("SHAREPOINT_CLIENT_SECRET");
            String siteUrl = getConfig("SHAREPOINT_URL");
            String library = getConfig("SHAREPOINT_LIBRARY");
            if (tenantId == null || clientId == null || clientSecret == null || siteUrl == null || library == null) {
                System.out.println("  ℹ SharePoint OAuth2 no configurado. Omitiendo fase de carga.");
                return;
            }
            System.out.println("  Obteniendo token OAuth2...");
            String token = OAuth2TokenHelper.getAccessToken(tenantId, clientId, clientSecret);
            if (token == null) {
                System.err.println("  ✗ No se pudo obtener el token OAuth2");
                return;
            }
            System.out.println("  ✓ Token obtenido");

            // Obtener siteId y driveId usando Graph
            String hostname = siteUrl.replace("https://", "").split("/", 2)[0];
            String sitePath = siteUrl.substring(siteUrl.indexOf(hostname) + hostname.length());
            System.out.println("  Buscando siteId y driveId por Graph...");
            String siteId = GraphHelper.getSiteId(hostname, sitePath, token);
            if (siteId == null) {
                System.err.println("  ✗ No se pudo obtener el siteId");
                return;
            }
            // Mostrar todos los drives para depuración
            GraphHelper.listDrives(siteId, token);
            
            // Buscar el driveId usando múltiples estrategias de fallback
            String driveId = null;
            String driveDisplayName = null;
            
            // 1. Intentar 'Documentos compartidos' (español)
            driveId = GraphHelper.getDriveIdByDisplayName(siteId, token, "Documentos compartidos");
            if (driveId != null) {
                driveDisplayName = "Documentos compartidos";
            }
            
            // 2. Si no, intentar 'Documents' (inglés)
            if (driveId == null) {
                driveId = GraphHelper.getDriveIdByDisplayName(siteId, token, "Documents");
                if (driveId != null) {
                    driveDisplayName = "Documents";
                }
            }
            
            // 3. Si no, intentar 'Shared Documents'
            if (driveId == null) {
                driveId = GraphHelper.getDriveIdByDisplayName(siteId, token, "Shared Documents");
                if (driveId != null) {
                    driveDisplayName = "Shared Documents";
                }
            }
            
            // 4. Si no, usar el drive por defecto
            if (driveId == null) {
                System.out.println("  ℹ No se encontró biblioteca estándar, usando drive por defecto...");
                driveId = GraphHelper.getDefaultDriveId(siteId, token);
                if (driveId != null) {
                    driveDisplayName = "(por defecto)";
                }
            }
            
            if (driveId == null) {
                System.err.println("  ✗ No se pudo obtener ningún driveId");
                return;
            }
            System.out.println("  ✓ siteId: " + siteId);
            System.out.println("  ✓ driveId (" + driveDisplayName + "): " + driveId);

            GraphSharePointUploader uploader = new GraphSharePointUploader(token, siteId, driveId);
            java.io.File folder = new java.io.File(excelDir);
            if (!folder.exists()) {
                System.out.println("  ℹ No hay directorio de Excel. Omitiendo carga.");
                return;
            }
            java.io.File[] excelFiles = folder.listFiles((dir, name) -> name.endsWith(".xlsx"));
            if (excelFiles == null || excelFiles.length == 0) {
                System.out.println("  ℹ No hay archivos Excel para subir.");
                return;
            }
            int exitosos = 0;
            for (java.io.File file : excelFiles) {
                try {
                    System.out.println("  Subiendo: " + file.getName());
                    // El path destino: usar la carpeta de SHAREPOINT_LIBRARY si está definida
                    String destino;
                    if (library != null && !library.isEmpty()) {
                        destino = library + "/" + file.getName();
                    } else {
                        destino = file.getName();
                    }
                    // Imprimir la ruta destino en consola para depuración de codificación
                    System.out.println("    [DEBUG] Ruta destino (UTF-8): " + destino);
                    if (uploader.uploadFile(file.getAbsolutePath(), destino)) {
                        System.out.println("    ✓ Subido: " + file.getName());
                        exitosos++;
                        // Eliminar archivo local después de subir exitosamente
                        if (file.delete()) {
                            System.out.println("    ✓ Eliminado localmente: " + file.getName());
                        }
                    } else {
                        System.err.println("    ✗ Error al subir: " + file.getName());
                    }
                } catch (Exception e) {
                    System.err.println("    ✗ Error: " + e.getMessage());
                }
            }
            System.out.println("  Resumen: " + exitosos + "/" + excelFiles.length + " archivos subidos exitosamente.");
        } catch (Exception e) {
            System.err.println("  ✗ Error en SharePoint: " + e.getMessage());
        }
    }

    /**
     * Elimina todos los archivos ZIP del directorio especificado.
     * 
     * @param directory Directorio donde buscar archivos ZIP
     */
    private void eliminarArchivosZip(String directory) {
        java.io.File folder = new java.io.File(directory);
        java.io.File[] zipFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
        if (zipFiles != null && zipFiles.length > 0) {
            for (java.io.File zipFile : zipFiles) {
                if (zipFile.delete()) {
                    System.out.println("  ✓ Eliminado: " + zipFile.getName());
                } else {
                    System.err.println("  ✗ No se pudo eliminar: " + zipFile.getName());
                }
            }
        } else {
            System.out.println("  ℹ No hay archivos ZIP para eliminar.");
        }
    }

    /**
     * Punto de entrada principal del programa.
     * 
     * @param args Argumentos de línea de comandos (no utilizados)
     * @throws IOException si hay error en las operaciones
     */
    public static void main(String[] args) throws IOException {
        String[] urls = {
            "https://www.hacienda.gob.es/es-es/gobiernoabierto/datos%20abiertos/paginas/licitacionescontratante.aspx",
            "https://www.hacienda.gob.es/es-es/gobiernoabierto/datos%20abiertos/paginas/licitacionesagregacion.aspx"
        };

        PlacspWorkflow workflow = new PlacspWorkflow();
        workflow.ejecutar(urls);
    }
}
