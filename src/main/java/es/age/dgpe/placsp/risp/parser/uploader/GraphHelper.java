package es.age.dgpe.placsp.risp.parser.uploader;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Scanner;

public class GraphHelper {
            /**
             * Obtiene el driveId de la biblioteca 'Documentos compartidos' por displayName
             */
            public static String getDriveIdByDisplayName(String siteId, String accessToken, String displayName) throws IOException {
        String url = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives";
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String response;
            try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                response = scanner.next();
            }
            // Buscar el displayName en el JSON
            String[] items = response.split("\\{" );
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
                        System.out.println("[DEBUG] driveId encontrado para '" + displayName + "': " + id);
                        return id;
                    }
                }
            }
        } else {
            try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                String error = scanner.next();
                System.err.println("Error buscando driveId por displayName: " + error);
            }
        }
        return null;
            }
        /**
         * Lista todos los drives (bibliotecas) de un sitio SharePoint usando Microsoft Graph
         */
        public static void listDrives(String siteId, String accessToken) throws IOException {
            String url = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives";
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // Drives listados correctamente (output silenciado)
            } else {
                try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                    String error = scanner.next();
                    System.err.println("Error listando drives: " + error);
                }
            }
        }
    /**
     * Obtiene el siteId de un sitio SharePoint usando Microsoft Graph
     */
    public static String getSiteId(String hostname, String sitePath, String accessToken) throws IOException {
        String url = "https://graph.microsoft.com/v1.0/sites/" + hostname + ":" + sitePath;
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
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
                return response.substring(i, j);
            }
        } else {
            try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                String error = scanner.next();
                System.err.println("Error obteniendo siteId: " + error);
            }
        }
        return null;
    }

    /**
     * Obtiene el driveId de la biblioteca de documentos principal
     */
    public static String getDefaultDriveId(String siteId, String accessToken) throws IOException {
        String url = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives";
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
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
                return response.substring(i, j);
            }
        } else {
            try (Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                String error = scanner.next();
                System.err.println("Error obteniendo driveId: " + error);
            }
        }
        return null;
    }
}
