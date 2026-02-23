package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepción base para todos los errores de PLACSP.
 * Permite categorizar y registrar errores de forma estructurada.
 */
public class PlacspException extends Exception {

    private final ErrorCategory category;
    private final String errorCode;

    /**
     * Categorías de error para clasificación.
     */
    public enum ErrorCategory {
        NETWORK("RED"),           // Errores de red (timeout, conexión, etc.)
        DOWNLOAD("DESCARGA"),     // Errores al descargar archivos
        DECOMPRESSION("DESCOMPRESION"), // Errores al descomprimir
        CONVERSION("CONVERSION"), // Errores al convertir ATOM a Excel
        VALIDATION("VALIDACION"), // Errores de validación (archivo vacío, corrupto)
        SHAREPOINT("SHAREPOINT"), // Errores de SharePoint
        AUTHENTICATION("AUTENTICACION"), // Errores de autenticación OAuth
        MEMORY("MEMORIA"),        // Errores de memoria
        FILESYSTEM("FILESYSTEM"), // Errores de sistema de archivos
        CONFIGURATION("CONFIGURACION"), // Errores de configuración
        UNKNOWN("DESCONOCIDO");   // Errores no categorizados

        private final String label;

        ErrorCategory(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public PlacspException(String message) {
        super(message);
        this.category = ErrorCategory.UNKNOWN;
        this.errorCode = "ERR_UNKNOWN";
    }

    public PlacspException(String message, Throwable cause) {
        super(message, cause);
        this.category = ErrorCategory.UNKNOWN;
        this.errorCode = "ERR_UNKNOWN";
    }

    public PlacspException(ErrorCategory category, String errorCode, String message) {
        super(message);
        this.category = category;
        this.errorCode = errorCode;
    }

    public PlacspException(ErrorCategory category, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.errorCode = errorCode;
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Obtiene un mensaje formateado para logging.
     */
    public String getFormattedMessage() {
        return String.format("[%s] [%s] %s", category.getLabel(), errorCode, getMessage());
    }

    @Override
    public String toString() {
        String causeMsg = getCause() != null ? " | Causa: " + getCause().getMessage() : "";
        return getFormattedMessage() + causeMsg;
    }
}
