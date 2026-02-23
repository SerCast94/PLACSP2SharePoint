package es.age.dgpe.placsp.risp.parser.converter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.FileInputStream;
import java.time.LocalDate;

import es.age.dgpe.placsp.risp.parser.utils.EnvConfig;

/**
 * Clase responsable de convertir archivos ZIP/ATOM a formato Excel.
 * Utiliza el CLI de PLACSP2SharePoint para realizar las conversiones.
 * 
 * ParÃ¡metros configurables desde .env:
 * - CLI_COMMAND: Comando del CLI a ejecutar
 * - CLI_DOS_TABLAS, CLI_INCLUIR_EMP, CLI_INCLUIR_CPM: Opciones del CLI
 * - ANYO_MES_PATTERN, FECHA_COMPLETA_PATTERN: Patrones de fechas
 * - EXCEL_NAME_PERF_CONTRAT, EXCEL_NAME_AGREGADAS: Nombres de archivos Excel
 */
public class AtomToExcelConverter {

    // Patrones cargados desde configuracion
    private final Pattern anyoMesPattern;
    private final Pattern fechaCompletaPattern;

    /**
     * Constructor que carga configuracion desde .env
     */
    public AtomToExcelConverter() {
        this.anyoMesPattern = EnvConfig.getAnyoMesPattern();
        this.fechaCompletaPattern = EnvConfig.getFechaCompletaPattern();
    }

    /**
     * Construye los argumentos del CLI segÃºn la configuracion
     */
    private List<String> buildCliArgs(String inputPath, String outputPath) {
        List<String> args = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        // Construir las opciones del CLI
        StringBuilder options = new StringBuilder();
        options.append("--in '").append(inputPath).append("' ");
        options.append("--out '").append(outputPath).append("'");

        if (EnvConfig.isCliDosTablas()) {
            options.append(" --dos-tablas");
        }
        if (!EnvConfig.isCliIncluirEmp()) {
            options.append(" --sin-emp");
        }
        if (!EnvConfig.isCliIncluirCpm()) {
            options.append(" --sin-cpm");
        }

        if (os.contains("win")) {
            args.add("cmd.exe");
            args.add("/c");
            args.add(EnvConfig.getCliCommand() + " " + options.toString().replace("'", "\""));
        } else {
            args.add("sh");
            args.add("-c");
            args.add("./" + EnvConfig.getCliCommand() + " " + options.toString());
        }

        return args;
    }

    /**
     * Convierte un archivo ZIP a Excel usando el CLI.
     * 
     * @param zipFilePath Ruta del archivo ZIP a convertir
     * @param excelFilePath Ruta del archivo Excel de salida
     */
    public void convertirZipAExcel(String zipFilePath, String excelFilePath) {
        try {
            System.out.println("  Procesando ZIP...");
            
            // Construir argumentos del CLI desde configuracion
            List<String> args = buildCliArgs(zipFilePath, excelFilePath);
            
            ProcessBuilder pb = new ProcessBuilder(args);
            
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Mostrar el output del proceso (filtrado)
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
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

    /**
     * Convierte un archivo ATOM a Excel usando el CLI.
     * 
     * @param atomFilePath Ruta del archivo ATOM a convertir
     * @param excelFilePath Ruta del archivo Excel de salida
     */
    public void convertirAtomAExcel(String atomFilePath, String excelFilePath) {
        try {
            System.out.println("  Procesando ATOM...");
            
            // Construir argumentos del CLI desde configuracion
            List<String> args = buildCliArgs(atomFilePath, excelFilePath);
            
            ProcessBuilder pb = new ProcessBuilder(args);
            
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Mostrar el output del proceso (filtrado)
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
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
                System.err.println("  [ERROR] Error al convertir ATOM");
            }
            
        } catch (Exception e) {
            System.err.println("Error al convertir archivo ATOM: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Convierte todos los archivos ZIP de un directorio a Excel.
     * Si hay mÃºltiples ZIPs del mismo tipo, los descomprime en orden (antiguo a nuevo)
     * y genera UN solo Excel por tipo, usando todos los ATOMs acumulados.
     * 
     * @param zipDir Directorio con archivos ZIP
     * @param excelDir Directorio de salida para archivos Excel
     * @param atomDir Directorio donde guardar los archivos ATOM extraÃ­dos
     * @param mesesAntiguedad NÃºmero de meses de antigÃ¼edad mÃ¡xima para los archivos ATOM
     */
    public void convertirTodosZipAExcel(String zipDir, String excelDir, String atomDir, int mesesAntiguedad) {
        try {
            Path dirPath = Paths.get(zipDir);
            Path atomPath = Paths.get(atomDir);
            
            // Crear directorio atom si no existe
            Files.createDirectories(atomPath);
            
            // Obtener lista de archivos ZIP
            List<Path> zipFiles = Files.list(dirPath)
                .filter(path -> path.toString().toLowerCase().endsWith(".zip"))
                .sorted((a, b) -> {
                    // Ordenar por fecha (YYYYMM) de mÃ¡s antiguo a mÃ¡s reciente
                    int fechaA = extraerFecha(a.getFileName().toString());
                    int fechaB = extraerFecha(b.getFileName().toString());
                    return Integer.compare(fechaA, fechaB);
                })
                .collect(Collectors.toList());
            
            if (zipFiles.isEmpty()) {
                System.out.println("No se encontraron archivos ZIP para convertir.");
                return;
            }
            
            System.out.println("Se encontraron " + zipFiles.size() + " archivos ZIP para procesar\n");
            
            // Agrupar ZIPs por tipo (licPerfContrat o licPlatafAgregadas)
            List<Path> zipsPerfContrat = new ArrayList<>();
            List<Path> zipsAgregadas = new ArrayList<>();
            
            for (Path zipFile : zipFiles) {
                String nombre = zipFile.getFileName().toString();
                if (nombre.contains("PerfilesContratante")) {
                    zipsPerfContrat.add(zipFile);
                } else if (nombre.contains("PlataformasAgregadas")) {
                    zipsAgregadas.add(zipFile);
                }
            }
            
            // Procesar cada grupo usando nombres de Excel desde configuracion
            if (!zipsPerfContrat.isEmpty()) {
                System.out.println("=== Procesando " + zipsPerfContrat.size() + " ZIPs de Perfiles Contratante ===\n");
                procesarGrupoZips(zipsPerfContrat, excelDir, atomDir, EnvConfig.getExcelNamePerfContrat(), mesesAntiguedad);
            }
            
            if (!zipsAgregadas.isEmpty()) {
                System.out.println("\n=== Procesando " + zipsAgregadas.size() + " ZIPs de Plataformas Agregadas ===\n");
                procesarGrupoZips(zipsAgregadas, excelDir, atomDir, EnvConfig.getExcelNameAgregadas(), mesesAntiguedad);
            }
            
            System.out.println("\nTodos los archivos ZIP han sido procesados.");
        } catch (IOException e) {
            System.err.println("Error al buscar archivos ZIP: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Procesa un grupo de ZIPs del mismo tipo.
     * Extrae los ATOMs en orden (antiguo a nuevo) y genera UN solo Excel.
     * 
     * @param mesesAntiguedad NÃºmero de meses de antigÃ¼edad mÃ¡xima para limpiar ATOMs
     */
    private void procesarGrupoZips(List<Path> zipFiles, String excelDir, String atomDir, String nombreExcel, int mesesAntiguedad) {
        try {
            int archivoActual = 0;
            
            // 1. Extraer todos los ATOMs de los ZIPs a la carpeta atom
            for (Path zipFile : zipFiles) {
                archivoActual++;
                String nombreArchivo = zipFile.getFileName().toString();
                
                System.out.printf("[ZIP %d/%d] Extrayendo '%s'%n", 
                    archivoActual, zipFiles.size(), nombreArchivo);
                
                // Extraer el ATOM del ZIP a la carpeta atom
                extraerAtomDeZip(zipFile.toString(), atomDir);
            }
            
            // 1.5. Limpiar ATOMs antiguos (mÃ¡s de N meses)
            limpiarAtomsAntiguos(atomDir, nombreExcel, mesesAntiguedad);
            
            // 2. Obtener todos los ATOMs de la carpeta, ordenados por fecha
            List<Path> atomFiles = Files.list(Paths.get(atomDir))
                .filter(path -> path.toString().toLowerCase().endsWith(".atom"))
                .filter(path -> {
                    // Filtrar solo los del mismo tipo
                    String nombre = path.getFileName().toString();
                    if (nombreExcel.contains("PerfContrat")) {
                        return nombre.contains("PerfilesContratante");
                    } else {
                        return nombre.contains("PlataformasAgregadas");
                    }
                })
                .sorted((a, b) -> {
                    int fechaA = extraerFecha(a.getFileName().toString());
                    int fechaB = extraerFecha(b.getFileName().toString());
                    return Integer.compare(fechaA, fechaB);
                })
                .collect(Collectors.toList());
            
            if (atomFiles.isEmpty()) {
                System.out.println("  [INFO] No hay archivos ATOM para este tipo.");
                return;
            }
            
            System.out.println("\n  Total ATOMs acumulados: " + atomFiles.size());
            for (Path atom : atomFiles) {
                System.out.println("    - " + atom.getFileName());
            }
            
            // 3. Generar UN solo Excel usando el ATOM principal (el primero/mÃ¡s antiguo)
            // Este es el archivo completo, los demÃ¡s son incrementales
            Path atomPrincipal = atomFiles.get(0);
            Path excelPath = Paths.get(excelDir, nombreExcel + ".xlsx");
            
            System.out.println("\n  Generando Excel desde: " + atomPrincipal.getFileName());
            System.out.println("  Archivo destino: " + excelPath.getFileName());
            
            convertirAtomAExcel(atomPrincipal.toString(), excelPath.toString());
            
            System.out.println("  [OK] Conversion completada\n");
            
        } catch (IOException e) {
            System.err.println("Error procesando grupo de ZIPs: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Extrae TODOS los archivos ATOM de un ZIP y los guarda en el directorio especificado.
     * @return NÃºmero de archivos ATOM extraÃ­dos
     */
    private int extraerAtomDeZip(String zipFilePath, String atomDir) {
        int contadorExtraidos = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".atom")) {
                    // Mantener el nombre original del ATOM
                    String nombreAtom = entry.getName();
                    
                    Path destino = Paths.get(atomDir, nombreAtom);
                    
                    // Copiar el contenido
                    Files.copy(zis, destino, StandardCopyOption.REPLACE_EXISTING);
                    contadorExtraidos++;
                }
                zis.closeEntry();
            }
            System.out.println("    [OK] Extraidos " + contadorExtraidos + " archivos ATOM");
        } catch (IOException e) {
            System.err.println("Error extrayendo ATOM de ZIP: " + e.getMessage());
        }
        return contadorExtraidos;
    }
    
    /**
     * Extrae la fecha (YYYYMM) de un nombre de archivo.
     */
    private int extraerFecha(String nombreArchivo) {
        Matcher matcher = anyoMesPattern.matcher(nombreArchivo);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }
    
    /**
     * Elimina los archivos ATOM que tienen mÃ¡s de N meses de antigÃ¼edad.
     * La comparacion se hace dÃ­a a dÃ­a para una limpieza precisa.
     * 
     * @param atomDir Directorio donde estÃ¡n los archivos ATOM
     * @param tipoExcel Tipo de archivo para filtrar (PerfContrat o Agregadas)
     * @param mesesAntiguedad NÃºmero de meses de antigÃ¼edad mÃ¡xima
     */
    private void limpiarAtomsAntiguos(String atomDir, String tipoExcel, int mesesAntiguedad) {
        try {
            // Calcular la fecha lÃ­mite (hace N meses exactos desde hoy)
            LocalDate fechaHoy = LocalDate.now();
            LocalDate fechaLimite = fechaHoy.minusMonths(mesesAntiguedad);
            
            // Listar archivos ATOM del tipo correspondiente
            List<Path> atomsAEliminar = Files.list(Paths.get(atomDir))
                .filter(path -> path.toString().toLowerCase().endsWith(".atom"))
                .filter(path -> {
                    String nombre = path.getFileName().toString();
                    if (tipoExcel.contains("PerfContrat")) {
                        return nombre.contains("PerfilesContratante");
                    } else {
                        return nombre.contains("PlataformasAgregadas");
                    }
                })
                .filter(path -> {
                    LocalDate fechaAtom = extraerFechaCompleta(path.getFileName().toString());
                    // Si no tiene fecha en el nombre, es el archivo principal, no eliminar
                    if (fechaAtom == null) return false;
                    // Eliminar si es anterior a la fecha lÃ­mite
                    return fechaAtom.isBefore(fechaLimite);
                })
                .collect(Collectors.toList());
            
            if (!atomsAEliminar.isEmpty()) {
                System.out.println("\n  [LIMPIEZA] Eliminando " + atomsAEliminar.size() + " ATOMs antiguos (anteriores a " + fechaLimite + "):");
                for (Path atom : atomsAEliminar) {
                    Files.delete(atom);
                    System.out.println("    - Eliminado: " + atom.getFileName());
                }
            }
        } catch (IOException e) {
            System.err.println("Error limpiando ATOMs antiguos: " + e.getMessage());
        }
    }
    
    /**
     * Extrae la fecha completa (YYYYMMDD) de un nombre de archivo ATOM.
     * Los archivos tienen formato: nombre_YYYYMMDD_HHMMSS.atom
     * 
     * @param nombreArchivo Nombre del archivo
     * @return LocalDate con la fecha, o null si no tiene fecha
     */
    private LocalDate extraerFechaCompleta(String nombreArchivo) {
        Matcher matcher = fechaCompletaPattern.matcher(nombreArchivo);
        if (matcher.find()) {
            String fechaStr = matcher.group(1); // YYYYMMDD
            int year = Integer.parseInt(fechaStr.substring(0, 4));
            int month = Integer.parseInt(fechaStr.substring(4, 6));
            int day = Integer.parseInt(fechaStr.substring(6, 8));
            return LocalDate.of(year, month, day);
        }
        return null;
    }
    
    /**
     * Sobrecarga para mantener compatibilidad (sin directorio atom separado).
     * Usa 3 meses como valor por defecto.
     */
    public void convertirTodosZipAExcel(String zipDir, String excelDir) {
        convertirTodosZipAExcel(zipDir, excelDir, zipDir + "/atom", 3);
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
            System.out.println("Ubicacion: " + excelDir);
        } catch (IOException e) {
            System.err.println("Error al mostrar resumen: " + e.getMessage());
        }
    }
}
