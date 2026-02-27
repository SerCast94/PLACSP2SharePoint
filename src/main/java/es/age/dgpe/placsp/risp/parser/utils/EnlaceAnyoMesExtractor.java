package es.age.dgpe.placsp.risp.parser.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EnlaceAnyoMesExtractor {

    static {
        // Desactivar validacion SSL para pruebas
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

    public static void main(String[] args) throws IOException {
        String[] urls = {
            "https://www.hacienda.gob.es/es-es/gobiernoabierto/datos%20abiertos/paginas/licitacionescontratante.aspx",
            "https://www.hacienda.gob.es/es-es/gobiernoabierto/datos%20abiertos/paginas/licitacionesagregacion.aspx"
        };

        // Ejecutar el proceso completo
        procesarCompleto(urls);
    }
    
    public static void procesarCompleto(String[] urls) throws IOException {
        String downloadDir = System.getenv().getOrDefault("DOWNLOAD_DIR", "/app/descargas");
        String excelDir = System.getenv().getOrDefault("EXCEL_DIR", "/app/descargas/excel");
        
        long startTime = System.currentTimeMillis();
        
        System.out.println("========================================");
        System.out.println("PROCESO COMPLETO: Descargar y Convertir a Excel");
        System.out.println("========================================\n");
        
        // Crear directorios
        Files.createDirectories(Paths.get(downloadDir));
        Files.createDirectories(Paths.get(excelDir));
        
        // Fase 1: Descargar archivos
        System.out.println("[FASE 1] Descargando archivos ZIP...");
        for (String url : urls) {
            System.out.println("  Buscando en: " + url);
            String enlace = extraerPrimerEnlaceAnyoMes(url);
            if (enlace != null) {
                System.out.println("  Enlace encontrado: " + enlace);
                
                // Extraer nombre original del archivo de la URL
                String nombreOriginal = enlace.substring(enlace.lastIndexOf('/') + 1);
                String nombreArchivo = downloadDir + "/" + nombreOriginal;
                
                System.out.println("  Descargando: " + nombreOriginal);
                descargarArchivo(enlace, nombreArchivo);
            } else {
                System.out.println("  No se encontro enlace ANOMES.");
            }
        }
        
        // Fase 2: Convertir ZIP a Excel (el CLI maneja la descompresion automaticamente)
        System.out.println("\n[FASE 2] Convirtiendo archivos ZIP a Excel...");
        convertirTodosZipAExcel(downloadDir, excelDir);
        
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        
        System.out.println("\n========================================");
        System.out.println("PROCESO COMPLETADO EN " + duration + " segundos");
        System.out.println("========================================");
        
        // Resumen final
        mostrarResumen(excelDir);
    }

    public static String obtenerTokenAcceso(String tenantId, String clientId, String clientSecret) {
        try {
            // Usar Client Credentials Flow - funciona sin consentimiento del administrador
            String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
            HttpsURLConnection conn = (HttpsURLConnection) URI.create(tokenUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            
            String requestBody = "client_id=" + clientId + 
                                "&client_secret=" + clientSecret +
                                "&scope=https://magteles.sharepoint.com/.default" +
                                "&grant_type=client_credentials";
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes());
            }
            
            int responseCode = conn.getResponseCode();
            System.out.println("Codigo de respuesta del token: " + responseCode);
            
            if (responseCode == 200) {
                try (Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A")) {
                    String response = scanner.hasNext() ? scanner.next() : "";
                    // Parsear JSON manualmente para extraer access_token
                    int tokenStart = response.indexOf("\"access_token\":\"") + 17;
                    int tokenEnd = response.indexOf("\"", tokenStart);
                    if (tokenStart > 16 && tokenEnd > tokenStart) {
                        String token = response.substring(tokenStart, tokenEnd);
                        System.out.println("Token obtenido exitosamente");
                        return token;
                    }
                }
            } else {
                System.err.println("Error al obtener token: " + responseCode);
                try (Scanner scanner = new Scanner(conn.getErrorStream()).useDelimiter("\\A")) {
                    String error = scanner.hasNext() ? scanner.next() : "";
                    System.err.println("Detalle: " + error);
                }
            }
        } catch (Exception e) {
            System.err.println("Excepcion al obtener token: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static String extraerPrimerEnlaceAnyoMes(String url) throws IOException {
        Document doc = Jsoup.connect(url).get();
        Elements links = doc.select("a[href]");
        Pattern pattern = Pattern.compile("_(\\d{6})\\.zip$");
        for (Element link : links) {
            String href = link.attr("abs:href");
            Matcher matcher = pattern.matcher(href);
            if (matcher.find()) {
                return href;
            }
        }
        return null;
    }

    public static void descargarArchivo(String urlStr, String nombreArchivo) {
        try {
            HttpsURLConnection conn = (HttpsURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            
            // Obtener tamaÃ±o del archivo (puede ser -1 si el servidor no lo envÃ­a)
            long fileSize = conn.getContentLengthLong();
            boolean conoceTamano = fileSize > 0;
            
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(Paths.get(nombreArchivo))) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                long lastPrintedMB = 0;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // Mostrar progreso cada 10 MB
                    long currentMB = totalBytesRead / (1024 * 1024);
                    if (currentMB >= lastPrintedMB + 10) {
                        if (conoceTamano) {
                            double percentage = (totalBytesRead * 100.0) / fileSize;
                            double fileSizeMB = fileSize / (1024.0 * 1024.0);
                            System.out.printf("    Progreso: %.1f%% (%d MB / %.0f MB)%n", 
                                             percentage, currentMB, fileSizeMB);
                        } else {
                            System.out.printf("    Progreso: %d MB descargados%n", currentMB);
                        }
                        lastPrintedMB = currentMB;
                    }
                }
                
                // Mostrar resultado final
                double finalSizeMB = totalBytesRead / (1024.0 * 1024.0);
                System.out.printf("    [OK] Descarga completada: %s (%.2f MB)%n", nombreArchivo, finalSizeMB);
            }
        } catch (Exception e) {
            System.err.println("    [ERROR] Error al descargar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void subirASharePoint(String archivoLocal, String sharePointUrl, String sharePointPath, String accessToken) {
        try {
            String nombreArchivo = archivoLocal.substring(archivoLocal.lastIndexOf('/') + 1);
            
            // Usar REST API con token OAuth
            String restUrl = "https://magteles.sharepoint.com/sites/PrcticasTD/_api/web/GetFolderByServerRelativeUrl('/sites/PrcticasTD/Documentos%20compartidos/Colaboraci%C3%B3n')/Files/add(url='" + nombreArchivo + "',overwrite=true)";
            
            System.out.println("Subiendo a SharePoint: " + nombreArchivo);
            
            HttpsURLConnection conn = (HttpsURLConnection) URI.create(restUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            
            // Usar token de acceso OAuth
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json;odata=verbose");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            
            // Subir archivo
            try (InputStream fileIn = Files.newInputStream(Paths.get(archivoLocal));
                 OutputStream out = conn.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    if (totalBytesRead % (1024 * 1024) == 0) {
                        System.out.println("Subidos " + (totalBytesRead / (1024 * 1024)) + " MB");
                    }
                }
            }
            
            int responseCode = conn.getResponseCode();
            System.out.println("Codigo de respuesta: " + responseCode);
            
            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("Archivo subido exitosamente a SharePoint: " + nombreArchivo);
                // Eliminar archivo local despuÃ©s de subir
                Files.delete(Paths.get(archivoLocal));
                System.out.println("Archivo local eliminado: " + archivoLocal);
            } else {
                System.err.println("Error al subir a SharePoint. Codigo: " + responseCode);
                System.err.println("Respuesta: " + conn.getResponseMessage());
                try (InputStream errorStream = conn.getErrorStream()) {
                    if (errorStream != null) {
                        try (Scanner scanner = new Scanner(errorStream).useDelimiter("\\A")) {
                            String response = scanner.hasNext() ? scanner.next() : "";
                            System.err.println("Detalle error: " + response);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error al subir a SharePoint: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void descomprimirZip(String zipFilePath, String extractDir) {
        try {
            byte[] buffer = new byte[8192];
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(zipFilePath)))) {
                ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    Path newFilePath = zipSlipProtect(Paths.get(extractDir), zipEntry.getName());
                    
                    if (zipEntry.isDirectory()) {
                        Files.createDirectories(newFilePath);
                    } else {
                        Files.createDirectories(newFilePath.getParent());
                        try (OutputStream os = Files.newOutputStream(newFilePath)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                os.write(buffer, 0, len);
                            }
                        }
                    }
                    zipEntry = zis.getNextEntry();
                }
            }
            System.out.println("Archivo descomprimido exitosamente en: " + extractDir);
        } catch (Exception e) {
            System.err.println("Error al descomprimir: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Path zipSlipProtect(Path targetDir, String filename) throws IOException {
        Path resolvedPath = targetDir.resolve(filename).normalize();
        if (!resolvedPath.startsWith(targetDir.normalize())) {
            throw new IOException("Ruta zip detectada fuera del directorio de destino");
        }
        return resolvedPath;
    }

    public static void convertirTodosZipAExcel(String zipDir, String excelDir) {
        try {
            Path dirPath = Paths.get(zipDir);
            
            // Obtener lista de archivos ZIP
            java.util.List<Path> zipFiles = Files.list(dirPath)
                .filter(path -> path.toString().toLowerCase().endsWith(".zip"))
                .sorted()
                .collect(java.util.stream.Collectors.toList());
            
            if (zipFiles.isEmpty()) {
                System.out.println("No se encontraron archivos ZIP para convertir.");
                return;
            }
            
            System.out.println("Se encontraron " + zipFiles.size() + " archivos ZIP para convertir\n");
            
            // Procesar cada archivo ZIP
            int archivoActual = 0;
            for (Path zipFile : zipFiles) {
                archivoActual++;
                String nombreArchivo = zipFile.getFileName().toString();
                String nombreBase = nombreArchivo.replaceAll("\\.zip$", "");
                
                Path excelPath = Paths.get(excelDir, nombreBase + ".xlsx");
                
                System.out.printf("[Archivo %d/%d] Convirtiendo '%s'%n", 
                    archivoActual, zipFiles.size(), nombreArchivo);
                System.out.println("  Archivo destino: " + excelPath.getFileName());
                
                // Convertir ZIP a Excel usando el CLI
                convertirZipAExcelCLI(zipFile.toString(), excelPath.toString());
                
                System.out.println("  [OK] Conversion completada\n");
            }
            
            System.out.println("Todos los archivos ZIP han sido convertidos.");
        } catch (IOException e) {
            System.err.println("Error al buscar archivos ZIP: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void convertirZipAExcelCLI(String zipFilePath, String excelFilePath) {
        try {
            System.out.println("  Procesando ZIP...");
            
            // Llamar al CLI del proyecto pasando el archivo ZIP
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                String cmd = "placsp-cli.bat --in \"" + zipFilePath + "\" --out \"" + excelFilePath + "\"";
                pb = new ProcessBuilder("cmd.exe", "/c", cmd);
            } else {
                String cmd = "./placsp-cli.sh --in '" + zipFilePath + "' --out '" + excelFilePath + "'";
                pb = new ProcessBuilder("sh", "-c", cmd);
            }
            
            pb.directory(new java.io.File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Mostrar el output del proceso (filtrado)
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.contains("org.apache.logging") && 
                        !line.contains("XmlConfiguration") &&
                        !line.contains("watching for changes") &&
                        !line.contains("Starting configuration") &&
                        !line.contains("Stopping configuration")) {
                        System.out.println("    " + line);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                System.err.println("  [ERROR] Error al convertir ZIP");
            }
            
        } catch (Exception e) {
            System.err.println("Error al convertir archivo ZIP: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void mostrarResumen(String excelDir) {
        try {
            Path dirPath = Paths.get(excelDir);
            
            if (!Files.exists(dirPath)) {
                System.out.println("El directorio de Excel no existe.");
                return;
            }
            
            java.util.List<Path> excelFiles = Files.list(dirPath)
                .filter(path -> path.toString().toLowerCase().endsWith(".xlsx"))
                .sorted()
                .collect(java.util.stream.Collectors.toList());
            
            if (excelFiles.isEmpty()) {
                System.out.println("No se encontraron archivos Excel generados.");
                return;
            }
            
            long totalSize = 0;
            System.out.println("\nArchivos Excel generados:");
            for (Path file : excelFiles) {
                long fileSize = Files.size(file);
                totalSize += fileSize;
                double sizeMB = fileSize / (1024.0 * 1024.0);
                System.out.printf("  - %s (%.2f MB)%n", file.getFileName(), sizeMB);
            }
            
            double totalSizeMB = totalSize / (1024.0 * 1024.0);
            System.out.printf("\nTotal: %d archivos, %.2f MB%n", excelFiles.size(), totalSizeMB);
            System.out.println("Ubicacion: " + excelDir);
        } catch (IOException e) {
            System.err.println("Error al mostrar resumen: " + e.getMessage());
        }
    }
}
