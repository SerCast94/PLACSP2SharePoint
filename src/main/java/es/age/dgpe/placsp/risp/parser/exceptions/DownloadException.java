package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepciones relacionadas con la descarga de archivos.
 */
public class DownloadException extends PlacspException {

    public DownloadException(String message) {
        super(ErrorCategory.DOWNLOAD, "ERR_DOWNLOAD", message);
    }

    public DownloadException(String message, Throwable cause) {
        super(ErrorCategory.DOWNLOAD, "ERR_DOWNLOAD", message, cause);
    }

    public DownloadException(String errorCode, String message) {
        super(ErrorCategory.DOWNLOAD, errorCode, message);
    }

    public DownloadException(String errorCode, String message, Throwable cause) {
        super(ErrorCategory.DOWNLOAD, errorCode, message, cause);
    }

    // Constructores específicos

    public static DownloadException noLinksFound(String url) {
        return new DownloadException(
            "ERR_NO_LINKS",
            "No se encontraron enlaces de descarga en la página: " + url
        );
    }

    public static DownloadException httpError(String url, int httpStatus) {
        return new DownloadException(
            "ERR_HTTP_" + httpStatus,
            "Error HTTP " + httpStatus + " al descargar: " + url
        );
    }

    public static DownloadException incompleteDownload(String fileName, long expected, long actual) {
        return new DownloadException(
            "ERR_INCOMPLETE",
            String.format("Descarga incompleta de '%s': se esperaban %d bytes pero se recibieron %d", 
                          fileName, expected, actual)
        );
    }

    public static DownloadException emptyFile(String fileName) {
        return new DownloadException(
            "ERR_EMPTY_FILE",
            "El archivo descargado está vacío: " + fileName
        );
    }

    public static DownloadException writeError(String fileName, Throwable cause) {
        return new DownloadException(
            "ERR_WRITE",
            "Error al escribir el archivo descargado: " + fileName,
            cause
        );
    }

    public static DownloadException connectionInterrupted(String url, Throwable cause) {
        return new DownloadException(
            "ERR_INTERRUPTED",
            "La descarga fue interrumpida: " + url,
            cause
        );
    }
}
