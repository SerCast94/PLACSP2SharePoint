package es.age.dgpe.placsp.risp.parser.uploader;

import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class GraphSharePointUploader {
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
     * @param remotePath Ruta destino en la biblioteca (ej: "ColaboraciÃ³n/archivo.xlsx")
     * @return true si fue exitoso
     */
    public boolean uploadFile(String localFilePath, String remotePath) {
        try {
            File file = new File(localFilePath);
            if (!file.exists()) {
                System.err.println("  [ERROR] Archivo no encontrado: " + localFilePath);
                return false;
            }
            byte[] fileContent = Files.readAllBytes(file.toPath());
            // Codificar cada segmento de la ruta para caracteres especiales (tildes, etc.)
            String[] pathParts = remotePath.split("/");
            StringBuilder encodedPath = new StringBuilder();
            for (int i = 0; i < pathParts.length; i++) {
                if (i > 0) encodedPath.append("/");
                encodedPath.append(URLEncoder.encode(pathParts[i], "UTF-8").replace("+", "%20"));
            }
            String url = "https://graph.microsoft.com/v1.0/sites/" + siteId + "/drives/" + driveId + "/root:/" + encodedPath.toString() + ":/content";
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
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
                // Registrar subida exitosa en log
                double sizeMB = fileContent.length / (1024.0 * 1024.0);
                PlacspLogger.upload(file.getName(), remotePath, sizeMB, true);
                return true;
            } else {
                String error;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    error = reader.lines().reduce("", (a, b) -> a + b);
                }
                System.err.println("    [ERROR] Error al subir por Graph (codigo " + responseCode + "): " + error);
                // Registrar error en log
                PlacspLogger.upload(file.getName(), remotePath, false);
                PlacspLogger.error("Error HTTP " + responseCode + " subiendo " + file.getName() + ": " + error);
                return false;
            }
        } catch (Exception e) {
            System.err.println("  [ERROR] Error al subir archivo por Graph: " + e.getMessage());
            // Registrar excepciÃ³n en log
            PlacspLogger.upload(localFilePath, remotePath, false);
            PlacspLogger.error("ExcepciÃ³n subiendo archivo", e);
            return false;
        }
    }
}
