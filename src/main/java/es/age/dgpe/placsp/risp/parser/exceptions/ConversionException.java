package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepciones relacionadas con la conversión de archivos ATOM a Excel.
 */
public class ConversionException extends PlacspException {

    public ConversionException(String message) {
        super(ErrorCategory.CONVERSION, "ERR_CONVERSION", message);
    }

    public ConversionException(String message, Throwable cause) {
        super(ErrorCategory.CONVERSION, "ERR_CONVERSION", message, cause);
    }

    public ConversionException(String errorCode, String message) {
        super(ErrorCategory.CONVERSION, errorCode, message);
    }

    public ConversionException(String errorCode, String message, Throwable cause) {
        super(ErrorCategory.CONVERSION, errorCode, message, cause);
    }

    // Constructores específicos

    public static ConversionException invalidAtomFormat(String fileName, Throwable cause) {
        return new ConversionException(
            "ERR_INVALID_ATOM",
            "El archivo ATOM tiene un formato inválido: " + fileName,
            cause
        );
    }

    public static ConversionException emptyAtomFile(String fileName) {
        return new ConversionException(
            "ERR_EMPTY_ATOM",
            "El archivo ATOM está vacío: " + fileName
        );
    }

    public static ConversionException corruptedAtomFile(String fileName, Throwable cause) {
        return new ConversionException(
            "ERR_CORRUPTED_ATOM",
            "El archivo ATOM está corrupto: " + fileName,
            cause
        );
    }

    public static ConversionException xmlParseError(String fileName, Throwable cause) {
        return new ConversionException(
            "ERR_XML_PARSE",
            "Error al parsear XML del archivo: " + fileName,
            cause
        );
    }

    public static ConversionException excelGenerationError(String fileName, Throwable cause) {
        return new ConversionException(
            "ERR_EXCEL_GEN",
            "Error al generar archivo Excel: " + fileName,
            cause
        );
    }

    public static ConversionException excelCorrupted(String fileName) {
        return new ConversionException(
            "ERR_EXCEL_CORRUPTED",
            "El archivo Excel generado está corrupto o vacío: " + fileName
        );
    }

    public static ConversionException cliProcessError(int exitCode) {
        return new ConversionException(
            "ERR_CLI_" + exitCode,
            "El proceso CLI terminó con código de error: " + exitCode
        );
    }

    public static ConversionException cliTimeout() {
        return new ConversionException(
            "ERR_CLI_TIMEOUT",
            "El proceso de conversión excedió el tiempo límite"
        );
    }

    public static ConversionException noAtomFilesFound(String directory) {
        return new ConversionException(
            "ERR_NO_ATOMS",
            "No se encontraron archivos ATOM en el directorio: " + directory
        );
    }
}
