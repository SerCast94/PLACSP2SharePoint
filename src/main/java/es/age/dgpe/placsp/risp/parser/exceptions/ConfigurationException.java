package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepciones relacionadas con errores de configuración.
 */
public class ConfigurationException extends PlacspException {

    public ConfigurationException(String message) {
        super(ErrorCategory.CONFIGURATION, "ERR_CONFIG", message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(ErrorCategory.CONFIGURATION, "ERR_CONFIG", message, cause);
    }

    public ConfigurationException(String errorCode, String message) {
        super(ErrorCategory.CONFIGURATION, errorCode, message);
    }

    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(ErrorCategory.CONFIGURATION, errorCode, message, cause);
    }

    // Constructores específicos

    public static ConfigurationException envFileNotFound() {
        return new ConfigurationException(
            "ERR_ENV_NOT_FOUND",
            "No se encontró el archivo .env - Copie .env.example a .env y configure sus credenciales"
        );
    }

    public static ConfigurationException missingRequiredProperty(String propertyName) {
        return new ConfigurationException(
            "ERR_MISSING_PROPERTY",
            "Falta la propiedad requerida en .env: " + propertyName
        );
    }

    public static ConfigurationException invalidPropertyValue(String propertyName, String value, String expectedFormat) {
        return new ConfigurationException(
            "ERR_INVALID_VALUE",
            String.format("Valor inválido para '%s': '%s' (esperado: %s)", propertyName, value, expectedFormat)
        );
    }

    public static ConfigurationException invalidUrl(String propertyName, String url) {
        return new ConfigurationException(
            "ERR_INVALID_URL",
            String.format("URL inválida en '%s': %s", propertyName, url)
        );
    }

    public static ConfigurationException sharePointNotConfigured() {
        return new ConfigurationException(
            "ERR_SP_NOT_CONFIGURED",
            "SharePoint no está configurado - Configure SHAREPOINT_TENANT_ID, CLIENT_ID, CLIENT_SECRET y URL en .env"
        );
    }
}
