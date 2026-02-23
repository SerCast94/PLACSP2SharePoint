package es.age.dgpe.placsp.risp.parser.uploader;

import es.age.dgpe.placsp.risp.parser.exceptions.FileSystemException;
import es.age.dgpe.placsp.risp.parser.exceptions.MemoryException;
import es.age.dgpe.placsp.risp.parser.exceptions.NetworkException;
import es.age.dgpe.placsp.risp.parser.exceptions.SharePointException;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.net.ssl.SSLException;

public class GraphSharePointUploader {
    
    private static final int CONNECT_TIMEOUT = 30000; // 30 segundos
    private static final int READ_TIMEOUT = 120000; // 2 minutos para uploads
    private static final long MAX_SIMPLE_UPLOAD_SIZE = 4 * 1024 * 1024; // 4MB límite para upload simple
    
    private final String accessToken;
    private final String siteId;
    private final String driveId;

    public GraphSharePointUploader(String accessToken, String siteId, String driveId) {
        this.accessToken = accessToken;
        this.siteId = siteId;
        this.driveId = driveId;
    }

    /**
     * Sube un archivo a una carpeta de SharePoint usando Microsoft Graph
     * @param localFilePath Ruta local del archivo
     * @param remotePath Ruta destino en la biblioteca (ej: "Colaboración/archivo.xlsx")
     * @throws SharePointException si hay error de autenticación, permisos o cuota
     * @throws NetworkException si hay error de conexión
     * @throws FileSystemException si el archivo local no existe o no se puede leer
     * @throws MemoryException si no hay memoria suficiente para cargar el archivo
     */
    public void uploadFile(String localFilePath, String remotePath) 
            throws SharePointException, NetworkException, FileSystemException, MemoryException {
        
        File file = new File(localFilePath);
        
        // Validar que el archivo existe
        if (!file.exists()) {
            PlacspLogger.fileSystemError("Archivo no encontrado para subir: " + localFilePath);
            throw FileSystemException.fileNotFound(localFilePath);
        }
        
        // Validar que el archivo no está vacío
        if (file.length() == 0) {
            PlacspLogger.validationError("Archivo vacío, no se puede subir: " + localFilePath);
            throw FileSystemException.emptyFile(localFilePath);
        }
        
        // Validar tamaño máximo para upload simple (Graph API límite 4MB)
        if (file.length() > MAX_SIMPLE_UPLOAD_SIZE) {
            PlacspLogger.warn("Archivo muy grande para upload simple (" + 
                (file.length() / 1024 / 1024) + "MB): " + file.getName() + 
                ". Considerar upload en sesión.");
        }
        
        // Verificar memoria disponible antes de cargar
        long freeMemory = Runtime.getRuntime().freeMemory();
        if (file.length() > freeMemory * 0.8) {
            PlacspLogger.memoryError("Memoria insuficiente para cargar archivo de " + 
                (file.length() / 1024 / 1024) + "MB. " + PlacspLogger.getMemoryStats());
            throw MemoryException.largeFileProcessing(localFilePath, file.length());
        }
        
        byte[] fileContent;
        try {
            fileContent = Files.readAllBytes(file.toPath());
        } catch (OutOfMemoryError e) {
            PlacspLogger.memoryError("OutOfMemory al leer archivo para subir: " + localFilePath);
            throw MemoryException.outOfMemory("Leyendo archivo para subir: " + localFilePath, e);
        } catch (IOException e) {
            PlacspLogger.fileSystemError("Error leyendo archivo para subir: " + localFilePath + " - " + e.getMessage());
            throw FileSystemException.readError(localFilePath, e);
        }
        
        // Codificar cada segmento de la ruta para caracteres especiales (tildes, etc.)
        String encodedPath = encodePath(remotePath);
        String url = "https://graph.microsoft.com/v1.0/sites/" + siteId + 
                     "/drives/" + driveId + "/root:/" + encodedPath + ":/content";
        
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Content-Length", String.valueOf(fileContent.length));
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(fileContent);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("    [OK] Archivo subido exitosamente por Graph");
                double sizeMB = fileContent.length / (1024.0 * 1024.0);
                PlacspLogger.upload(file.getName(), remotePath, sizeMB, true);
                return;
            }
            
            // Manejar diferentes códigos de error HTTP
            String errorBody = readErrorStream(conn);
            PlacspLogger.upload(file.getName(), remotePath, false);
            
            switch (responseCode) {
                case 401:
                    PlacspLogger.sharePointError("Token expirado o inválido al subir archivo: " + file.getName());
                    throw SharePointException.tokenExpired();
                    
                case 403:
                    PlacspLogger.sharePointError("Acceso denegado al subir archivo a: " + remotePath);
                    throw SharePointException.accessDenied(remotePath, errorBody);
                    
                case 404:
                    PlacspLogger.sharePointError("Ruta destino no encontrada: " + remotePath);
                    throw SharePointException.folderNotFound(remotePath);
                    
                case 409:
                    PlacspLogger.sharePointError("Conflicto al subir archivo (posiblemente bloqueado): " + file.getName());
                    throw SharePointException.uploadFailed(file.getName(), "Conflicto: archivo bloqueado o en uso", null);
                    
                case 413:
                    PlacspLogger.sharePointError("Archivo demasiado grande para subir: " + file.getName() + 
                        " (" + (file.length() / 1024 / 1024) + "MB)");
                    throw SharePointException.fileTooLarge(file.getName(), file.length());
                    
                case 429:
                    PlacspLogger.sharePointError("Demasiadas solicitudes (rate limit) al subir: " + file.getName());
                    throw SharePointException.rateLimitExceeded();
                    
                case 500:
                case 502:
                case 503:
                case 504:
                    PlacspLogger.sharePointError("Error del servidor SharePoint (" + responseCode + ") al subir: " + file.getName());
                    throw SharePointException.serverError(responseCode, errorBody);
                    
                case 507:
                    PlacspLogger.sharePointError("Cuota excedida en SharePoint al subir: " + file.getName());
                    throw SharePointException.quotaExceeded(driveId);
                    
                default:
                    PlacspLogger.sharePointError("Error HTTP " + responseCode + " al subir " + file.getName() + ": " + errorBody);
                    throw SharePointException.uploadFailed(file.getName(), 
                        "Error HTTP " + responseCode + ": " + errorBody, null);
            }
            
        } catch (SocketTimeoutException e) {
            PlacspLogger.upload(file.getName(), remotePath, false);
            PlacspLogger.networkError("Timeout al subir archivo: " + file.getName() + " - " + e.getMessage());
            throw NetworkException.timeout(url, READ_TIMEOUT / 1000);
            
        } catch (UnknownHostException e) {
            PlacspLogger.upload(file.getName(), remotePath, false);
            PlacspLogger.networkError("Host no encontrado (graph.microsoft.com): " + e.getMessage());
            throw NetworkException.dnsNotResolved("graph.microsoft.com");
            
        } catch (ConnectException e) {
            PlacspLogger.upload(file.getName(), remotePath, false);
            PlacspLogger.networkError("Conexión rechazada al subir archivo: " + e.getMessage());
            throw NetworkException.connectionRefused("graph.microsoft.com", 443);
            
        } catch (SSLException e) {
            PlacspLogger.upload(file.getName(), remotePath, false);
            PlacspLogger.networkError("Error SSL al subir archivo: " + e.getMessage());
            throw NetworkException.sslError("graph.microsoft.com", e);
            
        } catch (IOException e) {
            PlacspLogger.upload(file.getName(), remotePath, false);
            PlacspLogger.networkError("Error de conexión al subir archivo: " + e.getMessage());
            throw SharePointException.connectionError("graph.microsoft.com", e);
            
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * Codifica la ruta para URL, manejando caracteres especiales
     */
    private String encodePath(String remotePath) {
        String[] pathParts = remotePath.split("/");
        StringBuilder encodedPath = new StringBuilder();
        for (int i = 0; i < pathParts.length; i++) {
            if (i > 0) encodedPath.append("/");
            try {
                encodedPath.append(URLEncoder.encode(pathParts[i], "UTF-8").replace("+", "%20"));
            } catch (UnsupportedEncodingException e) {
                // UTF-8 siempre está disponible
                encodedPath.append(pathParts[i]);
            }
        }
        return encodedPath.toString();
    }
    
    /**
     * Lee el stream de error de una conexión HTTP
     */
    private String readErrorStream(HttpURLConnection conn) {
        try {
            InputStream errorStream = conn.getErrorStream();
            if (errorStream == null) {
                return "Sin detalles de error";
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                return reader.lines().reduce("", (a, b) -> a + b);
            }
        } catch (Exception e) {
            System.err.println("  [ERROR] Error al subir archivo por Graph: " + e.getMessage());
            // Registrar excepcion en log
            PlacspLogger.upload(localFilePath, remotePath, false);
            PlacspLogger.error("Excepcion subiendo archivo", e);
            return false;
        }
    }
}
