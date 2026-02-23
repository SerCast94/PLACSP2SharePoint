/*******************************************************************************
 * Copyright 2021 Subdireccion General de Coordinacion de la Contratacion Electronica
 * Licencia EUPL v1.2
 ******************************************************************************/
package es.age.dgpe.placsp.risp.parser.utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Clase centralizada para gestionar la configuracion desde el archivo .env
 * 
 * Uso:
 *   String valor = EnvConfig.get("MI_VARIABLE");
 *   String valorConDefault = EnvConfig.get("MI_VARIABLE", "valor_por_defecto");
 *   int numero = EnvConfig.getInt("MI_NUMERO", 10);
 *   boolean flag = EnvConfig.getBoolean("MI_FLAG", false);
 *   Pattern patron = EnvConfig.getPattern("MI_PATRON", "default.*");
 */
public class EnvConfig {

    private static Map<String, String> config = null;
    private static final String ENV_FILE = ".env";

    /**
     * Carga las variables del archivo .env con codificacion UTF-8.
     */
    private static synchronized void loadConfig() {
        if (config != null) return;
        config = new HashMap<>();
        try {
            File envFile = new File(ENV_FILE);
            if (envFile.exists()) {
                List<String> lines = Files.readAllLines(envFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int idx = line.indexOf('=');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        config.put(key, value);
                    }
                }
                System.out.println("[EnvConfig] Cargadas " + config.size() + " variables de configuracion");
            } else {
                System.out.println("[EnvConfig] Archivo .env no encontrado, usando valores por defecto");
            }
        } catch (Exception e) {
            System.err.println("[EnvConfig] Error cargando .env: " + e.getMessage());
        }
    }

    /**
     * Obtiene una variable de configuracion.
     * Busca primero en .env, luego en variables de entorno del sistema.
     * 
     * @param key Nombre de la variable
     * @return Valor de la variable o null si no existe
     */
    public static String get(String key) {
        loadConfig();
        String value = config.get(key);
        if (value != null) return value;
        return System.getenv(key);
    }

    /**
     * Obtiene una variable de configuracion con valor por defecto.
     * 
     * @param key Nombre de la variable
     * @param defaultValue Valor por defecto si no existe
     * @return Valor de la variable o el valor por defecto
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Obtiene una variable de configuracion como entero.
     * 
     * @param key Nombre de la variable
     * @param defaultValue Valor por defecto si no existe o no es un numero
     * @return Valor entero de la variable
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Obtiene una variable de configuracion como booleano.
     * 
     * @param key Nombre de la variable
     * @param defaultValue Valor por defecto si no existe
     * @return true si el valor es "true" (case insensitive)
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Obtiene una variable de configuracion como patron regex compilado.
     * 
     * @param key Nombre de la variable
     * @param defaultPattern Patron por defecto si no existe
     * @return Pattern compilado
     */
    public static Pattern getPattern(String key, String defaultPattern) {
        String value = get(key, defaultPattern);
        return Pattern.compile(value);
    }

    /**
     * Obtiene una lista de valores separados por punto y coma.
     * 
     * @param key Nombre de la variable
     * @return Array de valores
     */
    public static String[] getList(String key) {
        String value = get(key);
        if (value == null || value.isEmpty()) return new String[0];
        return value.split(";");
    }

    /**
     * Verifica si existe una variable de configuracion.
     * 
     * @param key Nombre de la variable
     * @return true si existe
     */
    public static boolean exists(String key) {
        return get(key) != null;
    }

    /**
     * Recarga la configuracion desde el archivo .env.
     * Ãštil si se modifica el archivo en tiempo de ejecucion.
     */
    public static void reload() {
        config = null;
        loadConfig();
    }

    // ========== CONSTANTES DE CONFIGURACIÃ“N CON VALORES POR DEFECTO ==========

    // URLs
    public static String getUrlContratante() {
        return get("PLACSP_URL_CONTRATANTE", 
            "https://www.hacienda.gob.es/es-es/gobiernoabierto/datos%20abiertos/paginas/licitacionescontratante.aspx");
    }

    public static String getUrlAgregacion() {
        return get("PLACSP_URL_AGREGACION", 
            "https://www.hacienda.gob.es/es-es/gobiernoabierto/datos%20abiertos/paginas/licitacionesagregacion.aspx");
    }

    public static String[] getUrls() {
        return new String[] { getUrlContratante(), getUrlAgregacion() };
    }

    // Patrones
    public static Pattern getZipLinkPattern() {
        return getPattern("ZIP_LINK_PATTERN", "_(\\d{6})\\.zip$");
    }

    public static Pattern getAnyoMesPattern() {
        return getPattern("ANYO_MES_PATTERN", "_(\\d{6})\\.");
    }

    public static Pattern getFechaCompletaPattern() {
        return getPattern("FECHA_COMPLETA_PATTERN", "_(\\d{8})_");
    }

    // Directorios
    public static String getDownloadDir() {
        return get("DOWNLOAD_DIR", "descargas");
    }

    public static String getAtomDir() {
        return get("ATOM_DIR", "descargas/atom");
    }

    public static String getExcelDir() {
        return get("EXCEL_OUTPUT_DIR", "descargas/excel");
    }

    // Configuracion de descarga
    public static int getMesesHistorico() {
        return getInt("MESES_HISTORICO", 5);
    }

    public static int getHttpConnectTimeout() {
        return getInt("HTTP_CONNECT_TIMEOUT", 30000);
    }

    public static int getHttpReadTimeout() {
        return getInt("HTTP_READ_TIMEOUT", 60000);
    }

    public static int getDownloadBufferSize() {
        return getInt("DOWNLOAD_BUFFER_SIZE", 8192);
    }

    public static int getDownloadProgressIntervalMb() {
        return getInt("DOWNLOAD_PROGRESS_INTERVAL_MB", 10);
    }

    // Configuracion CLI
    public static String getCliCommand() {
        return get("CLI_COMMAND", "placsp-cli.bat");
    }

    public static boolean isCliDosTablas() {
        return getBoolean("CLI_DOS_TABLAS", true);
    }

    public static boolean isCliIncluirEmp() {
        return getBoolean("CLI_INCLUIR_EMP", false);
    }

    public static boolean isCliIncluirCpm() {
        return getBoolean("CLI_INCLUIR_CPM", false);
    }

    // Configuracion de logging
    public static String getLogDir() {
        return get("LOG_DIR", "logs");
    }

    public static String getLogFile() {
        return get("LOG_FILE", "placsp.log");
    }

    public static int getMaxLogDays() {
        return getInt("MAX_LOG_DAYS", 30);
    }

    // SSL
    public static boolean isSslDisableValidation() {
        return getBoolean("SSL_DISABLE_VALIDATION", true);
    }

    // Nombres de archivos Excel
    public static String getExcelNamePerfContrat() {
        return get("EXCEL_NAME_PERF_CONTRAT", "licPerfContratPLACSP");
    }

    public static String getExcelNameAgregadas() {
        return get("EXCEL_NAME_AGREGADAS", "licPlatafAgregadas");
    }

    // SharePoint
    public static String[] getSharePointDriveNames() {
        return getList("SHAREPOINT_DRIVE_NAMES");
    }
}
