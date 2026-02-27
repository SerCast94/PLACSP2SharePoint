package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepciones relacionadas con la descompresión de archivos ZIP.
 */
public class DecompressionException extends PlacspException {

    public DecompressionException(String message) {
        super(ErrorCategory.DECOMPRESSION, "ERR_DECOMPRESS", message);
    }

    public DecompressionException(String message, Throwable cause) {
        super(ErrorCategory.DECOMPRESSION, "ERR_DECOMPRESS", message, cause);
    }

    public DecompressionException(String errorCode, String message) {
        super(ErrorCategory.DECOMPRESSION, errorCode, message);
    }

    public DecompressionException(String errorCode, String message, Throwable cause) {
        super(ErrorCategory.DECOMPRESSION, errorCode, message, cause);
    }

    // Constructores específicos

    public static DecompressionException corruptedZip(String fileName, Throwable cause) {
        return new DecompressionException(
            "ERR_CORRUPTED_ZIP",
            "El archivo ZIP está corrupto o dañado: " + fileName,
            cause
        );
    }

    public static DecompressionException invalidZipFormat(String fileName, Throwable cause) {
        return new DecompressionException(
            "ERR_INVALID_ZIP",
            "El archivo no es un ZIP válido: " + fileName,
            cause
        );
    }

    public static DecompressionException emptyZip(String fileName) {
        return new DecompressionException(
            "ERR_EMPTY_ZIP",
            "El archivo ZIP está vacío o no contiene archivos ATOM: " + fileName
        );
    }

    public static DecompressionException extractionError(String zipFile, String entryName, Throwable cause) {
        return new DecompressionException(
            "ERR_EXTRACTION",
            String.format("Error al extraer '%s' del archivo '%s'", entryName, zipFile),
            cause
        );
    }

    public static DecompressionException diskSpaceError(String directory) {
        return new DecompressionException(
            "ERR_DISK_SPACE",
            "No hay suficiente espacio en disco para extraer archivos en: " + directory
        );
    }

    public static DecompressionException permissionDenied(String path, Throwable cause) {
        return new DecompressionException(
            "ERR_PERMISSION",
            "Sin permisos para escribir en: " + path,
            cause
        );
    }
}
