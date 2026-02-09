package es.age.dgpe.placsp.risp.parser.downloader;

import es.age.dgpe.placsp.risp.parser.utils.EnvConfig;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Clase responsable de descargar archivos desde URLs HTTP/HTTPS.
 * Maneja la descarga con indicador de progreso en consola.
 * 
 * ParÃ¡metros configurables desde .env:
 * - DOWNLOAD_BUFFER_SIZE: TamaÃ±o del buffer de descarga
 * - DOWNLOAD_PROGRESS_INTERVAL_MB: Intervalo para mostrar progreso
 * - HTTP_CONNECT_TIMEOUT: Timeout de conexiÃ³n
 * - HTTP_READ_TIMEOUT: Timeout de lectura
 */
public class FileDownloader {

    /**
     * Descarga un archivo desde una URL y muestra el progreso.
     * 
     * @param urlStr URL del archivo a descargar
     * @param nombreArchivo Ruta local donde guardar el archivo
     */
    public void descargarArchivo(String urlStr, String nombreArchivo) {
        // Cargar configuraciÃ³n desde .env
        int bufferSize = EnvConfig.getDownloadBufferSize();
        int progressIntervalMb = EnvConfig.getDownloadProgressIntervalMb();
        int connectTimeout = EnvConfig.getHttpConnectTimeout();
        int readTimeout = EnvConfig.getHttpReadTimeout();
        
        try {
            HttpsURLConnection conn = (HttpsURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            // Obtener tamaÃ±o del archivo (puede ser -1 si el servidor no lo envÃ­a)
            long fileSize = conn.getContentLengthLong();
            boolean conoceTamano = fileSize > 0;
            
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(Paths.get(nombreArchivo))) {
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                long totalBytesRead = 0;
                long lastPrintedMB = 0;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // Mostrar progreso segÃºn intervalo configurado
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
                
                // Mostrar resultado final
                double finalSizeMB = totalBytesRead / (1024.0 * 1024.0);
                System.out.printf("    [OK] Descarga completada: %s (%.2f MB)%n", nombreArchivo, finalSizeMB);
                
                // Registrar en log
                PlacspLogger.download(nombreArchivo, urlStr, finalSizeMB, true);
            }
        } catch (Exception e) {
            System.err.println("    [ERROR] Error al descargar: " + e.getMessage());
            PlacspLogger.download(nombreArchivo, urlStr, false);
            PlacspLogger.error("Error descargando " + nombreArchivo, e);
            e.printStackTrace();
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
