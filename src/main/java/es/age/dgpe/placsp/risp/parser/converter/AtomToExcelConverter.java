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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.io.FileInputStream;
import java.time.LocalDate;

import es.age.dgpe.placsp.risp.parser.exceptions.ConversionException;
import es.age.dgpe.placsp.risp.parser.exceptions.DecompressionException;
import es.age.dgpe.placsp.risp.parser.exceptions.FileSystemException;
import es.age.dgpe.placsp.risp.parser.exceptions.MemoryException;
import es.age.dgpe.placsp.risp.parser.exceptions.ValidationException;
import es.age.dgpe.placsp.risp.parser.utils.EnvConfig;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

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

    // Patrones cargados desde configuraciÃ³n
    private final Pattern anyoMesPattern;
    private final Pattern fechaCompletaPattern;
    
    // Timeout para el proceso CLI (30 minutos por defecto)
    private static final long CLI_TIMEOUT_MINUTES = 30;
    
    // Tamaño mínimo esperado para un Excel válido (1 KB)
    private static final long MIN_EXCEL_SIZE_BYTES = 1024;

    /**
     * Constructor que carga configuraciÃ³n desde .env
     */
    public AtomToExcelConverter() {
        this.anyoMesPattern = EnvConfig.getAnyoMesPattern();
        this.fechaCompletaPattern = EnvConfig.getFechaCompletaPattern();
    }

    /**
     * Construye los argumentos del CLI segÃºn la configuraciÃ³n
     */
    private List<String> buildCliArgs(String inputPath, String outputPath) {
        List<String> args = new ArrayList<>();
        args.add("cmd.exe");
        args.add("/c");
        args.add(EnvConfig.getCliCommand());
        args.add("--in");
        args.add(inputPath);
        args.add("--out");
        args.add(outputPath);
        
        if (EnvConfig.isCliDosTablas()) {
            args.add("--dos-tablas");
        }
        // Nota: La lÃ³gica aquÃ­ es invertida porque el CLI usa --sin-xxx
        // pero en el .env usamos CLI_INCLUIR_xxx para mayor claridad
        if (!EnvConfig.isCliIncluirEmp()) {
            args.add("--sin-emp");
        }
        if (!EnvConfig.isCliIncluirCpm()) {
            args.add("--sin-cpm");
        }
        
        return args;
    }

    /**
     * Convierte un archivo ZIP a Excel usando el CLI.
     * 
     * @param zipFilePath Ruta del archivo ZIP a convertir
     * @param excelFilePath Ruta del archivo Excel de salida
     * @throws ConversionException si hay error durante la conversión
     */
    public void convertirZipAExcel(String zipFilePath, String excelFilePath) throws ConversionException {
        System.out.println("  Procesando ZIP...");
        ejecutarConversion(zipFilePath, excelFilePath, "ZIP");
    }

    /**
     * Convierte un archivo ATOM a Excel usando el CLI.
     * 
     * @param atomFilePath Ruta del archivo ATOM a convertir
     * @param excelFilePath Ruta del archivo Excel de salida
     * @throws ConversionException si hay error durante la conversión
     */
    public void convertirAtomAExcel(String atomFilePath, String excelFilePath) throws ConversionException {
        System.out.println("  Procesando ATOM...");
        ejecutarConversion(atomFilePath, excelFilePath, "ATOM");
    }
    
    /**
     * Ejecuta la conversión de un archivo de entrada a Excel.
     * Maneja errores de proceso, timeout, memoria y validación del resultado.
     */
    private void ejecutarConversion(String inputPath, String outputPath, String tipo) throws ConversionException {
        Path inputFile = Paths.get(inputPath);
        Path outputFile = Paths.get(outputPath);
        
        // Validar que el archivo de entrada existe
        if (!Files.exists(inputFile)) {
            PlacspLogger.conversionError(inputPath, outputPath, "ARCHIVO_NO_ENCONTRADO", null);
            throw new ConversionException("ERR_INPUT_NOT_FOUND", "Archivo de entrada no encontrado: " + inputPath);
        }
        
        // Validar que el archivo de entrada no está vacío
        try {
            if (Files.size(inputFile) == 0) {
                PlacspLogger.conversionError(inputPath, outputPath, "ARCHIVO_VACIO", null);
                throw ConversionException.emptyAtomFile(inputPath);
            }
        } catch (IOException e) {
            PlacspLogger.conversionError(inputPath, outputPath, "ERROR_LECTURA", e);
            throw new ConversionException("Error al leer archivo de entrada: " + inputPath, e);
        }
        
        Process process = null;
        StringBuilder errorOutput = new StringBuilder();
        
        try {
            // Construir argumentos del CLI desde configuración
            List<String> args = buildCliArgs(inputPath, outputPath);
            
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            
            // Registrar memoria antes de la conversión
            PlacspLogger.info("Iniciando conversión " + tipo + ": " + inputPath + " | " + PlacspLogger.getMemoryStats());
            
            process = pb.start();
            
            // Leer output del proceso (filtrado)
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Capturar errores
                    if (line.toLowerCase().contains("error") || 
                        line.toLowerCase().contains("exception") ||
                        line.toLowerCase().contains("outofmemory")) {
                        errorOutput.append(line).append("\n");
                    }
                    
                    // Mostrar líneas relevantes
                    if (!line.contains("org.apache.logging") && 
                        !line.contains("XmlConfiguration") &&
                        !line.contains("watching for changes") &&
                        !line.contains("Starting configuration") &&
                        !line.contains("Stopping configuration")) {
                        System.out.println("    " + line);
                    }
                }
            }
            
            // Esperar con timeout
            boolean completed = process.waitFor(CLI_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            
            if (!completed) {
                process.destroyForcibly();
                PlacspLogger.conversionError(inputPath, outputPath, "TIMEOUT", null);
                throw ConversionException.cliTimeout();
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode != 0) {
                String errorMsg = errorOutput.length() > 0 ? errorOutput.toString() : "Código de salida: " + exitCode;
                PlacspLogger.conversionError(inputPath, outputPath, "CLI_ERROR_" + exitCode, null);
                
                // Detectar tipos específicos de error
                if (errorMsg.contains("OutOfMemoryError") || errorMsg.contains("heap space")) {
                    Runtime rt = Runtime.getRuntime();
                    long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                    long maxMB = rt.maxMemory() / (1024 * 1024);
                    PlacspLogger.memoryError("CONVERSION_" + tipo, usedMB, maxMB, null);
                    throw new ConversionException("ERR_MEMORY", 
                        "Error de memoria durante la conversión. Considere aumentar -Xmx. " + 
                        "Memoria usada: " + usedMB + "MB / " + maxMB + "MB");
                }
                
                throw ConversionException.cliProcessError(exitCode);
            }
            
            // Validar que se generó el archivo Excel
            if (!Files.exists(outputFile)) {
                PlacspLogger.conversionError(inputPath, outputPath, "EXCEL_NO_GENERADO", null);
                throw new ConversionException("ERR_NO_OUTPUT", "No se generó el archivo Excel: " + outputPath);
            }
            
            // Validar tamaño mínimo del Excel
            long excelSize = Files.size(outputFile);
            if (excelSize < MIN_EXCEL_SIZE_BYTES) {
                PlacspLogger.validationError(outputPath, "EXCEL_MUY_PEQUEÑO", 
                    "Tamaño: " + excelSize + " bytes (mínimo: " + MIN_EXCEL_SIZE_BYTES + ")");
                throw ConversionException.excelCorrupted(outputPath);
            }
            
            PlacspLogger.info("Conversión completada: " + outputPath + " (" + (excelSize / 1024) + " KB)");
            
        } catch (OutOfMemoryError e) {
            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long maxMB = rt.maxMemory() / (1024 * 1024);
            PlacspLogger.memoryError("CONVERSION_" + tipo, usedMB, maxMB, e);
            throw new ConversionException("ERR_OUT_OF_MEMORY", 
                "Memoria insuficiente durante la conversión de " + tipo + ". Memoria: " + usedMB + "MB/" + maxMB + "MB", e);
                
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            PlacspLogger.conversionError(inputPath, outputPath, "INTERRUMPIDO", e);
            throw new ConversionException("Conversión interrumpida", e);
            
        } catch (IOException e) {
            PlacspLogger.conversionError(inputPath, outputPath, "ERROR_IO", e);
            throw new ConversionException("Error de E/S durante la conversión: " + e.getMessage(), e);
            
        } catch (ConversionException e) {
            throw e; // Re-lanzar excepciones ya tipificadas
            
        } catch (Exception e) {
            PlacspLogger.conversionError(inputPath, outputPath, "ERROR_INESPERADO", e);
            throw new ConversionException("Error inesperado durante la conversión", e);
            
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
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
     * @throws ConversionException si hay error durante la conversión
     * @throws DecompressionException si hay error al descomprimir
     */
    public void convertirTodosZipAExcel(String zipDir, String excelDir, String atomDir, int mesesAntiguedad) 
            throws ConversionException, DecompressionException {
        try {
            Path dirPath = Paths.get(zipDir);
            Path atomPath = Paths.get(atomDir);
            
            // Crear directorio atom si no existe
            try {
                Files.createDirectories(atomPath);
            } catch (IOException e) {
                PlacspLogger.fileSystemError("CREAR_DIRECTORIO", atomDir, e);
                throw new ConversionException("Error al crear directorio de atoms: " + atomDir, e);
            }
            
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
                PlacspLogger.warning("No se encontraron archivos ZIP en: " + zipDir);
                System.out.println("No se encontraron archivos ZIP para convertir.");
                return;
            }
            
            System.out.println("Se encontraron " + zipFiles.size() + " archivos ZIP para procesar\n");
            PlacspLogger.info("Procesando " + zipFiles.size() + " archivos ZIP");
            
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
            
            // Procesar cada grupo usando nombres de Excel desde configuraciÃ³n
            if (!zipsPerfContrat.isEmpty()) {
                System.out.println("=== Procesando " + zipsPerfContrat.size() + " ZIPs de Perfiles Contratante ===\n");
                procesarGrupoZips(zipsPerfContrat, excelDir, atomDir, EnvConfig.getExcelNamePerfContrat(), mesesAntiguedad);
            }
            
            if (!zipsAgregadas.isEmpty()) {
                System.out.println("\n=== Procesando " + zipsAgregadas.size() + " ZIPs de Plataformas Agregadas ===\n");
                procesarGrupoZips(zipsAgregadas, excelDir, atomDir, EnvConfig.getExcelNameAgregadas(), mesesAntiguedad);
            }
            
            System.out.println("\nTodos los archivos ZIP han sido procesados.");
            PlacspLogger.info("Todos los ZIP procesados correctamente");
            
        } catch (IOException e) {
            PlacspLogger.fileSystemError("LISTAR_ZIPS", zipDir, e);
            throw new ConversionException("Error al buscar archivos ZIP en: " + zipDir, e);
        }
    }
    
    /**
     * Procesa un grupo de ZIPs del mismo tipo.
     * Extrae los ATOMs en orden (antiguo a nuevo) y genera UN solo Excel.
     * 
     * @param mesesAntiguedad NÃºmero de meses de antigÃ¼edad mÃ¡xima para limpiar ATOMs
     * @throws ConversionException si hay error durante la conversión
     * @throws DecompressionException si hay error al descomprimir
     */
    private void procesarGrupoZips(List<Path> zipFiles, String excelDir, String atomDir, String nombreExcel, int mesesAntiguedad) 
            throws ConversionException, DecompressionException {
        int archivoActual = 0;
        int erroresExtraccion = 0;
        
        // 1. Extraer todos los ATOMs de los ZIPs a la carpeta atom
        for (Path zipFile : zipFiles) {
            archivoActual++;
            String nombreArchivo = zipFile.getFileName().toString();
            
            System.out.printf("[ZIP %d/%d] Extrayendo '%s'%n", 
                archivoActual, zipFiles.size(), nombreArchivo);
            
            try {
                // Extraer el ATOM del ZIP a la carpeta atom
                int atomsExtraidos = extraerAtomDeZip(zipFile.toString(), atomDir);
                if (atomsExtraidos == 0) {
                    PlacspLogger.warning("ZIP sin archivos ATOM: " + nombreArchivo);
                }
            } catch (DecompressionException e) {
                erroresExtraccion++;
                System.err.println("  [ERROR] " + e.getMessage());
                // Continuar con los otros ZIPs
            }
        }
        
        if (erroresExtraccion > 0) {
            PlacspLogger.warning("Hubo " + erroresExtraccion + " errores de extracción de " + zipFiles.size() + " ZIPs");
        }
        
        // 1.5. Limpiar ATOMs antiguos (mÃ¡s de N meses)
        limpiarAtomsAntiguos(atomDir, nombreExcel, mesesAntiguedad);
        
        // 2. Obtener todos los ATOMs de la carpeta, ordenados por fecha
        List<Path> atomFiles;
        try {
            atomFiles = Files.list(Paths.get(atomDir))
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
        } catch (IOException e) {
            PlacspLogger.fileSystemError("LISTAR_ATOMS", atomDir, e);
            throw new ConversionException("Error al listar archivos ATOM en: " + atomDir, e);
        }
        
        if (atomFiles.isEmpty()) {
            PlacspLogger.warning("No hay archivos ATOM para tipo: " + nombreExcel);
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
        
        // Validar el Excel generado
        try {
            validarExcelGenerado(excelPath);
        } catch (ValidationException e) {
            PlacspLogger.error(e);
            throw new ConversionException("ERR_VALIDATION", e.getMessage(), e);
        }
        
        System.out.println("  [OK] Conversion completada\n");
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
     * La comparaciÃ³n se hace dÃ­a a dÃ­a para una limpieza precisa.
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
            System.out.println("UbicaciÃ³n: " + excelDir);
        } catch (IOException e) {
            System.err.println("Error al mostrar resumen: " + e.getMessage());
        }
    }
}
