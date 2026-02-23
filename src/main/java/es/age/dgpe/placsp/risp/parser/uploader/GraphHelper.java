package es.age.dgpe.placsp.risp.parser.uploader;

import es.age.dgpe.placsp.risp.parser.exceptions.SharePointException;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Scanner;

public class GraphHelper {
    
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 30000;
    
    /**
     * Obtiene el driveId de la biblioteca por displayName
     * @throws SharePointException si hay error
     */
    public static String getDriveIdByDisplayName(String siteId, String accessToken, String displayName) throws SharePointException {
        String url = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives";
        
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                String response;
                try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    response = scanner.next();
                }
                // Buscar el displayName en el JSON
                String[] items = response.split("\\{");
                for (String item : items) {
                    if (item.contains("displayName") && item.contains("id")) {
                        int dnIdx = item.indexOf("\"displayName\":");
                        int dnStart = item.indexOf('"', dnIdx + 14) + 1;
                        int dnEnd = item.indexOf('"', dnStart);
                        String dn = item.substring(dnStart, dnEnd);
                        if (dn.equals(displayName)) {
                            int idIdx = item.indexOf("\"id\":");
                            int idStart = item.indexOf('"', idIdx + 5) + 1;
                            int idEnd = item.indexOf('"', idStart);
                            String id = item.substring(idStart, idEnd);
                            PlacspLogger.info("DriveId encontrado para '" + displayName + "': " + id);
                            return id;
                        }
                    }
                }
                // No se encontró - devolver null sin lanzar excepción
                return null;
                
            } else if (responseCode == 401) {
                PlacspLogger.sharePointError("BUSCAR_DRIVE", displayName, responseCode, "Token expirado o inválido");
                throw SharePointException.tokenExpired();
                
            } else if (responseCode == 403) {
                PlacspLogger.sharePointError("BUSCAR_DRIVE", displayName, responseCode, "Acceso denegado");
                throw SharePointException.accessDenied("Drive: " + displayName);
                
            } else if (responseCode == 404) {
                return null; // No encontrado, no es error
                
            } else {
                String error = "";
                try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                    error = scanner.hasNext() ? scanner.next() : "Sin detalles";
                }
                PlacspLogger.sharePointError("BUSCAR_DRIVE", displayName, responseCode, error);
                throw SharePointException.graphApiError(responseCode, error);
            }
            
        } catch (SocketTimeoutException e) {
            PlacspLogger.networkError(url, "TIMEOUT", e);
            throw SharePointException.connectionError(e);
            
        } catch (UnknownHostException e) {
            PlacspLogger.networkError(url, "DNS_NO_RESUELTO", e);
            throw SharePointException.connectionError(e);
            
        } catch (SharePointException e) {
            throw e;
            
        } catch (IOException e) {
            PlacspLogger.error("Error buscando driveId", e);
            throw SharePointException.connectionError(e);
            
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
    
    /**
     * Lista todos los drives (bibliotecas) de un sitio SharePoint usando Microsoft Graph
     * @throws SharePointException si hay error
     */
    public static void listDrives(String siteId, String accessToken) throws SharePointException {
        String url = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives";
        
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                // Drives listados correctamente (output silenciado)
            } else if (responseCode == 401) {
                throw SharePointException.tokenExpired();
            } else if (responseCode == 403) {
                throw SharePointException.accessDenied("Listar drives");
            } else {
                String error = "";
                try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                    error = scanner.hasNext() ? scanner.next() : "Sin detalles";
                }
                PlacspLogger.sharePointError("LISTAR_DRIVES", siteId, responseCode, error);
            }
            
        } catch (SocketTimeoutException e) {
            PlacspLogger.networkError(url, "TIMEOUT", e);
            throw SharePointException.connectionError(e);
            
        } catch (SharePointException e) {
            throw e;
            
        } catch (IOException e) {
            PlacspLogger.error("Error listando drives", e);
            throw SharePointException.connectionError(e);
            
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
    
    /**
     * Obtiene el siteId de un sitio SharePoint usando Microsoft Graph
     * @throws SharePointException si hay error
     */
    public static String getSiteId(String hostname, String sitePath, String accessToken) throws SharePointException {
        String url = "https://graph.microsoft.com/v1.0/sites/" + hostname + ":" + sitePath;
        
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                String response;
                try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    response = scanner.next();
                }
                int i = response.indexOf("\"id\":\"");
                if (i != -1) {
                    i += 6;
                    int j = response.indexOf('"', i);
                    String siteId = response.substring(i, j);
                    PlacspLogger.info("SiteId obtenido: " + siteId);
                    return siteId;
                }
                throw SharePointException.siteNotFound(hostname + sitePath);
                
            } else if (responseCode == 401) {
                PlacspLogger.sharePointError("OBTENER_SITE", sitePath, responseCode, "Token expirado");
                throw SharePointException.tokenExpired();
                
            } else if (responseCode == 403) {
                PlacspLogger.sharePointError("OBTENER_SITE", sitePath, responseCode, "Acceso denegado");
                throw SharePointException.accessDenied("Site: " + sitePath);
                
            } else if (responseCode == 404) {
                PlacspLogger.sharePointError("OBTENER_SITE", sitePath, responseCode, "Sitio no encontrado");
                throw SharePointException.siteNotFound(hostname + sitePath);
                
            } else {
                String error = "";
                try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                    error = scanner.hasNext() ? scanner.next() : "Sin detalles";
                }
                PlacspLogger.sharePointError("OBTENER_SITE", sitePath, responseCode, error);
                throw SharePointException.graphApiError(responseCode, error);
            }
            
        } catch (SocketTimeoutException e) {
            PlacspLogger.networkError(url, "TIMEOUT", e);
            throw SharePointException.connectionError(e);
            
        } catch (UnknownHostException e) {
            PlacspLogger.networkError(url, "DNS_NO_RESUELTO", e);
            throw SharePointException.connectionError(e);
            
        } catch (SharePointException e) {
            throw e;
            
        } catch (IOException e) {
            PlacspLogger.error("Error obteniendo siteId", e);
            throw SharePointException.connectionError(e);
            
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Obtiene el driveId de la biblioteca de documentos principal
     * @throws SharePointException si hay error
     */
    public static String getDefaultDriveId(String siteId, String accessToken) throws SharePointException {
        String url = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives";
        
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                String response;
                try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    response = scanner.next();
                }
                int i = response.indexOf("\"id\":\"");
                if (i != -1) {
                    i += 6;
                    int j = response.indexOf('"', i);
                    String driveId = response.substring(i, j);
                    PlacspLogger.info("DriveId por defecto: " + driveId);
                    return driveId;
                }
                throw SharePointException.driveNotFound("default");
                
            } else if (responseCode == 401) {
                throw SharePointException.tokenExpired();
                
            } else if (responseCode == 403) {
                throw SharePointException.accessDenied("Default drive");
                
            } else {
                String error = "";
                try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                    error = scanner.hasNext() ? scanner.next() : "Sin detalles";
                }
                PlacspLogger.sharePointError("OBTENER_DRIVE", siteId, responseCode, error);
                throw SharePointException.graphApiError(responseCode, error);
            }
            
        } catch (SocketTimeoutException e) {
            PlacspLogger.networkError(url, "TIMEOUT", e);
            throw SharePointException.connectionError(e);
            
        } catch (SharePointException e) {
            throw e;
            
        } catch (IOException e) {
            PlacspLogger.error("Error obteniendo driveId", e);
            throw SharePointException.connectionError(e);
            
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
