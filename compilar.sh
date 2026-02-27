#!/bin/bash

# 1. Eliminar BOM del archivo (si existe)
FILE="src/main/java/es/age/dgpe/placsp/risp/parser/cli/AtomToExcelCLI.java"
if [ -f "$FILE" ]; then
    # Eliminar BOM (bytes EF BB BF) si está presente
    sed -i '1s/^\xEF\xBB\xBF//' "$FILE"
    echo "BOM eliminado de $FILE (si existía)"
else
    echo "Archivo $FILE no encontrado, se omite eliminación de BOM"
fi

# 2. Limpiar y preparar directorio de salida
rm -rf target/classes
mkdir -p target/classes

# 3. Recopilar archivos .java (manejo de espacios en nombres)
find src/main/java -name "*.java" -print0 > sources.txt

# 4. Crear classpath con todos los JAR de la carpeta lib
#    (asumiendo que lib está en la raíz del proyecto)
if [ -d "lib" ]; then
    CP=$(find lib -name "*.jar" -printf "%p:" | sed 's/:$//')
else
    CP="."
fi

# 5. Compilar
javac -encoding UTF-8 -d target/classes -cp "$CP" @sources.txt

# 6. Verificar compilación y copiar recursos
if [ $? -eq 0 ]; then
    echo "Compilación OK"
    # Copiar recursos (si existen)
    if [ -d "src/main/resources" ]; then
        cp -r src/main/resources/* target/classes/ 2>/dev/null
        echo "Recursos copiados"
    fi
else
    echo "Error en la compilación"
    exit 1
fi

# Limpiar archivo temporal
rm -f sources.txt