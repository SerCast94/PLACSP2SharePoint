#!/bin/sh
set -e

# Añadir /app al PATH para que placsp-cli.sh siempre sea encontrado
export PATH="/app:$PATH"

# Función para ejecutar el proceso
run_workflow() {
    echo "=========================================="
    echo "Iniciando proceso PLACSP - $(date)"
    echo "=========================================="
    # Verificar que existe el archivo .env
    if [ ! -f /app/.env ]; then
        echo "[ERROR] No existe archivo .env"
        echo "Monte un archivo .env o use variables de entorno"
        exit 1
    fi
    java $JAVA_OPTS -Dfile.encoding=UTF-8 -cp "target/classes:lib/*" \
        es.age.dgpe.placsp.risp.parser.workflow.PlacspWorkflow
    EXIT_CODE=$?
    echo ""
    echo "=========================================="
    echo "Proceso finalizado - $(date)"
    echo "Código de salida: $EXIT_CODE"
    echo "=========================================="
    return $EXIT_CODE
}

# Función para ejecutar con cron
run_cron() {
    CRON_SCHEDULE="${CRON_SCHEDULE:-0 2 * * *}"
    echo "Configurando ejecución periódica: $CRON_SCHEDULE"

    # Eliminar cualquier archivo sobrante en /etc/cron.d/
    rm -f /etc/cron.d/placsp-cron

    # Limpiar crontab de root por completo
    crontab -r 2>/dev/null || true

    # Establecer la nueva tarea (asegurar salto de línea final)
    (echo "$CRON_SCHEDULE cd /app && /usr/local/bin/docker-entrypoint.sh run >> /var/log/cron.log 2>&1") | crontab -

    echo "Cron configurado. Iniciando servicio..."

    # Asegurar que no hay otro crond en ejecución (por si acaso)
    killall crond 2>/dev/null || true

    # Iniciar demonio cron en background
        touch /var/log/cron.log
        crond
    # Asegurar que el archivo existe
    touch /var/log/cron.log
    # Mostrar la salida de cron en consola (espera aunque esté vacío)
        tail -F /var/log/cron.log
}

# Punto de entrada
case "$1" in
    run)
        run_workflow
        ;;
    cron)
        run_cron
        ;;
    bash)
        exec /bin/bash
        ;;
    *)
        echo "Uso: docker-entrypoint.sh {run|cron|bash}"
        echo "  run   - Ejecuta el proceso una vez"
        echo "  cron  - Ejecuta el proceso periódicamente"
        echo "  bash  - Abre shell interactivo"
        exit 1
        ;;
esac
