package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepciones relacionadas con el sistema de archivos.
 */
public class FileSystemException extends PlacspException {

    public FileSystemException(String message) {
        super(ErrorCategory.FILESYSTEM, "ERR_FILESYSTEM", message);
    }

    public FileSystemException(String message, Throwable cause) {
        super(ErrorCategory.FILESYSTEM, "ERR_FILESYSTEM", message, cause);
    }

    public FileSystemException(String errorCode, String message) {
        super(ErrorCategory.FILESYSTEM, errorCode, message);
    }

    public FileSystemException(String errorCode, String message, Throwable cause) {
        super(ErrorCategory.FILESYSTEM, errorCode, message, cause);
    }

    // Constructores específicos

    public static FileSystemException fileNotFound(String path) {
        return new FileSystemException(
            "ERR_FILE_NOT_FOUND",
            "Archivo no encontrado: " + path
        );
    }

    public static FileSystemException directoryNotFound(String path) {
        return new FileSystemException(
            "ERR_DIR_NOT_FOUND",
            "Directorio no encontrado: " + path
        );
    }

    public static FileSystemException createDirectoryFailed(String path, Throwable cause) {
        return new FileSystemException(
            "ERR_CREATE_DIR",
            "No se pudo crear el directorio: " + path,
            cause
        );
    }

    public static FileSystemException writeError(String path, Throwable cause) {
        return new FileSystemException(
            "ERR_WRITE",
            "Error al escribir archivo: " + path,
            cause
        );
    }

    public static FileSystemException readError(String path, Throwable cause) {
        return new FileSystemException(
            "ERR_READ",
            "Error al leer archivo: " + path,
            cause
        );
    }

    public static FileSystemException deleteError(String path, Throwable cause) {
        return new FileSystemException(
            "ERR_DELETE",
            "Error al eliminar archivo: " + path,
            cause
        );
    }

    public static FileSystemException diskFull(String path) {
        return new FileSystemException(
            "ERR_DISK_FULL",
            "No hay espacio en disco para escribir en: " + path
        );
    }

    public static FileSystemException permissionDenied(String path) {
        return new FileSystemException(
            "ERR_PERMISSION_DENIED",
            "Permiso denegado para acceder a: " + path
        );
    }

    public static FileSystemException pathTooLong(String path) {
        return new FileSystemException(
            "ERR_PATH_TOO_LONG",
            "La ruta excede el límite de caracteres del sistema: " + path
        );
    }
}
