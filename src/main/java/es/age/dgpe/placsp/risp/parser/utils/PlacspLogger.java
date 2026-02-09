package es.age.dgpe.placsp.risp.parser.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Sistema de logging para OpenPLACSP.
 * 
 * CaracterÃ­sticas:
 * - Un Ãºnico archivo de log (placsp.log)
 * - Conserva automÃ¡ticamente solo las lÃ­neas de los Ãºltimos N dÃ­as (configurable)
 * - Registro de descargas, subidas y errores
 * - Formato estructurado con timestamp
 * 
 * ParÃ¡metros configurables desde .env:
 * - LOG_DIR: Directorio de logs
 * - LOG_FILE: Nombre del archivo de log
 * - MAX_LOG_DAYS: DÃ­as mÃ¡ximos de antigÃ¼edad
 * 
 * Uso:
 *   PlacspLogger.info("Mensaje informativo");
 *   PlacspLogger.download("archivo.zip", "https://url.com", true);
 *   PlacspLogger.upload("archivo.xlsx", "SharePoint/ruta", true);
 *   PlacspLogger.error("DescripciÃ³n del error", excepcion);
 */
public class PlacspLogger {

    // ConfiguraciÃ³n cargada desde EnvConfig
    private static String LOG_DIR;
    private static String LOG_FILE;
    private static int MAX_LOG_DAYS;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Niveles de log
    public enum Level {
        INFO("INFO"),
        DOWNLOAD("DESCARGA"),
        UPLOAD("SUBIDA"),
        WARNING("AVISO"),
        ERROR("ERROR");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // Instancia singleton
    private static PlacspLogger instance;
    private PrintWriter writer;
    private Path logPath;

    /**
     * Constructor privado (singleton).
     */
    private PlacspLogger() {
        // Cargar configuraciÃ³n desde EnvConfig
        LOG_DIR = EnvConfig.getLogDir();
        LOG_FILE = EnvConfig.getLogFile();
        MAX_LOG_DAYS = EnvConfig.getMaxLogDays();
        initializeLogger();
    }

    /**
     * Obtiene la instancia del logger.
     */
    private static synchronized PlacspLogger getInstance() {
        if (instance == null) {
            instance = new PlacspLogger();
        }
        return instance;
    }

    /**
     * Inicializa el logger y limpia lÃ­neas antiguas.
     */
    private void initializeLogger() {
        try {
            // Crear directorio de logs si no existe
            Files.createDirectories(Paths.get(LOG_DIR));
            logPath = Paths.get(LOG_DIR, LOG_FILE);

            // Limpiar lÃ­neas antiguas (mÃ¡s de N dÃ­as segÃºn configuraciÃ³n)
            cleanOldLines();

            // Abrir archivo de log en modo append
            openLog();

        } catch (IOException e) {
            System.err.println("Error inicializando logger: " + e.getMessage());
        }
    }

    /**
     * Abre el archivo de log.
     */
    private void openLog() throws IOException {
        // Cerrar writer anterior si existe
        closeWriter();

        // Abrir en modo append con UTF-8
        writer = new PrintWriter(new BufferedWriter(
            new OutputStreamWriter(
                new FileOutputStream(logPath.toFile(), true),
                StandardCharsets.UTF_8
            )
        ));
    }

    /**
     * Elimina lÃ­neas del log con mÃ¡s de 30 dÃ­as.
     * Lee el archivo, filtra las lÃ­neas recientes y reescribe.
     */
    private void cleanOldLines() {
        if (!Files.exists(logPath)) {
            return;
        }

        try {
            List<String> allLines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            if (allLines.isEmpty()) {
                return;
            }

            LocalDate cutoffDate = LocalDate.now().minusDays(MAX_LOG_DAYS);
            List<String> recentLines = new ArrayList<>();
            int removedCount = 0;

            for (String line : allLines) {
                // Intentar extraer la fecha de la lÃ­nea [YYYY-MM-DD HH:mm:ss]
                if (line.startsWith("[") && line.length() >= 11) {
                    try {
                        String dateStr = line.substring(1, 11);
                        LocalDate lineDate = LocalDate.parse(dateStr, DATE_FORMAT);
                        
                        if (lineDate.isBefore(cutoffDate)) {
                            removedCount++;
                            continue; // Saltar lÃ­nea antigua
                        }
                    } catch (Exception e) {
                        // Si no se puede parsear la fecha, conservar la lÃ­nea
                    }
                }
                recentLines.add(line);
            }

            // Si se eliminaron lÃ­neas, reescribir el archivo
            if (removedCount > 0) {
                Files.write(logPath, recentLines, StandardCharsets.UTF_8);
                System.out.println("[Logger] Limpieza: " + removedCount + " lÃ­neas antiguas eliminadas");
            }

        } catch (IOException e) {
            System.err.println("[Logger] Error limpiando lÃ­neas antiguas: " + e.getMessage());
        }
    }

    /**
     * Cierra el writer actual.
     */
    private void closeWriter() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }

    /**
     * Escribe una entrada en el log.
     */
    private synchronized void writeLog(Level level, String message) {
        try {
            if (writer == null) {
                openLog();
            }

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String logEntry = String.format("[%s] [%s] %s", timestamp, level.getLabel(), message);

            writer.println(logEntry);
            writer.flush();

        } catch (IOException e) {
            System.err.println("Error escribiendo log: " + e.getMessage());
        }
    }

    // ========== METODOS ESTATICOS PUBLICOS ==========

    /**
     * Registra un mensaje informativo.
     */
    public static void info(String message) {
        getInstance().writeLog(Level.INFO, message);
    }

    /**
     * Registra una descarga.
     */
    public static void download(String fileName, String url, boolean success) {
        download(fileName, url, 0, success);
    }

    /**
     * Registra una descarga con tamaÃ±o en MB.
     */
    public static void download(String fileName, String url, double sizeMB, boolean success) {
        String status = success ? "OK" : "FALLIDO";
        String sizeInfo = sizeMB > 0 ? String.format(" (%.2f MB)", sizeMB) : "";
        String message = String.format("[%s] Archivo: %s%s | Origen: %s", status, fileName, sizeInfo, url);
        getInstance().writeLog(Level.DOWNLOAD, message);
    }

    /**
     * Registra una subida.
     */
    public static void upload(String fileName, String destination, boolean success) {
        upload(fileName, destination, 0, success);
    }

    /**
     * Registra una subida con tamaÃ±o en MB.
     */
    public static void upload(String fileName, String destination, double sizeMB, boolean success) {
        String status = success ? "OK" : "FALLIDO";
        String sizeInfo = sizeMB > 0 ? String.format(" (%.2f MB)", sizeMB) : "";
        String message = String.format("[%s] Archivo: %s%s | Destino: %s", status, fileName, sizeInfo, destination);
        getInstance().writeLog(Level.UPLOAD, message);
    }

    /**
     * Registra un error.
     */
    public static void error(String message) {
        getInstance().writeLog(Level.ERROR, message);
    }

    /**
     * Registra un error con excepciÃ³n.
     */
    public static void error(String message, Throwable throwable) {
        String fullMessage = message;
        if (throwable != null) {
            fullMessage += " | ExcepciÃ³n: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }
        getInstance().writeLog(Level.ERROR, fullMessage);
    }

    /**
     * Registra un aviso.
     */
    public static void warning(String message) {
        getInstance().writeLog(Level.WARNING, message);
    }

    /**
     * Registra el inicio de una sesion de trabajo.
     */
    public static void startSession() {
        getInstance().writeLog(Level.INFO, "========== INICIO DE SESION ==========");
    }

    /**
     * Registra el inicio de una sesion con informacion adicional.
     */
    public static void startSession(int urlCount) {
        startSession();
        info("URLs a procesar: " + urlCount);
    }

    /**
     * Registra el fin de una sesion de trabajo.
     */
    public static void endSession(long durationSeconds) {
        getInstance().writeLog(Level.INFO, 
            String.format("========== FIN DE SESION (Duracion: %d segundos) ==========", durationSeconds));
    }

    /**
     * Cierra el logger y libera recursos.
     */
    public static void close() {
        if (instance != null) {
            instance.closeWriter();
            instance = null;
        }
    }

    /**
     * Obtiene la ruta del archivo de log actual.
     */
    public static Path getLogPath() {
        return getInstance().logPath;
    }
}
