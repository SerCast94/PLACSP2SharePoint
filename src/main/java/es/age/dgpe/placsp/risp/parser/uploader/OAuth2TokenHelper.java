package es.age.dgpe.placsp.risp.parser.uploader;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class OAuth2TokenHelper {
    /**
     * Obtiene un token OAuth2 para SharePoint usando client credentials.
     * @param tenantId ID del tenant de Azure AD
     * @param clientId Client ID de la app registrada
     * @param clientSecret Secreto de la app
     * @return access_token o null si falla
     */
    public static String getAccessToken(String tenantId, String clientId, String clientSecret) throws IOException {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        // Usar el scope de Microsoft Graph para obtener un token vÃ¡lido para Graph API
        String data = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
            "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", "UTF-8") +
            "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") +
            "&grant_type=client_credentials";

        HttpURLConnection conn = (HttpURLConnection) URI.create(tokenUrl).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
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
                return token;
            }
        } else {
            try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                String error = scanner.next();
                System.err.println("Error obteniendo token: " + error);
            }
        }
        return null;
    }
}
