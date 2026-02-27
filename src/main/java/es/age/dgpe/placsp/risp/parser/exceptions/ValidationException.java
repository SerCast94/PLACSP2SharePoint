package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepciones relacionadas con validación de archivos y datos.
 */
public class ValidationException extends PlacspException {

    public ValidationException(String message) {
        super(ErrorCategory.VALIDATION, "ERR_VALIDATION", message);
    }

    public ValidationException(String message, Throwable cause) {
        super(ErrorCategory.VALIDATION, "ERR_VALIDATION", message, cause);
    }

    public ValidationException(String errorCode, String message) {
        super(ErrorCategory.VALIDATION, errorCode, message);
    }

    public ValidationException(String errorCode, String message, Throwable cause) {
        super(ErrorCategory.VALIDATION, errorCode, message, cause);
    }

    // Constructores específicos

    public static ValidationException emptyFile(String fileName) {
        return new ValidationException(
            "ERR_EMPTY_FILE",
            "El archivo está vacío: " + fileName
        );
    }

    public static ValidationException corruptedFile(String fileName) {
        return new ValidationException(
            "ERR_CORRUPTED_FILE",
            "El archivo está corrupto: " + fileName
        );
    }

    public static ValidationException invalidFileFormat(String fileName, String expectedFormat) {
        return new ValidationException(
            "ERR_INVALID_FORMAT",
            String.format("El archivo '%s' no tiene el formato esperado (%s)", fileName, expectedFormat)
        );
    }

    public static ValidationException fileTooSmall(String fileName, long minSizeBytes) {
        return new ValidationException(
            "ERR_FILE_TOO_SMALL",
            String.format("El archivo '%s' es demasiado pequeño (mínimo %d bytes)", fileName, minSizeBytes)
        );
    }

    public static ValidationException excelValidationFailed(String fileName, String reason) {
        return new ValidationException(
            "ERR_EXCEL_INVALID",
            String.format("Validación fallida del Excel '%s': %s", fileName, reason)
        );
    }

    public static ValidationException excelEmpty(String fileName) {
        return new ValidationException(
            "ERR_EXCEL_EMPTY",
            "El archivo Excel generado no contiene datos: " + fileName
        );
    }

    public static ValidationException excelCorrupted(String fileName, Throwable cause) {
        return new ValidationException(
            "ERR_EXCEL_CORRUPTED",
            "El archivo Excel está corrupto y no se puede abrir: " + fileName,
            cause
        );
    }
}
