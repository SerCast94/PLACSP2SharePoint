package es.age.dgpe.placsp.risp.parser.exceptions;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import javax.net.ssl.SSLException;

/**
 * Excepciones relacionadas con problemas de red.
 * Incluye: URL inválida, timeout, conexión rechazada, DNS no resuelto, SSL, etc.
 */
public class NetworkException extends PlacspException {

    public NetworkException(String message) {
        super(ErrorCategory.NETWORK, "ERR_NETWORK", message);
    }

    public NetworkException(String message, Throwable cause) {
        super(ErrorCategory.NETWORK, determineErrorCode(cause), message, cause);
    }

    /**
     * Determina el código de error específico según la excepción.
     */
    private static String determineErrorCode(Throwable cause) {
        if (cause instanceof SocketTimeoutException) {
            return "ERR_TIMEOUT";
        } else if (cause instanceof UnknownHostException) {
            return "ERR_DNS_NOT_RESOLVED";
        } else if (cause instanceof ConnectException) {
            return "ERR_CONNECTION_REFUSED";
        } else if (cause instanceof SSLException) {
            return "ERR_SSL";
        } else if (cause instanceof java.net.MalformedURLException) {
            return "ERR_INVALID_URL";
        } else {
            return "ERR_NETWORK";
        }
    }

    // Constructores específicos para cada tipo de error

    public static NetworkException timeout(String url, Throwable cause) {
        return new NetworkException(
            "Timeout al conectar con: " + url + " - El servidor no respondió a tiempo", 
            cause
        );
    }

    public static NetworkException dnsNotResolved(String url, Throwable cause) {
        return new NetworkException(
            "No se pudo resolver el nombre del servidor: " + url + " - Verifique la URL o la conexión a Internet", 
            cause
        );
    }

    public static NetworkException connectionRefused(String url, Throwable cause) {
        return new NetworkException(
            "Conexión rechazada por el servidor: " + url + " - El servidor puede estar caído o bloqueando conexiones", 
            cause
        );
    }

    public static NetworkException sslError(String url, Throwable cause) {
        return new NetworkException(
            "Error SSL/TLS al conectar con: " + url + " - Problema con el certificado del servidor", 
            cause
        );
    }

    public static NetworkException webDown(String url, int httpStatus) {
        return new NetworkException(
            "El servidor devolvió error HTTP " + httpStatus + " para: " + url + " - La web puede estar caída o en mantenimiento"
        );
    }

    public static NetworkException invalidUrl(String url, Throwable cause) {
        return new NetworkException(
            "URL inválida: " + url, 
            cause
        );
    }
}
