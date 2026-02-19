package es.age.dgpe.placsp.risp.parser.workflow;

import es.age.dgpe.placsp.risp.parser.downloader.FileDownloader;
import es.age.dgpe.placsp.risp.parser.downloader.WebScraper;
import es.age.dgpe.placsp.risp.parser.converter.AtomToExcelConverter;
import es.age.dgpe.placsp.risp.parser.uploader.OAuth2TokenHelper;
import es.age.dgpe.placsp.risp.parser.uploader.GraphHelper;
import es.age.dgpe.placsp.risp.parser.uploader.GraphSharePointUploader;
import es.age.dgpe.placsp.risp.parser.utils.EnvConfig;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;
import es.age.dgpe.placsp.risp.parser.exceptions.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Orquestador principal del flujo de trabajo PLACSP.
 * Coordina las operaciones de descarga y conversión de datos de contratación pública.
 * 
 * Flujo:
 * 1. Extrae enlaces de archivos ZIP desde páginas web gubernamentales
 * 2. Descarga los archivos ZIP
 * 3. Convierte los ZIP a formato Excel usando el CLI
 * 4. Sube los archivos Excel a SharePoint
 * 5. Muestra resumen de archivos generados
 */
public class PlacspWorkflow {

    private final WebScraper webScraper;
    private final FileDownloader fileDownloader;
    private final AtomToExcelConverter converter;
    
    // Contadores para el resumen final
    private int archivosDescargados = 0;
    private int archivosConvertidos = 0;
    private int archivosSubidos = 0;
    private int erroresDescarga = 0;
    private int erroresConversion = 0;
    private int erroresSubida = 0;

    static {
        // Desactivar validación SSL si está configurado (solo para pruebas)
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
                PlacspLogger.error("Error configurando SSL: " + e.getMessage());
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
     * Ejecuta el proceso completo: descarga y conversión.
     * 
     * @param urls URLs de las páginas web donde buscar enlaces de descarga
     */
    public void ejecutar(String[] urls) {
        // Obtener configuración de directorios desde .env
        String downloadDir;
        String atomDir;
        String excelDir;
        int mesesHistorico;
        
        try {
            downloadDir = EnvConfig.getDownloadDir();
            atomDir = EnvConfig.getAtomDir();
            excelDir = EnvConfig.getExcelDir();
            mesesHistorico = EnvConfig.getMesesHistorico();
        } catch (Exception e) {
            PlacspLogger.configError("Error leyendo configuración: " + e.getMessage());
            System.err.println("[ERROR FATAL] No se pudo leer la configuración: " + e.getMessage());
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        // Iniciar sesión de logging
        PlacspLogger.startSession();
        PlacspLogger.info("URLs a procesar: " + urls.length);
        PlacspLogger.info("Estado de memoria inicial: " + PlacspLogger.getMemoryStats());
        
        System.out.println("========================================");
        System.out.println("PROCESO COMPLETO: Descargar y Convertir a Excel");
        System.out.println("========================================\n");
        
        // Crear directorios
        try {
            Files.createDirectories(Paths.get(downloadDir));
            Files.createDirectories(Paths.get(atomDir));
            Files.createDirectories(Paths.get(excelDir));
        } catch (IOException e) {
            PlacspLogger.fileSystemError("Error creando directorios: " + e.getMessage());
            System.err.println("[ERROR FATAL] No se pudieron crear los directorios necesarios: " + e.getMessage());
            PlacspLogger.close();
            return;
        }
        
        // Verificar si la carpeta atom está vacía
        boolean atomVacio = esCarpetaVacia(atomDir);
        int numZipsDescargar = atomVacio ? mesesHistorico : 1;
        
        System.out.println("[VERIFICACION] Carpeta atom: " + (atomVacio ? "VACIA - descargando ultimos " + numZipsDescargar + " ZIPs" : "CON ARCHIVOS - descargando solo el ultimo ZIP"));
        PlacspLogger.info("Carpeta atom " + (atomVacio ? "vacía" : "con archivos") + " - descargando " + numZipsDescargar + " ZIP(s)");
        
        // Fase 1: Descargar archivos
        System.out.println("\n[FASE 1] Descargando archivos ZIP...");
        PlacspLogger.info("=== FASE 1: Descarga de archivos ZIP ===");
        try {
            descargarArchivos(urls, downloadDir, numZipsDescargar);
            PlacspLogger.info("Fase 1 completada: " + archivosDescargados + " descargados, " + erroresDescarga + " errores");
        } catch (Exception e) {
            PlacspLogger.error("Error fatal en fase de descarga", e);
            System.err.println("[ERROR] Fase de descarga fallida: " + e.getMessage());
        }
        
        // Fase 2: Convertir ZIP a Excel
        System.out.println("\n[FASE 2] Convirtiendo archivos ZIP a Excel...");
        PlacspLogger.info("=== FASE 2: Conversión ZIP a Excel ===");
        try {
            converter.convertirTodosZipAExcel(downloadDir, excelDir, atomDir, mesesHistorico);
            // Contar archivos Excel generados
            java.io.File excelFolder = new java.io.File(excelDir);
            java.io.File[] excels = excelFolder.listFiles((d, n) -> n.endsWith(".xlsx"));
            archivosConvertidos = excels != null ? excels.length : 0;
            PlacspLogger.info("Fase 2 completada: " + archivosConvertidos + " archivos Excel generados");
        } catch (ConversionException e) {
            erroresConversion++;
            PlacspLogger.error(e);
            System.err.println("[ERROR] Error de conversión: " + e.getMessage());
        } catch (DecompressionException e) {
            erroresConversion++;
            PlacspLogger.error(e);
            System.err.println("[ERROR] Error de descompresión: " + e.getMessage());
        } catch (Exception e) {
            erroresConversion++;
            // Detectar si es error de memoria
            if (e.getCause() instanceof OutOfMemoryError || 
                (e.getMessage() != null && e.getMessage().toLowerCase().contains("memory"))) {
                PlacspLogger.memoryError("Error de memoria durante conversión: " + e.getMessage());
            } else {
                PlacspLogger.error("Error inesperado en conversión", e);
            }
            System.err.println("[ERROR] Error en conversión: " + e.getMessage());
        }
        
        // Limpiar archivos ZIP después de convertir
        System.out.println("\n[LIMPIEZA] Eliminando archivos ZIP...");
        eliminarArchivosZip(downloadDir);
        
        // Fase 3: Subir a SharePoint (opcional)
        System.out.println("\n[FASE 3] Subiendo archivos a SharePoint...");
        PlacspLogger.info("=== FASE 3: Subida a SharePoint ===");
        subirASharePoint(excelDir);
        PlacspLogger.info("Fase 3 completada: " + archivosSubidos + " subidos, " + erroresSubida + " errores");
        
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        
        // Resumen de errores
        int totalErrores = erroresDescarga + erroresConversion + erroresSubida;
        if (totalErrores > 0) {
            PlacspLogger.warn("Proceso completado con " + totalErrores + " errores: " +
                erroresDescarga + " descarga, " + erroresConversion + " conversión, " + erroresSubida + " subida");
        }
        
        // Finalizar sesión de logging
        PlacspLogger.info("Estado de memoria final: " + PlacspLogger.getMemoryStats());
        PlacspLogger.endSession(duration);
        PlacspLogger.close();
        
        System.out.println("\n========================================");
        System.out.println("PROCESO COMPLETADO EN " + duration + " segundos");
        System.out.println("  Descargados: " + archivosDescargados + " | Errores: " + erroresDescarga);
        System.out.println("  Convertidos: " + archivosConvertidos + " | Errores: " + erroresConversion);
        System.out.println("  Subidos: " + archivosSubidos + " | Errores: " + erroresSubida);
        System.out.println("========================================");
        
        // Resumen final
        converter.mostrarResumen(excelDir);
    }
    
    /**
     * Verifica si una carpeta está vacía o no existe.
     * 
     * @param dirPath Ruta de la carpeta
     * @return true si está vacía o no existe
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
     * @param urls URLs de las páginas web donde buscar enlaces
     * @param downloadDir Directorio de descarga
     * @param cantidad Número de archivos a descargar por cada URL
     */
    private void descargarArchivos(String[] urls, String downloadDir, int cantidad) {
        for (String url : urls) {
            System.out.println("  Buscando en: " + url);
            PlacspLogger.info("Buscando enlaces en: " + url);
            
            List<String> enlaces;
            try {
                // Obtener los N enlaces más recientes (ordenados de más antiguo a más reciente)
                enlaces = webScraper.extraerEnlacesAnyoMes(url, cantidad);
            } catch (NetworkException e) {
                erroresDescarga++;
                PlacspLogger.error(e);
                System.err.println("  [ERROR] Error de red accediendo a " + url + ": " + e.getMessage());
                continue;
            } catch (DownloadException e) {
                erroresDescarga++;
                PlacspLogger.error(e);
                System.err.println("  [ERROR] Error descargando página " + url + ": " + e.getMessage());
                continue;
            } catch (Exception e) {
                erroresDescarga++;
                PlacspLogger.error("Error inesperado buscando enlaces en " + url, e);
                System.err.println("  [ERROR] Error inesperado: " + e.getMessage());
                continue;
            }
            
            if (!enlaces.isEmpty()) {
                System.out.println("  Encontrados " + enlaces.size() + " enlaces");
                PlacspLogger.info("Encontrados " + enlaces.size() + " enlaces en " + url);
                
                for (String enlace : enlaces) {
                    System.out.println("  Enlace encontrado: " + enlace);
                    
                    // Extraer nombre original del archivo de la URL
                    String nombreOriginal = webScraper.extraerNombreArchivo(enlace);
                    String nombreArchivo = downloadDir + "/" + nombreOriginal;
                    
                    System.out.println("  Descargando: " + nombreOriginal);
                    
                    try {
                        fileDownloader.descargarArchivo(enlace, nombreArchivo);
                        archivosDescargados++;
                        PlacspLogger.info("Descargado exitosamente: " + nombreOriginal);
                    } catch (NetworkException e) {
                        erroresDescarga++;
                        PlacspLogger.error(e);
                        System.err.println("  [ERROR] Error de red descargando " + nombreOriginal + ": " + e.getMessage());
                    } catch (DownloadException e) {
                        erroresDescarga++;
                        PlacspLogger.error(e);
                        System.err.println("  [ERROR] Error descargando " + nombreOriginal + ": " + e.getMessage());
                    } catch (Exception e) {
                        erroresDescarga++;
                        PlacspLogger.error("Error inesperado descargando " + nombreOriginal, e);
                        System.err.println("  [ERROR] Error inesperado: " + e.getMessage());
                    }
                }
            } else {
                System.out.println("  No se encontro enlace ANYOMES.");
                PlacspLogger.warn("No se encontraron enlaces en: " + url);
            }
        }
    }

    /**
     * Sube los archivos Excel a SharePoint.
     * 
     * @param excelDir Directorio con los archivos Excel a subir
     */
    private void subirASharePoint(String excelDir) {
        // Leer credenciales para OAuth2 desde EnvConfig
        String tenantId = EnvConfig.get("SHAREPOINT_TENANT_ID");
        String clientId = EnvConfig.get("SHAREPOINT_CLIENT_ID");
        String clientSecret = EnvConfig.get("SHAREPOINT_CLIENT_SECRET");
        String siteUrl = EnvConfig.get("SHAREPOINT_URL");
        String library = EnvConfig.get("SHAREPOINT_LIBRARY");
        
        if (tenantId == null || clientId == null || clientSecret == null || siteUrl == null || library == null) {
            System.out.println("  [INFO] SharePoint OAuth2 no configurado. Omitiendo fase de carga.");
            PlacspLogger.info("SharePoint no configurado - fase de carga omitida");
            return;
        }
        
        // Obtener token OAuth2
        String token;
        System.out.println("  Obteniendo token OAuth2...");
        try {
            token = OAuth2TokenHelper.getAccessToken(tenantId, clientId, clientSecret);
            System.out.println("  [OK] Token obtenido");
            PlacspLogger.info("Token OAuth2 obtenido exitosamente");
        } catch (SharePointException e) {
            erroresSubida++;
            PlacspLogger.error(e);
            System.err.println("  [ERROR] Error de autenticación SharePoint: " + e.getMessage());
            return;
        } catch (Exception e) {
            erroresSubida++;
            PlacspLogger.error("Error obteniendo token OAuth2", e);
            System.err.println("  [ERROR] Error obteniendo token: " + e.getMessage());
            return;
        }

        // Obtener siteId y driveId usando Graph
        String hostname = siteUrl.replace("https://", "").split("/", 2)[0];
        String sitePath = siteUrl.substring(siteUrl.indexOf(hostname) + hostname.length());
        
        String siteId;
        System.out.println("  Buscando siteId y driveId por Graph...");
        try {
            siteId = GraphHelper.getSiteId(hostname, sitePath, token);
        } catch (SharePointException e) {
            erroresSubida++;
            PlacspLogger.error(e);
            System.err.println("  [ERROR] Error obteniendo siteId: " + e.getMessage());
            return;
        }
        
        // Mostrar todos los drives para depuración
        try {
            GraphHelper.listDrives(siteId, token);
        } catch (SharePointException e) {
            PlacspLogger.warn("No se pudieron listar los drives: " + e.getMessage());
        }
        
        // Buscar el driveId usando nombres configurados o estrategias de fallback
        String driveId = null;
        String driveDisplayName = null;
        
        try {
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
                // Usar nombres por defecto si no hay configuración
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
            }
            
            // 4. Si no, usar el drive por defecto
            if (driveId == null) {
                System.out.println("  [INFO] No se encontro biblioteca estandar, usando drive por defecto...");
                driveId = GraphHelper.getDefaultDriveId(siteId, token);
                if (driveId != null) {
                    driveDisplayName = "(por defecto)";
                }
            }
        } catch (SharePointException e) {
            erroresSubida++;
            PlacspLogger.error(e);
            System.err.println("  [ERROR] Error buscando driveId: " + e.getMessage());
            return;
        }
        
        if (driveId == null) {
            erroresSubida++;
            PlacspLogger.sharePointError("No se pudo encontrar ningún driveId en el sitio");
            System.err.println("  [ERROR] No se pudo obtener ningun driveId");
            return;
        }
        
        System.out.println("  [OK] siteId: " + siteId);
        System.out.println("  [OK] driveId (" + driveDisplayName + "): " + driveId);
        PlacspLogger.info("Conectado a SharePoint - Site: " + siteId + ", Drive: " + driveDisplayName);

        GraphSharePointUploader uploader = new GraphSharePointUploader(token, siteId, driveId);
        java.io.File folder = new java.io.File(excelDir);
        
        if (!folder.exists()) {
            System.out.println("  [INFO] No hay directorio de Excel. Omitiendo carga.");
            PlacspLogger.info("Directorio Excel no existe - nada que subir");
            return;
        }
        
        java.io.File[] excelFiles = folder.listFiles((dir, name) -> name.endsWith(".xlsx"));
        if (excelFiles == null || excelFiles.length == 0) {
            System.out.println("  [INFO] No hay archivos Excel para subir.");
            PlacspLogger.info("No hay archivos Excel para subir");
            return;
        }
        
        PlacspLogger.info("Subiendo " + excelFiles.length + " archivos Excel a SharePoint");
        
        for (java.io.File file : excelFiles) {
            System.out.println("  Subiendo: " + file.getName());
            
            // El path destino: usar la carpeta de SHAREPOINT_LIBRARY si está definida
            String destino;
            if (library != null && !library.isEmpty()) {
                destino = library + "/" + file.getName();
            } else {
                destino = file.getName();
            }
            
            try {
                uploader.uploadFile(file.getAbsolutePath(), destino);
                System.out.println("    [OK] Subido: " + file.getName());
                archivosSubidos++;
                
                // Eliminar archivo local después de subir exitosamente
                if (file.delete()) {
                    System.out.println("    [OK] Eliminado localmente: " + file.getName());
                } else {
                    PlacspLogger.warn("No se pudo eliminar archivo local: " + file.getName());
                }
                
            } catch (SharePointException e) {
                erroresSubida++;
                PlacspLogger.error(e);
                System.err.println("    [ERROR] Error SharePoint subiendo " + file.getName() + ": " + e.getMessage());
                
                // Si es error de cuota o token expirado, no continuar
                if (e.getMessage().contains("cuota") || e.getMessage().contains("quota") ||
                    e.getMessage().contains("token expirado") || e.getMessage().contains("Token expirado")) {
                    System.err.println("  [ERROR FATAL] Error crítico, abortando subida de archivos restantes");
                    PlacspLogger.error("Subida abortada por error crítico: " + e.getMessage());
                    break;
                }
                
            } catch (NetworkException e) {
                erroresSubida++;
                PlacspLogger.error(e);
                System.err.println("    [ERROR] Error de red subiendo " + file.getName() + ": " + e.getMessage());
                
            } catch (FileSystemException e) {
                erroresSubida++;
                PlacspLogger.error(e);
                System.err.println("    [ERROR] Error de archivo subiendo " + file.getName() + ": " + e.getMessage());
                
            } catch (MemoryException e) {
                erroresSubida++;
                PlacspLogger.error(e);
                System.err.println("    [ERROR] Error de memoria subiendo " + file.getName() + ": " + e.getMessage());
                // Error de memoria es crítico
                System.err.println("  [ERROR FATAL] Memoria insuficiente, abortando subida");
                PlacspLogger.error("Subida abortada por falta de memoria");
                break;
                
            } catch (Exception e) {
                erroresSubida++;
                PlacspLogger.error("Error inesperado subiendo " + file.getName(), e);
                System.err.println("    [ERROR] Error inesperado: " + e.getMessage());
            }
        }
        
        System.out.println("  Resumen: " + archivosSubidos + "/" + excelFiles.length + " archivos subidos exitosamente.");
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
                    PlacspLogger.fileSystemError("No se pudo eliminar ZIP: " + zipFile.getName());
                }
            }
        } else {
            System.out.println("  [INFO] No hay archivos ZIP para eliminar.");
        }
    }

    /**
     * Punto de entrada principal del programa.
     * 
     * @param args Argumentos de línea de comandos (no utilizados)
     */
    public static void main(String[] args) {
        try {
            // FORZAR EXCEPCIÓN DE VALIDACIÓN PARA PROBAR LOG EN ISO-8859-1
            throw ValidationException.invalidFileFormat("archivo.txt", "formato esperado");
        } catch (OutOfMemoryError e) {
            PlacspLogger.memoryError("OutOfMemoryError en el proceso principal: " + e.getMessage());
            System.err.println("[ERROR FATAL] Memoria insuficiente: " + e.getMessage());
            System.err.println("Considere aumentar la memoria con: java -Xmx2g ...");
        } catch (Exception e) {
            PlacspLogger.error("Error fatal en el proceso principal", e);
            System.err.println("[ERROR FATAL] " + e.getMessage());
            e.printStackTrace();
        } finally {
            PlacspLogger.close();
        }
    }
}
