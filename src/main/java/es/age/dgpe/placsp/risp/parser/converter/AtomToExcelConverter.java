package es.age.dgpe.placsp.risp.parser.converter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Clase responsable de convertir archivos ZIP/ATOM a formato Excel.
 * Utiliza el CLI de open-placsp para realizar las conversiones.
 */
public class AtomToExcelConverter {

    private static final String CLI_COMMAND = "scripts\\open-placsp-cli.bat";

    /**
     * Convierte un archivo ZIP a Excel usando el CLI.
     * 
     * @param zipFilePath Ruta del archivo ZIP a convertir
     * @param excelFilePath Ruta del archivo Excel de salida
     */
    public void convertirZipAExcel(String zipFilePath, String excelFilePath) {
        try {
            System.out.println("  Procesando ZIP...");
            
            // Llamar al CLI del proyecto pasando el archivo ZIP
            ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe",
                "/c",
                CLI_COMMAND,
                "--in", zipFilePath,
                "--out", excelFilePath,
                "--dos-tablas",
                "--sin-emp",
                "--sin-cpm"
            );
            
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Mostrar el output del proceso (filtrado)
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Filtrar líneas de log4j y mostrar solo las importantes
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
                System.err.println("  ✗ Error al convertir ZIP");
            }
            
        } catch (Exception e) {
            System.err.println("Error al convertir archivo ZIP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Convierte todos los archivos ZIP de un directorio a Excel.
     * 
     * @param zipDir Directorio con archivos ZIP
     * @param excelDir Directorio de salida para archivos Excel
     */
    public void convertirTodosZipAExcel(String zipDir, String excelDir) {
        try {
            Path dirPath = Paths.get(zipDir);
            
            // Obtener lista de archivos ZIP
            List<Path> zipFiles = Files.list(dirPath)
                .filter(path -> path.toString().toLowerCase().endsWith(".zip"))
                .sorted()
                .collect(Collectors.toList());
            
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
                
                // Aplicar reglas de nomenclatura personalizadas
                String nombreExcel = obtenerNombreExcel(nombreBase);
                
                Path excelPath = Paths.get(excelDir, nombreExcel + ".xlsx");
                
                System.out.printf("[Archivo %d/%d] Convirtiendo '%s'%n", 
                    archivoActual, zipFiles.size(), nombreArchivo);
                System.out.println("  Archivo destino: " + excelPath.getFileName());
                
                // Convertir ZIP a Excel usando el CLI
                convertirZipAExcel(zipFile.toString(), excelPath.toString());
                
                System.out.println("  ✓ Conversión completada\n");
            }
            
            System.out.println("Todos los archivos ZIP han sido convertidos.");
        } catch (IOException e) {
            System.err.println("Error al buscar archivos ZIP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obtiene el nombre del archivo Excel según reglas de nomenclatura personalizadas.
     * 
     * @param nombreBase Nombre base del archivo ZIP (sin extensión)
     * @return Nombre personalizado para el archivo Excel
     */
    private String obtenerNombreExcel(String nombreBase) {
        // Regla 1: licitacionesPerfilesContratanteCompleto3 → licPerfContratPLACSP
        if (nombreBase.startsWith("licitacionesPerfilesContratanteCompleto3")) {
            return "licPerfContratPLACSP";
        }
        
        // Regla 2: PlataformasAgregadasSinMenores → licPlatafAgregadas
        if (nombreBase.startsWith("PlataformasAgregadasSinMenores")) {
            return "licPlatafAgregadas";
        }
        
        // Si no coincide con ninguna regla, mantener el nombre original
        return nombreBase;
    }

    /**
     * Muestra un resumen de los archivos Excel generados.
     * 
     * @param excelDir Directorio con archivos Excel
     */
    public void mostrarResumen(String excelDir) {
        try {
            Path dirPath = Paths.get(excelDir);
            
            if (!Files.exists(dirPath)) {
                System.out.println("El directorio de Excel no existe.");
                return;
            }
            
            List<Path> excelFiles = Files.list(dirPath)
                .filter(path -> path.toString().toLowerCase().endsWith(".xlsx"))
                .sorted()
                .collect(Collectors.toList());
            
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
            System.out.println("Ubicación: " + excelDir);
        } catch (IOException e) {
            System.err.println("Error al mostrar resumen: " + e.getMessage());
        }
    }
}
