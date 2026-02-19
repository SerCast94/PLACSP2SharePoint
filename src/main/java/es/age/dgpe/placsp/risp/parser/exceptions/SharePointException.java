package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepciones relacionadas con SharePoint y Microsoft Graph.
 */
public class SharePointException extends PlacspException {

    public SharePointException(String message) {
        super(ErrorCategory.SHAREPOINT, "ERR_SHAREPOINT", message);
    }

    public SharePointException(String message, Throwable cause) {
        super(ErrorCategory.SHAREPOINT, "ERR_SHAREPOINT", message, cause);
    }

    public SharePointException(String errorCode, String message) {
        super(ErrorCategory.SHAREPOINT, errorCode, message);
    }

    public SharePointException(String errorCode, String message, Throwable cause) {
        super(ErrorCategory.SHAREPOINT, errorCode, message, cause);
    }

    // Constructores específicos

    public static SharePointException authenticationFailed(Throwable cause) {
        return new SharePointException(
            "ERR_AUTH_FAILED",
            "Error de autenticación OAuth2 con Azure AD - Verifique TENANT_ID, CLIENT_ID y CLIENT_SECRET",
            cause
        );
    }

    public static SharePointException tokenExpired() {
        return new SharePointException(
            "ERR_TOKEN_EXPIRED",
            "El token OAuth2 ha expirado"
        );
    }

    public static SharePointException invalidCredentials() {
        return new SharePointException(
            "ERR_INVALID_CREDENTIALS",
            "Credenciales de SharePoint inválidas - Verifique la configuración en .env"
        );
    }

    public static SharePointException siteNotFound(String siteUrl) {
        return new SharePointException(
            "ERR_SITE_NOT_FOUND",
            "No se encontró el sitio de SharePoint: " + siteUrl + " - Verifique la URL y permisos"
        );
    }

    public static SharePointException driveNotFound(String driveName) {
        return new SharePointException(
            "ERR_DRIVE_NOT_FOUND",
            "No se encontró la biblioteca de documentos: " + driveName
        );
    }

    public static SharePointException folderNotFound(String folderPath) {
        return new SharePointException(
            "ERR_FOLDER_NOT_FOUND",
            "No se encontró la carpeta en SharePoint: " + folderPath
        );
    }

    public static SharePointException uploadFailed(String fileName, int httpStatus, String errorMsg) {
        return new SharePointException(
            "ERR_UPLOAD_HTTP_" + httpStatus,
            String.format("Error al subir '%s' a SharePoint (HTTP %d): %s", fileName, httpStatus, errorMsg)
        );
    }

    public static SharePointException fileTooLarge(String fileName, long sizeBytes) {
        return new SharePointException(
            "ERR_FILE_TOO_LARGE",
            String.format("El archivo '%s' (%.2f MB) excede el límite de tamaño de SharePoint", 
                          fileName, sizeBytes / (1024.0 * 1024.0))
        );
    }

    public static SharePointException quotaExceeded(String siteName) {
        return new SharePointException(
            "ERR_QUOTA_EXCEEDED",
            "Se ha agotado el espacio de almacenamiento en el sitio: " + siteName
        );
    }

    public static SharePointException accessDenied(String resource) {
        return new SharePointException(
            "ERR_ACCESS_DENIED",
            "Acceso denegado al recurso de SharePoint: " + resource + " - Verifique los permisos de la aplicación"
        );
    }

    public static SharePointException accessDenied(String resource, String details) {
        return new SharePointException(
            "ERR_ACCESS_DENIED",
            "Acceso denegado al recurso de SharePoint: " + resource + " - " + details
        );
    }

    public static SharePointException uploadFailed(String fileName, String message, Throwable cause) {
        return new SharePointException(
            "ERR_UPLOAD_FAILED",
            "Error al subir '" + fileName + "' a SharePoint: " + message,
            cause
        );
    }

    public static SharePointException rateLimitExceeded() {
        return new SharePointException(
            "ERR_RATE_LIMIT",
            "Se ha excedido el límite de solicitudes a Graph API. Intente de nuevo más tarde."
        );
    }

    public static SharePointException serverError(int httpCode, String errorMessage) {
        return new SharePointException(
            "ERR_SERVER_" + httpCode,
            "Error del servidor SharePoint (HTTP " + httpCode + "): " + errorMessage
        );
    }

    public static SharePointException connectionError(String host, Throwable cause) {
        return new SharePointException(
            "ERR_CONNECTION",
            "Error de conexión con " + host,
            cause
        );
    }

    public static SharePointException graphApiError(int httpStatus, String errorMessage) {
        return new SharePointException(
            "ERR_GRAPH_API_" + httpStatus,
            "Error de Microsoft Graph API (HTTP " + httpStatus + "): " + errorMessage
        );
    }

    public static SharePointException connectionError(Throwable cause) {
        return new SharePointException(
            "ERR_SP_CONNECTION",
            "Error de conexión con SharePoint/Graph API",
            cause
        );
    }
}
