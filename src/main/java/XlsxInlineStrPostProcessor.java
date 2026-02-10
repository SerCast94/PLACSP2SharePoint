package es.age.dgpe.placsp.risp.parser.cli;
import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.zip.*;

public class XlsxInlineStrPostProcessor {
    /**
     * Convierte sharedStrings a inlineStr en todas las hojas de un archivo XLSX.
     * Elimina sharedStrings.xml del ZIP.
     * @param xlsxPath Ruta del archivo XLSX a modificar (se sobreescribe)
     * @throws IOException
     */
    public static void convertSharedStringsToInlineStr(String xlsxPath) throws IOException {
        Path tempFile = Files.createTempFile("inlineStrTemp", ".xlsx");
        try (ZipFile zipFile = new ZipFile(xlsxPath);
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals("xl/sharedStrings.xml")) {
                    // Omitir sharedStrings.xml
                    continue;
                }
                ZipEntry newEntry = new ZipEntry(name);
                zos.putNextEntry(newEntry);
                InputStream is = zipFile.getInputStream(entry);
                if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    // Procesar la hoja para reemplazar sharedString por inlineStr
                    String xml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String processed = convertSheetXmlToInlineStr(xml, zipFile);
                    zos.write(processed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    // Copiar tal cual
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        // Sobrescribir el archivo original
        Files.move(tempFile, Paths.get(xlsxPath), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String convertSheetXmlToInlineStr(String xml, ZipFile zipFile) throws IOException {
        // Cargar sharedStrings.xml en memoria
        ZipEntry sharedStringsEntry = zipFile.getEntry("xl/sharedStrings.xml");
        if (sharedStringsEntry == null) return xml;
        String sharedStringsXml = new String(zipFile.getInputStream(sharedStringsEntry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String[] sharedStrings = sharedStringsXml.split("<si>");
        // El primer split es cabecera, ignorar
        for (int i = 1; i < sharedStrings.length; i++) {
            String si = sharedStrings[i];
            int end = si.indexOf("</si>");
            if (end < 0) continue;
            String value = si.substring(0, end);
            // Extraer el texto entre <t>...</t>
            int t1 = value.indexOf("<t>");
            int t2 = value.indexOf("</t>");
            String text = (t1 >= 0 && t2 > t1) ? value.substring(t1+3, t2) : "";
            // Reemplazar todas las referencias a este índice
            // Buscar celdas <c t="s" ...><v>i</v></c>
            String regex = "<c ([^>]*?)t=\\\"s\\\"([^>]*)><v>" + (i-1) + "</v></c>";
            String replacement = "<c $1t=\"inlineStr\"$2><is><t>" + escapeXml(text) + "</t></is></c>";
            xml = xml.replaceAll(regex, replacement);
        }
        return xml;
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
