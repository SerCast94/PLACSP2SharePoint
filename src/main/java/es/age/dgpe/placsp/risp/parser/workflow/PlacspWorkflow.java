package es.age.dgpe.placsp.risp.parser.workflow;

import es.age.dgpe.placsp.risp.parser.downloader.FileDownloader;
import es.age.dgpe.placsp.risp.parser.downloader.WebScraper;
import es.age.dgpe.placsp.risp.parser.converter.AtomToExcelConverter;
import es.age.dgpe.placsp.risp.parser.uploader.OAuth2TokenHelper;
import es.age.dgpe.placsp.risp.parser.uploader.GraphHelper;
import es.age.dgpe.placsp.risp.parser.uploader.GraphSharePointUploader;
import es.age.dgpe.placsp.risp.parser.utils.EnvConfig;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Orquestador principal del flujo de trabajo PLACSP.
 * Coordina las operaciones de descarga y conversion de datos de contratacion pÃºblica.
 * 
 * Flujo:
 * 1. Extrae enlaces de archivos ZIP desde pÃ¡ginas web gubernamentales
 * 2. Descarga los archivos ZIP
 * 3. Convierte los ZIP a formato Excel usando el CLI
 * 4. Muestra resumen de archivos generados
 */
public class PlacspWorkflow {

    private final WebScraper webScraper;
    private final FileDownloader fileDownloader;
    private final AtomToExcelConverter converter;

    static {
        // Desactivar validacion SSL si estÃ¡ configurado (solo para pruebas)
        if (EnvConfig.isSslDisableValidation()) {
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
     * Ejecuta el proceso completo: descarga y conversion.
     * 
     * @param urls URLs de las pÃ¡ginas web donde buscar enlaces de descarga
     * @throws IOException si hay error en las operaciones de I/O
     */
    public void ejecutar(String[] urls) throws IOException {
        // Obtener configuracion de directorios desde .env
        String downloadDir = EnvConfig.getDownloadDir();
        String atomDir = EnvConfig.getAtomDir();
        String excelDir = EnvConfig.getExcelDir();
        int mesesHistorico = EnvConfig.getMesesHistorico();
        
        long startTime = System.currentTimeMillis();
        
        // Iniciar sesion de logging
        PlacspLogger.startSession();
        PlacspLogger.info("URLs a procesar: " + urls.length);
        
        System.out.println("========================================");
        System.out.println("PROCESO COMPLETO: Descargar y Convertir a Excel");
        System.out.println("========================================\n");
        
        // Crear directorios
        Files.createDirectories(Paths.get(downloadDir));
        Files.createDirectories(Paths.get(atomDir));
        Files.createDirectories(Paths.get(excelDir));
        
        // Verificar si la carpeta atom estÃ¡ vacÃ­a
        boolean atomVacio = esCarpetaVacia(atomDir);
        int numZipsDescargar = atomVacio ? mesesHistorico : 1;
        
        System.out.println("[VERIFICACION] Carpeta atom: " + (atomVacio ? "VACIA - descargando ultimos " + numZipsDescargar + " ZIPs" : "CON ARCHIVOS - descargando solo el ultimo ZIP"));
        
        // Fase 1: Descargar archivos
        System.out.println("\n[FASE 1] Descargando archivos ZIP...");
        descargarArchivos(urls, downloadDir, numZipsDescargar);
        
        // Fase 2: Convertir ZIP a Excel (el CLI maneja la descompresion automÃ¡ticamente)
        System.out.println("\n[FASE 2] Convirtiendo archivos ZIP a Excel...");
        converter.convertirTodosZipAExcel(downloadDir, excelDir, atomDir, mesesHistorico);
        
        // Limpiar archivos ZIP despuÃ©s de convertir
        System.out.println("\n[LIMPIEZA] Eliminando archivos ZIP...");
        eliminarArchivosZip(downloadDir);
        
        // Fase 3: Subir a SharePoint (opcional)
        System.out.println("\n[FASE 3] Subiendo archivos a SharePoint...");
        subirASharePoint(excelDir);
        
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        
        // Finalizar sesion de logging
        PlacspLogger.endSession(duration);
        PlacspLogger.close();
        
        System.out.println("\n========================================");
        System.out.println("PROCESO COMPLETADO EN " + duration + " segundos");
        System.out.println("========================================");
        
        // Resumen final
        converter.mostrarResumen(excelDir);
    }
    
    /**
     * Verifica si una carpeta estÃ¡ vacÃ­a o no existe.
     * 
     * @param dirPath Ruta de la carpeta
     * @return true si estÃ¡ vacÃ­a o no existe
     */
    private boolean esCarpetaVacia(String dirPath) {
        java.io.File folder = new java.io.File(dirPath);
        if (!folder.exists() || !folder.isDirectory()) {
            return true;
        }
        String[] archivos = folder.list();
        return archivos == null || archivos.length == 0;
    }

    /**
     * Descarga archivos ZIP de las URLs especificadas.
     * 
     * @param urls URLs de las pÃ¡ginas web donde buscar enlaces
     * @param downloadDir Directorio de descarga
     * @param cantidad NÃºmero de archivos a descargar por cada URL
     * @throws IOException si hay error en las operaciones
     */
    private void descargarArchivos(String[] urls, String downloadDir, int cantidad) throws IOException {
        for (String url : urls) {
            System.out.println("  Buscando en: " + url);
            
            // Obtener los N enlaces mÃ¡s recientes (ordenados de mÃ¡s antiguo a mÃ¡s reciente)
            List<String> enlaces = webScraper.extraerEnlacesAnyoMes(url, cantidad);
            
            if (!enlaces.isEmpty()) {
                System.out.println("  Encontrados " + enlaces.size() + " enlaces");
                
                for (String enlace : enlaces) {
                    System.out.println("  Enlace encontrado: " + enlace);
                    
                    // Extraer nombre original del archivo de la URL
                    String nombreOriginal = webScraper.extraerNombreArchivo(enlace);
                    String nombreArchivo = downloadDir + "/" + nombreOriginal;
                    
                    System.out.println("  Descargando: " + nombreOriginal);
                    fileDownloader.descargarArchivo(enlace, nombreArchivo);
                }
            } else {
                System.out.println("  No se encontro enlace ANYOMES.");
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
            // Leer credenciales para OAuth2 desde EnvConfig
            String tenantId = EnvConfig.get("SHAREPOINT_TENANT_ID");
            String clientId = EnvConfig.get("SHAREPOINT_CLIENT_ID");
            String clientSecret = EnvConfig.get("SHAREPOINT_CLIENT_SECRET");
            String siteUrl = EnvConfig.get("SHAREPOINT_URL");
            String library = EnvConfig.get("SHAREPOINT_LIBRARY");
            if (tenantId == null || clientId == null || clientSecret == null || siteUrl == null || library == null) {
                System.out.println("  [INFO] SharePoint OAuth2 no configurado. Omitiendo fase de carga.");
                return;
            }
            System.out.println("  Obteniendo token OAuth2...");
            String token = OAuth2TokenHelper.getAccessToken(tenantId, clientId, clientSecret);
            if (token == null) {
                System.err.println("  [ERROR] No se pudo obtener el token OAuth2");
                return;
            }
            System.out.println("  [OK] Token obtenido");

            // Obtener siteId y driveId usando Graph
            String hostname = siteUrl.replace("https://", "").split("/", 2)[0];
            String sitePath = siteUrl.substring(siteUrl.indexOf(hostname) + hostname.length());
            System.out.println("  Buscando siteId y driveId por Graph...");
            String siteId = GraphHelper.getSiteId(hostname, sitePath, token);
            if (siteId == null) {
                System.err.println("  [ERROR] No se pudo obtener el siteId");
                return;
            }
            // Mostrar todos los drives para depuracion
            GraphHelper.listDrives(siteId, token);
            
            // Buscar el driveId usando nombres configurados o estrategias de fallback
            String driveId = null;
            String driveDisplayName = null;
            
            // Primero intentar con los nombres configurados en SHAREPOINT_DRIVE_NAMES
            String[] driveNames = EnvConfig.getSharePointDriveNames();
            if (driveNames.length > 0) {
                for (String driveName : driveNames) {
                    driveId = GraphHelper.getDriveIdByDisplayName(siteId, token, driveName);
                    if (driveId != null) {
                        driveDisplayName = driveName;
                        break;
                    }
                }
            } else {
                // Usar nombres por defecto si no hay configuracion
                // 1. Intentar 'Documentos compartidos' (espaÃ±ol)
                driveId = GraphHelper.getDriveIdByDisplayName(siteId, token, "Documentos compartidos");
                if (driveId != null) {
                    driveDisplayName = "Documentos compartidos";
                }
                
                // 2. Si no, intentar 'Documents' (inglÃ©s)
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
            }
            
            // 4. Si no, usar el drive por defecto
            if (driveId == null) {
                System.out.println("  [INFO] No se encontro biblioteca estandar, usando drive por defecto...");
                driveId = GraphHelper.getDefaultDriveId(siteId, token);
                if (driveId != null) {
                    driveDisplayName = "(por defecto)";
                }
            }
            
            if (driveId == null) {
                System.err.println("  [ERROR] No se pudo obtener ningun driveId");
                return;
            }
            System.out.println("  [OK] siteId: " + siteId);
            System.out.println("  [OK] driveId (" + driveDisplayName + "): " + driveId);

            GraphSharePointUploader uploader = new GraphSharePointUploader(token, siteId, driveId);
            java.io.File folder = new java.io.File(excelDir);
            if (!folder.exists()) {
                System.out.println("  [INFO] No hay directorio de Excel. Omitiendo carga.");
                return;
            }
            java.io.File[] excelFiles = folder.listFiles((dir, name) -> name.endsWith(".xlsx"));
            if (excelFiles == null || excelFiles.length == 0) {
                System.out.println("  [INFO] No hay archivos Excel para subir.");
                return;
            }
            int exitosos = 0;
            for (java.io.File file : excelFiles) {
                try {
                    System.out.println("  Subiendo: " + file.getName());
                    // El path destino: usar la carpeta de SHAREPOINT_LIBRARY si estÃ¡ definida
                    String destino;
                    if (library != null && !library.isEmpty()) {
                        destino = library + "/" + file.getName();
                    } else {
                        destino = file.getName();
                    }
                    // Imprimir la ruta destino en consola para depuracion de codificacion
                    System.out.println("    [DEBUG] Ruta destino (UTF-8): " + destino);
                    if (uploader.uploadFile(file.getAbsolutePath(), destino)) {
                        System.out.println("    [OK] Subido: " + file.getName());
                        exitosos++;
                        // Eliminar archivo local despuÃ©s de subir exitosamente
                        if (file.delete()) {
                            System.out.println("    [OK] Eliminado localmente: " + file.getName());
                        }
                    } else {
                        System.err.println("    [ERROR] Error al subir: " + file.getName());
                    }
                } catch (Exception e) {
                    System.err.println("    [ERROR] Error: " + e.getMessage());
                }
            }
            System.out.println("  Resumen: " + exitosos + "/" + excelFiles.length + " archivos subidos exitosamente.");
        } catch (Exception e) {
            System.err.println("  [ERROR] Error en SharePoint: " + e.getMessage());
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
                    System.out.println("  [OK] Eliminado: " + zipFile.getName());
                } else {
                    System.err.println("  [ERROR] No se pudo eliminar: " + zipFile.getName());
                }
            }
        } else {
            System.out.println("  [INFO] No hay archivos ZIP para eliminar.");
        }
    }

    /**
     * Punto de entrada principal del programa.
     * 
     * @param args Argumentos de lÃ­nea de comandos (no utilizados)
     * @throws IOException si hay error en las operaciones
     */
    public static void main(String[] args) throws IOException {
        // Obtener URLs desde configuracion .env
        String[] urls = EnvConfig.getUrls();

        PlacspWorkflow workflow = new PlacspWorkflow();
        workflow.ejecutar(urls);
    }
}
