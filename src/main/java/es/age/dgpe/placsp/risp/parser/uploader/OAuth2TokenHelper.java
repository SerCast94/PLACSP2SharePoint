package es.age.dgpe.placsp.risp.parser.uploader;

import es.age.dgpe.placsp.risp.parser.exceptions.SharePointException;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class OAuth2TokenHelper {
    
    private static final int CONNECT_TIMEOUT = 30000; // 30 segundos
    private static final int READ_TIMEOUT = 30000;    // 30 segundos
    
    /**
     * Obtiene un token OAuth2 para SharePoint usando client credentials.
     * @param tenantId ID del tenant de Azure AD
     * @param clientId Client ID de la app registrada
     * @param clientSecret Secreto de la app
     * @return access_token
     * @throws SharePointException si hay error de autenticación
     */
    public static String getAccessToken(String tenantId, String clientId, String clientSecret) throws SharePointException {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        
        HttpURLConnection conn = null;
        try {
            // Usar el scope de Microsoft Graph para obtener un token valido para Graph API
            String data = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", "UTF-8") +
                "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") +
                "&grant_type=client_credentials";

            conn = (HttpURLConnection) URI.create(tokenUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data.getBytes(StandardCharsets.UTF_8));
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                String response;
                try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    response = scanner.next();
                }
                int i = response.indexOf("\"access_token\":\"");
                if (i != -1) {
                    i += 16;
                    int j = response.indexOf('"', i);
                    String token = response.substring(i, j);
                    PlacspLogger.info("Token OAuth2 obtenido correctamente");
                    return token;
                } else {
                    PlacspLogger.sharePointError("OBTENER_TOKEN", tokenUrl, "Respuesta sin access_token");
                    throw SharePointException.authenticationFailed(new Exception("Respuesta sin access_token"));
                }
            } else {
                String error = "";
                try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                    error = scanner.hasNext() ? scanner.next() : "Sin detalles";
                }
                
                PlacspLogger.sharePointError("OBTENER_TOKEN", tokenUrl, responseCode, error);
                
                // Analizar tipo de error
                if (responseCode == 400) {
                    if (error.contains("invalid_client")) {
                        throw SharePointException.invalidCredentials();
                    } else if (error.contains("invalid_grant")) {
                        throw SharePointException.tokenExpired();
                    }
                } else if (responseCode == 401) {
                    throw SharePointException.invalidCredentials();
                } else if (responseCode == 403) {
                    throw SharePointException.accessDenied("OAuth2 Token");
                }
                
                throw SharePointException.graphApiError(responseCode, error);
            }
            
        } catch (SocketTimeoutException e) {
            PlacspLogger.networkError(tokenUrl, "TIMEOUT", e);
            throw SharePointException.connectionError(e);
            
        } catch (UnknownHostException e) {
            PlacspLogger.networkError(tokenUrl, "DNS_NO_RESUELTO", e);
            throw SharePointException.connectionError(e);
            
        } catch (SharePointException e) {
            throw e; // Re-lanzar excepciones ya tipificadas
            
        } catch (IOException e) {
            PlacspLogger.error("Error de conexión al obtener token OAuth2", e);
            throw SharePointException.connectionError(e);
            
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
