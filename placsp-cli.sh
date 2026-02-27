#!/bin/sh
# Script CLI para conversión ATOM/ZIP a Excel (Linux)
# Equivalente a placsp-cli.bat para entornos Unix/Docker

# Obtener directorio del script
DIR="$(cd "$(dirname "$0")" && pwd)"

# Buscar Java: 1) JAVA_HOME, 2) jdk/ local, 3) java en PATH
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif [ -x "$DIR/jdk/bin/java" ]; then
    JAVA_CMD="$DIR/jdk/bin/java"
else
    JAVA_CMD="java"
fi

# Ejecutar el CLI de conversión
exec "$JAVA_CMD" $JAVA_OPTS -Dfile.encoding=UTF-8 -cp "$DIR/target/classes:$DIR/lib/*" \
    es.age.dgpe.placsp.risp.parser.cli.AtomToExcelCLI "$@"
