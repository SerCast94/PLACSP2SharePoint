#!/bin/bash

# 1. Eliminar BOM del archivo (si existe)
FILE="src/main/java/es/age/dgpe/placsp/risp/parser/cli/AtomToExcelCLI.java"
if [ -f "$FILE" ]; then
    sed -i '1s/^\xEF\xBB\xBF//' "$FILE"
    echo "BOM eliminado de $FILE (si existía)"
else
    echo "Archivo $FILE no encontrado, se omite eliminación de BOM"
fi

# 2. Limpiar y preparar directorio de salida
rm -rf target/classes
mkdir -p target/classes

# 3. Recopilar archivos .java (separados por newline)
find src/main/java -name "*.java" -print > sources.txt

# 4. Crear classpath con todos los JAR de la carpeta lib
if [ -d "lib" ] && [ "$(ls -A lib/*.jar 2>/dev/null)" ]; then
    CP=$(find lib -name "*.jar" -printf "%p:" | sed 's/:$//')
else
    CP="."
    echo "Aviso: no se encontraron JARs en lib/, se usa classpath actual (.)"
fi

# 5. Compilar
echo "Compilando con classpath: $CP"
javac -encoding UTF-8 -d target/classes -cp "$CP" @sources.txt

# 6. Verificar compilación y copiar recursos
if [ $? -eq 0 ]; then
    echo "Compilación OK"
    if [ -d "src/main/resources" ]; then
        cp -r src/main/resources/* target/classes/ 2>/dev/null
        echo "Recursos copiados"
    fi
else
    echo "Error en la compilación"
    echo "Contenido de sources.txt (primeros 10):"
    head -10 sources.txt
    exit 1
fi

# Limpiar archivo temporal
rm -f sources.txt