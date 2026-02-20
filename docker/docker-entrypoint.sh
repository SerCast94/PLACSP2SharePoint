#!/bin/sh
set -e

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
    # Formato cron: "0 2 * * *" = Todos los días a las 2 AM
    CRON_SCHEDULE="${CRON_SCHEDULE:-0 2 * * *}"
    
    echo "Configurando ejecución periódica: $CRON_SCHEDULE"
    
    # Crear archivo crontab
    echo "$CRON_SCHEDULE cd /app && /usr/local/bin/docker-entrypoint.sh run >> /var/log/cron.log 2>&1" > /etc/cron.d/placsp-cron
    
    # Dar permisos
    chmod 0644 /etc/cron.d/placsp-cron
    
    # Aplicar cron
    crontab /etc/cron.d/placsp-cron
    
    # Crear log
    touch /var/log/cron.log
    
    echo "Cron configurado. Iniciando servicio..."
    
    # Iniciar cron y seguir logs
    crond && tail -f /var/log/cron.log
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
