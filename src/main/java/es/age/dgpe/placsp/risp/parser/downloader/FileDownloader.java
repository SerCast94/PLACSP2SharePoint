package es.age.dgpe.placsp.risp.parser.downloader;

import es.age.dgpe.placsp.risp.parser.exceptions.DownloadException;
import es.age.dgpe.placsp.risp.parser.exceptions.NetworkException;
import es.age.dgpe.placsp.risp.parser.exceptions.FileSystemException;
import es.age.dgpe.placsp.risp.parser.utils.EnvConfig;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Clase responsable de descargar archivos desde URLs HTTP/HTTPS.
 * Maneja la descarga con indicador de progreso en consola.
 * 
 * Parametros configurables desde .env:
 * - DOWNLOAD_BUFFER_SIZE: TamaÃ±o del buffer de descarga
 * - DOWNLOAD_PROGRESS_INTERVAL_MB: Intervalo para mostrar progreso
 * - HTTP_CONNECT_TIMEOUT: Timeout de conexion
 * - HTTP_READ_TIMEOUT: Timeout de lectura
 */
public class FileDownloader {

    /**
     * Descarga un archivo desde una URL y muestra el progreso.
     * 
     * @param urlStr URL del archivo a descargar
     * @param nombreArchivo Ruta local donde guardar el archivo
     * @throws DownloadException si hay error durante la descarga
     * @throws NetworkException si hay error de red
     */
    public void descargarArchivo(String urlStr, String nombreArchivo) throws DownloadException, NetworkException {
        // Cargar configuracion desde .env
        int bufferSize = EnvConfig.getDownloadBufferSize();
        int progressIntervalMb = EnvConfig.getDownloadProgressIntervalMb();
        int connectTimeout = EnvConfig.getHttpConnectTimeout();
        int readTimeout = EnvConfig.getHttpReadTimeout();
        
        HttpURLConnection conn = null;
        Path filePath = Paths.get(nombreArchivo);
        
        try {
            // Crear directorios padre si no existen
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                try {
                    Files.createDirectories(parentDir);
                } catch (IOException e) {
                    throw FileSystemException.createDirectoryFailed(parentDir.toString(), e);
                }
            }
            
            // Establecer conexión
            conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            // Verificar código de respuesta
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                PlacspLogger.error("ERR_HTTP_" + responseCode, 
                    "Error HTTP al descargar archivo", 
                    "URL: " + urlStr + " | Código: " + responseCode);
                throw DownloadException.httpError(urlStr, responseCode);
            }
            
            // Obtener tamaÃ±o del archivo (puede ser -1 si el servidor no lo envÃ­a)
            long fileSize = conn.getContentLengthLong();
            boolean conoceTamano = fileSize > 0;
            
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(filePath)) {
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                long totalBytesRead = 0;
                long lastPrintedMB = 0;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // Mostrar progreso segun intervalo configurado
                    long currentMB = totalBytesRead / (1024 * 1024);
                    if (currentMB >= lastPrintedMB + progressIntervalMb) {
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
                
                // Validar que se descargó algo
                if (totalBytesRead == 0) {
                    PlacspLogger.validationError(nombreArchivo, "ARCHIVO_VACIO", "La descarga produjo un archivo vacío");
                    throw DownloadException.emptyFile(nombreArchivo);
                }
                
                // Validar descarga completa si se conocía el tamaño
                if (conoceTamano && totalBytesRead < fileSize) {
                    PlacspLogger.error("ERR_DESCARGA_INCOMPLETA", 
                        "Descarga incompleta", 
                        String.format("Esperados: %d bytes, Recibidos: %d bytes", fileSize, totalBytesRead));
                    throw DownloadException.incompleteDownload(nombreArchivo, fileSize, totalBytesRead);
                }
                
                // Mostrar resultado final
                double finalSizeMB = totalBytesRead / (1024.0 * 1024.0);
                System.out.printf("    [OK] Descarga completada: %s (%.2f MB)%n", nombreArchivo, finalSizeMB);
                
                // Registrar en log
                PlacspLogger.download(nombreArchivo, urlStr, finalSizeMB, true);
            }
            
        } catch (SocketTimeoutException e) {
            PlacspLogger.networkError(urlStr, "TIMEOUT", e);
            PlacspLogger.download(nombreArchivo, urlStr, false);
            cleanupFailedDownload(filePath);
            throw NetworkException.timeout(urlStr, e);
            
        } catch (UnknownHostException e) {
            PlacspLogger.networkError(urlStr, "DNS_NO_RESUELTO", e);
            PlacspLogger.download(nombreArchivo, urlStr, false);
            cleanupFailedDownload(filePath);
            throw NetworkException.dnsNotResolved(urlStr, e);
            
        } catch (ConnectException e) {
            PlacspLogger.networkError(urlStr, "CONEXION_RECHAZADA", e);
            PlacspLogger.download(nombreArchivo, urlStr, false);
            cleanupFailedDownload(filePath);
            throw NetworkException.connectionRefused(urlStr, e);
            
        } catch (SSLException e) {
            PlacspLogger.networkError(urlStr, "ERROR_SSL", e);
            PlacspLogger.download(nombreArchivo, urlStr, false);
            cleanupFailedDownload(filePath);
            throw NetworkException.sslError(urlStr, e);
            
        } catch (java.io.EOFException e) {
            PlacspLogger.error("ERR_EOF", "Conexión cerrada inesperadamente", "URL: " + urlStr);
            PlacspLogger.download(nombreArchivo, urlStr, false);
            cleanupFailedDownload(filePath);
            throw DownloadException.connectionInterrupted(urlStr, e);
            
        } catch (DownloadException e) {
            // Re-lanzar excepciones ya tipificadas
            throw e;
            
        } catch (IOException e) {
            // Determinar si es error de escritura o de lectura
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("no space") || errorMsg.contains("disk full") || errorMsg.contains("espacio")) {
                PlacspLogger.fileSystemError("DISCO_LLENO", nombreArchivo, e);
                PlacspLogger.download(nombreArchivo, urlStr, false);
                cleanupFailedDownload(filePath);
                throw new DownloadException("ERR_DISK_FULL", "No hay espacio en disco para guardar: " + nombreArchivo, e);
            } else if (errorMsg.contains("permission") || errorMsg.contains("denied") || errorMsg.contains("acceso")) {
                PlacspLogger.fileSystemError("PERMISO_DENEGADO", nombreArchivo, e);
                PlacspLogger.download(nombreArchivo, urlStr, false);
                cleanupFailedDownload(filePath);
                throw new DownloadException("ERR_PERMISSION", "Sin permisos para escribir: " + nombreArchivo, e);
            } else {
                PlacspLogger.error("Error IO durante descarga", e);
                PlacspLogger.download(nombreArchivo, urlStr, false);
                cleanupFailedDownload(filePath);
                throw new DownloadException("Error al descargar archivo: " + nombreArchivo, e);
            }
            
        } catch (Exception e) {
            // Capturar cualquier otra excepción inesperada
            PlacspLogger.error("Error inesperado durante descarga", e);
            PlacspLogger.download(nombreArchivo, urlStr, false);
            cleanupFailedDownload(filePath);
            throw new DownloadException("Error inesperado al descargar: " + nombreArchivo, e);
            
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * Elimina un archivo de descarga fallida para evitar archivos corruptos.
     */
    private void cleanupFailedDownload(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                PlacspLogger.info("Archivo parcial eliminado: " + filePath);
            }
        } catch (IOException ignored) {
            // Ignorar error al limpiar
        }
    }

    /**
     * Calcula el tamaÃ±o de un archivo en MB.
     * 
     * @param filePath Ruta del archivo
     * @return TamaÃ±o en MB
     * @throws IOException si hay error al leer el archivo
     */
    public double obtenerTamanoMB(String filePath) throws IOException {
        long fileSize = Files.size(Paths.get(filePath));
        return fileSize / (1024.0 * 1024.0);
    }
}
