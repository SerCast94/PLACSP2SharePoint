package es.age.dgpe.placsp.risp.parser.exceptions;

/**
 * Excepciones relacionadas con problemas de memoria.
 */
public class MemoryException extends PlacspException {

    public MemoryException(String message) {
        super(ErrorCategory.MEMORY, "ERR_MEMORY", message);
    }

    public MemoryException(String message, Throwable cause) {
        super(ErrorCategory.MEMORY, "ERR_MEMORY", message, cause);
    }

    public MemoryException(String errorCode, String message) {
        super(ErrorCategory.MEMORY, errorCode, message);
    }

    public MemoryException(String errorCode, String message, Throwable cause) {
        super(ErrorCategory.MEMORY, errorCode, message, cause);
    }

    // Constructores específicos

    public static MemoryException outOfMemory(String operation, Throwable cause) {
        return new MemoryException(
            "ERR_OUT_OF_MEMORY",
            "Memoria insuficiente durante: " + operation + " - Considere aumentar el heap de Java (-Xmx)",
            cause
        );
    }

    public static MemoryException heapSpaceExhausted(long usedMB, long maxMB) {
        return new MemoryException(
            "ERR_HEAP_EXHAUSTED",
            String.format("Espacio de heap agotado (%d MB usados de %d MB máximo)", usedMB, maxMB)
        );
    }

    public static MemoryException largeFileProcessing(String fileName, long sizeMB) {
        return new MemoryException(
            "ERR_LARGE_FILE",
            String.format("El archivo '%s' es muy grande (%d MB) para procesar en memoria", fileName, sizeMB)
        );
    }

    public static MemoryException excelMemoryError(Throwable cause) {
        return new MemoryException(
            "ERR_EXCEL_MEMORY",
            "Error de memoria al procesar archivo Excel - El archivo puede ser muy grande",
            cause
        );
    }
}
