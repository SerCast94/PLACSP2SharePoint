package es.age.dgpe.placsp.risp.parser.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ooxml.POIXMLProperties;
import org.purl.atompub.tombstones._1.DeletedEntryType;
import org.w3._2005.atom.EntryType;
import org.w3._2005.atom.FeedType;
import org.w3._2005.atom.LinkType;

import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.Transliterator;

import es.age.dgpe.placsp.risp.parser.model.DatosCPM;
import es.age.dgpe.placsp.risp.parser.model.DatosEMP;
import es.age.dgpe.placsp.risp.parser.model.DatosLicitacionGenerales;
import es.age.dgpe.placsp.risp.parser.model.DatosResultados;
import es.age.dgpe.placsp.risp.parser.model.SpreeadSheetManager;
import ext.place.codice.common.caclib.ContractFolderStatusType;
import ext.place.codice.common.caclib.PreliminaryMarketConsultationStatusType;

/**
 * CLI tool to convert PLACSP RISP ATOM files to Excel using existing logic.
 * Soporta multiples archivos ATOM de entrada que se combinan en un unico Excel.
 *
 * Usage:
 *   --in <path.atom>       Path to an ATOM file (puede repetirse para multiples archivos)
 *   --out <path.xlsx>      Output Excel path
 *   --dos-tablas           Output licitaciones + resultados in two sheets
 *   --sin-emp              Do not include EMP sheet
 *   --sin-cpm              Do not include CPM sheet
 */
public class AtomToExcelCLI {

    private static Unmarshaller atomUnMarshaller;

    // Transliterator de ICU4J para limpieza exhaustiva de texto (thread-safe, reutilizable)
    private static final Transliterator LATIN_ASCII = Transliterator.getInstance(
            "Any-Latin; Latin-ASCII; [\u0080-\uFFFF] Remove");
    private static final Normalizer2 NFC_NORMALIZER = Normalizer2.getNFCInstance();
    
    // Patron precompilado para caracteres problematicos en Power BI M
    // Incluye: controles, formato Unicode, surrogates, private use, etc.
    private static final java.util.regex.Pattern POWERBI_PROBLEMATIC = java.util.regex.Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F]" +  // Caracteres de control (excepto tab, LF, CR)
            "|[\\u00AD]" +                                      // Soft hyphen
            "|[\\u200B-\\u200F]" +                              // Zero-width y marcadores direccionales
            "|[\u2028-\u202F]" +                              // Separadores de linea/parrafo y espacios especiales
            "|[\\u2060-\\u206F]" +                              // Word joiner y caracteres de formato
            "|[\\uFEFF]" +                                       // BOM / Zero-width no-break space
            "|[\\uFFF0-\\uFFFF]" +                              // Specials (incluyendo replacement char)
            "|[\uD800-\uDFFF]" +                              // Surrogates (huerfanos causan errores)
            "|[\\uE000-\\uF8FF]" +                              // Private Use Area
            "|[\u0300-\u036F]+(?![\\p{L}])"                   // Diacriticos sueltos sin letra base
    );

    /**
     * Limpia y normaliza texto de forma exhaustiva para compatibilidad con Power BI.
     * Usa ICU4J para normalizacion Unicode y transliteracion a ASCII puro.
     * Elimina todos los caracteres problematicos conocidos para el lenguaje M de Power BI.
     * 
     * @param texto El texto a limpiar
     * @return El texto limpio y normalizado (solo ASCII imprimible), o null si el texto era null
     */
private static String limpiarSaltosDeLinea(String texto) {

    if (texto == null) return null;

    // 1. Desescapar entidades HTML comunes (ambas formas: &entity; y &amp;entity;)
    String limpio = texto
        // Forma doblemente escapada (&amp;entity;)
        .replace("&amp;#xD;", " ")
        .replace("&amp;#xA;", " ")
        .replace("&amp;#x9;", " ")
        .replace("&amp;#13;", " ")
        .replace("&amp;#10;", " ")
        .replace("&amp;#0;", "")
        .replace("&amp;quot;", "'")
        .replace("&amp;amp;", "&")
        .replace("&amp;lt;", "<")
        .replace("&amp;gt;", ">")
        .replace("&amp;apos;", "'")
        .replace("&amp;nbsp;", " ")
        // Forma simple (&entity;)
        .replace("&#xD;", " ")
        .replace("&#xA;", " ")
        .replace("&#x9;", " ")
        .replace("&#13;", " ")
        .replace("&#10;", " ")
        .replace("&#0;", "")
        .replace("&quot;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ");

    // 2. Normalización Unicode NFC
    limpio = NFC_NORMALIZER.normalize(limpio);

    // ----------------------------------------------------------------------
    // 🔥 2-B. LIMPIEZA DE ESCAPES TÍPICOS DEL PLACSP / JSON
    // ----------------------------------------------------------------------
    limpio = limpio
        .replace("\\\\", " ")  // elimina doble backslash
        .replace("\\(", "(")   // paréntesis escapados
        .replace("\\)", ")")
        .replace("\\-", "-")   // guiones escapados
        .replace("\\_", "_")   // subrayado escapado
        .replace("\\/", "/")   // barras escapadas
        .replace("\\n", " ")   // saltos escapados
        .replace("\\r", " ")
        .replace("\\t", " ")
        .replace("\\\"", "\"") // comillas escapadas
        .replace("\\'", "'");  // apostrofes escapados

    // ----------------------------------------------------------------------
    // 🔥 2-C. DECODIFICAR URLS (%2F, %3A, %26…)
    // ----------------------------------------------------------------------
    try {
        limpio = java.net.URLDecoder.decode(limpio, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception ignored) {}

    // ----------------------------------------------------------------------
    // 🔥 2-D. ELIMINAR ASCII CONTROL (0x00–0x1F) invisibles
    // ----------------------------------------------------------------------
    limpio = limpio.replaceAll("[\\x00-\\x1F]", " ");

    // 3. Sustituir saltos de línea reales por espacios
    limpio = limpio.replaceAll("\r\n|\r|\n", " ");

    // 4. Eliminar tabulaciones
    limpio = limpio.replace('\t', ' ');

    // 5. Eliminar diacríticos (acentos) si quieres texto plano
    limpio = java.text.Normalizer.normalize(limpio, java.text.Normalizer.Form.NFD);
    limpio = limpio.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

    // 6. Filtrar caracteres problemáticos según tu patrón
    limpio = POWERBI_PROBLEMATIC.matcher(limpio).replaceAll("");

    // 7. Convertir guiones "bonitos" a ASCII
    limpio = limpio
        .replace('\u2010', '-')  // hyphen
        .replace('\u2011', '-')  // non-breaking hyphen
        .replace('\u2012', '-')  // figure dash
        .replace('\u2013', '-')  // en dash
        .replace('\u2014', '-')  // em dash
        .replace('\u2015', '-')  // horizontal bar
        .replace('\u2212', '-')  // minus sign
        .replace('\u2043', '-'); // hyphen bullet

    // 8. Normalizar espacios Unicode a espacio ASCII
    limpio = limpio
        .replace('\u00A0', ' ')
        .replace('\u2002', ' ')
        .replace('\u2003', ' ')
        .replace('\u2004', ' ')
        .replace('\u2005', ' ')
        .replace('\u2006', ' ')
        .replace('\u2007', ' ')
        .replace('\u2008', ' ')
        .replace('\u2009', ' ')
        .replace('\u200A', ' ')
        .replace('\u202F', ' ')
        .replace('\u205F', ' ');

    // 9. Comillas tipográficas → ASCII
    limpio = limpio
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace('\u201A', '\'')
        .replace('\u201B', '\'')
        .replace('\u201C', '"')
        .replace('\u201D', '"')
        .replace('\u201E', '"')
        .replace('\u201F', '"')
        .replace('\u00AB', '"')
        .replace('\u00BB', '"')
        .replace('\u2039', '\'')
        .replace('\u203A', '\'');

    // 10. Puntos suspensivos y símbolos varios
    limpio = limpio
        .replace("\u2026", "...") 
        .replace('\u2022', '*')
        .replace('\u2023', '>')
        .replace('\u2219', '*')
        .replace('\u25AA', '*')
        .replace('\u25CF', '*')
        .replace('\u00B7', '*');

    // 11. Permitir solo ASCII imprimible + ñÑüÜ
    String permitidos = "ñÑüÜ";
    StringBuilder sb = new StringBuilder(limpio.length());
    for (char c : limpio.toCharArray()) {
        if ((c >= 32 && c <= 126) || permitidos.indexOf(c) >= 0)
            sb.append(c);
    }
    limpio = sb.toString();

    // 12. Normalizar espacios
    limpio = limpio.replaceAll("\\s+", " ").trim();

    // 13. Limitar longitud a 4000 (Power BI Service)
    if (limpio.length() > 4000)
        limpio = limpio.substring(0, 3997) + "...";

    return limpio;
}

    // EDITA ESTAS RUTAS PARA EJECUCIONES RAPIDAS SIN ARGUMENTOS
    // Ejemplos:
    // private static String DEFAULT_IN_PATH  = "C:\\datos\\risp\\2025-01-01.atom";
    // private static String DEFAULT_OUT_PATH = "C:\\datos\\salida\\resultado.xlsx";
    private static String DEFAULT_IN_PATH  = "";
    private static String DEFAULT_OUT_PATH = "";

    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        if (!parsed.valid) {
            System.out.println(parsed.usage());
            System.exit(parsed.exitCode);
        }

        Path tempDir = null;
        try {
            // Procesar cada archivo de entrada
            List<String> actualInPaths = new ArrayList<>();
            boolean needsTempDir = false;
            
            for (String inPath : parsed.inPaths) {
                if (inPath.toLowerCase().endsWith(".zip")) {
                    needsTempDir = true;
                    break;
                }
            }
            
            if (needsTempDir) {
                tempDir = Files.createTempDirectory("atom-extract-");
            }
            
            for (String inPath : parsed.inPaths) {
                if (inPath.toLowerCase().endsWith(".zip")) {
                    System.out.println("Detectado archivo ZIP, extrayendo: " + inPath);
                    String extractedAtom = extractAndFindAtom(inPath, tempDir);
                    System.out.println("Usando archivo extraÃ­do: " + extractedAtom);
                    actualInPaths.add(extractedAtom);
                } else {
                    actualInPaths.add(inPath);
                }
            }

            Args actualArgs = new Args(actualInPaths, parsed.outPath, parsed.dosTablas, parsed.sinEMP, parsed.sinCPM, true, 0);
            new AtomToExcelCLI().convert(actualArgs);
            System.out.println("Conversion completada: " + parsed.outPath);
        } catch (Exception e) {
            System.err.println("Error en la conversion: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        } finally {
            // Limpiar directorio temporal si se creo
            if (tempDir != null) {
                try {
                    deleteRecursively(tempDir);
                } catch (IOException e) {
                    System.err.println("Advertencia: no se pudo eliminar temp dir: " + e.getMessage());
                }
            }
        }
    }

    private static String extractAndFindAtom(String zipPath, Path tempDir) throws IOException {
        File zipFile = new File(zipPath);
        String baseName = zipFile.getName().replaceAll("\\.[zZ][iI][pP]$", "");

        // Descomprimir
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    File outFile = new File(tempDir.toFile(), entry.getName());
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        // Buscar el .atom con el mismo nombre base
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.equalsIgnoreCase(baseName + ".atom"));
        if (files != null && files.length > 0) {
            return files[0].getAbsolutePath();
        }

        // Si no se encuentra con el mismo nombre, buscar cualquier .atom
        files = tempDir.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".atom"));
        if (files != null && files.length > 0) {
            return files[0].getAbsolutePath();
        }

        throw new FileNotFoundException("No se encontro archivo .atom en el ZIP");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // ignorar
                    }
                });
        }
    }

    @SuppressWarnings("unchecked")
    private void convert(Args args) throws Exception {
        HashSet<String> entriesProcesadas = new HashSet<>();
        HashMap<String, GregorianCalendar> entriesDeleted = new HashMap<>();
        int numeroEntries = 0;
        int numeroFicherosProcesados = 0;

        FeedType res = null;
        FileOutputStream output_file = null;
        InputStreamReader inStream = null;

        ArrayList<DatosLicitacionGenerales> seleccionLicitacionGenerales = new ArrayList<>(Arrays.asList(DatosLicitacionGenerales.values()));
        ArrayList<DatosResultados> seleccionLicitacionResultados = new ArrayList<>(Arrays.asList(DatosResultados.values()));
        ArrayList<DatosEMP> seleccionEncargosMediosPropios = new ArrayList<>(Arrays.asList(DatosEMP.values()));
        ArrayList<DatosCPM> seleccionConsultasPreliminares = new ArrayList<>(Arrays.asList(DatosCPM.values()));

        if (args.sinEMP) {
            seleccionEncargosMediosPropios.clear();
        }
        if (args.sinCPM) {
            seleccionConsultasPreliminares.clear();
        }

        try {
            // JAXB
            JAXBContext jc = JAXBContext.newInstance(
                "org.w3._2005.atom:org.dgpe.codice.common.caclib:org.dgpe.codice.common.cbclib:ext.place.codice.common.caclib:ext.place.codice.common.cbclib:org.purl.atompub.tombstones._1");
            atomUnMarshaller = jc.createUnmarshaller();

            // Hojas necesarias
            SpreeadSheetManager spreeadSheetManager = new SpreeadSheetManager(args.dosTablas, seleccionEncargosMediosPropios.size()>0, seleccionConsultasPreliminares.size()>0);

            insertarTitulos(spreeadSheetManager, seleccionLicitacionGenerales, seleccionLicitacionResultados, seleccionEncargosMediosPropios, seleccionConsultasPreliminares);
            spreeadSheetManager.updateColumnsSize();

            // Procesar cada archivo ATOM de entrada
            for (String inPath : args.inPaths) {
                System.out.println("Procesando fuente ATOM: " + inPath);
                
                // ATOM inicial
                File ficheroRISP = new File(inPath);
                String directorioPath = ficheroRISP.getParent();
                boolean existeFicheroRisp = ficheroRISP.exists() && ficheroRISP.isFile();
                if (!existeFicheroRisp) {
                    System.err.println("  Advertencia: No se puede acceder al fichero: " + inPath);
                    continue;
                }

                File[] lista_ficherosRISP = ficheroRISP.getParentFile().listFiles();
                if (lista_ficherosRISP != null) {
                }

                while (existeFicheroRisp) {

                    res = null;
                    inStream = new InputStreamReader(new FileInputStream(ficheroRISP), StandardCharsets.UTF_8);
                    res = ((JAXBElement<FeedType>) atomUnMarshaller.unmarshal(inStream)).getValue();

                    // entradas borradas
                    if (res.getAny() != null) {
                        for (int indice = 0; indice < res.getAny().size(); indice++) {
                            DeletedEntryType deletedEntry = ((JAXBElement<DeletedEntryType>) res.getAny().get(indice)).getValue();
                            if (!entriesDeleted.containsKey(deletedEntry.getRef())) {
                                entriesDeleted.put(deletedEntry.getRef(), deletedEntry.getWhen().toGregorianCalendar());
                            }
                        }
                    }

                    // recorrer entries
                    numeroEntries += res.getEntry().size();
                    for (EntryType entry : res.getEntry()) {
                        if (!entriesProcesadas.contains(entry.getId().getValue())) {
                            GregorianCalendar fechaDeleted = entriesDeleted.get(entry.getId().getValue());

                            boolean isCPM = false;
                            try {
                                isCPM = ((JAXBElement<?>) entry.getAny().get(0)).getValue() instanceof PreliminaryMarketConsultationStatusType;
                            } catch (Exception e) {
                                isCPM = false;
                            }

                            if (isCPM) {
                                if(seleccionConsultasPreliminares.size()>0) {
                                    procesarCPM(entry, spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.CPM), fechaDeleted, seleccionConsultasPreliminares);
                                }
                            } else {
                                boolean isEMP = false;
                                try {
                                    isEMP = (((JAXBElement<ContractFolderStatusType>) entry.getAny().get(0)).getValue().getTenderResult().get(0).getResultCode().getValue().compareTo("11") == 0);
                                }
                                catch(Exception e){
                                    isEMP = false;
                                }

                                if (isEMP) {
                                    if(seleccionEncargosMediosPropios.size()>0) {
                                        procesarEncargo(entry, spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.EMP), fechaDeleted, seleccionEncargosMediosPropios);
                                    }
                                } else {
                                    if (args.dosTablas) {
                                        procesarEntry(entry, spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.LICITACIONES), fechaDeleted, seleccionLicitacionGenerales);
                                        procesarEntryResultados(entry, spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.RESULTADOS), fechaDeleted, seleccionLicitacionResultados);
                                    } else {
                                        procesarEntryCompleta(entry, spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.LICITACIONES), fechaDeleted, seleccionLicitacionGenerales, seleccionLicitacionResultados);
                                    }
                                }
                            }

                            entriesProcesadas.add(entry.getId().getValue());
                        }
                    }

                    // siguiente fichero
                    for (LinkType linkType : res.getLink()) {
                        existeFicheroRisp = false;
                        if (linkType.getRel().toLowerCase().compareTo("next") == 0) {
                            String[] tempArray = linkType.getHref().split("/");
                            String nombreSiguienteRIPS = tempArray[tempArray.length - 1];
                            ficheroRISP = new File(directorioPath + "/" + nombreSiguienteRIPS);
                            existeFicheroRisp = ficheroRISP.exists() && ficheroRISP.isFile();
                        }
                    }
                    inStream.close();
                    numeroFicherosProcesados++;
                }
                
                System.out.println("  Procesados " + numeroFicherosProcesados + " ficheros ATOM de esta fuente");
            }

            // spreeadSheetManager.insertarFiltro(seleccionLicitacionGenerales.size(), seleccionLicitacionResultados.size(), seleccionEncargosMediosPropios.size(), seleccionConsultasPreliminares.size());

            // Eliminar hojas no deseadas antes de guardar (Presentacion y Resultados)
            SXSSFWorkbook wb = spreeadSheetManager.getWorkbook();
            // Eliminar hoja "Presentacion" si existe (con variantes de nombre)
            int idxPresentacion = wb.getSheetIndex("Presentación");
            if (idxPresentacion >= 0) {
                wb.removeSheetAt(idxPresentacion);
            }
            int idxPresentacion2 = wb.getSheetIndex("Presentacion");
            if (idxPresentacion2 >= 0) {
                wb.removeSheetAt(idxPresentacion2);
            }
            int idxPresentacion3 = wb.getSheetIndex("PRESENTACION");
            if (idxPresentacion3 >= 0) {
                wb.removeSheetAt(idxPresentacion3);
            }
            int idxPresentacion4 = wb.getSheetIndex("presentaci\u00f3n");
            if (idxPresentacion4 >= 0) {
                wb.removeSheetAt(idxPresentacion4);
            }
            // Eliminar hoja "Resultados" si existe
            int idxResultados = wb.getSheetIndex(SpreeadSheetManager.RESULTADOS);
            if (idxResultados >= 0) {
                wb.removeSheetAt(idxResultados);
            }

            // ===================================================================
            // 🔥 MODIFICACIÓN CLAVE: Conversión SXSSF → XSSF para Power BI
            // ===================================================================
            // 1. Crear archivo temporal con SXSSF
            File tempFile = File.createTempFile("temp_excel_", ".xlsx");
            try (FileOutputStream tempFos = new FileOutputStream(tempFile)) {
                wb.write(tempFos);
            } finally {
                wb.dispose(); // Importante: liberar recursos de SXSSF
                wb.close();
            }

            // 2. Leer archivo temporal con XSSF y reparar estructura
            try (XSSFWorkbook xssfWorkbook = new XSSFWorkbook(tempFile);
                 FileOutputStream finalFos = new FileOutputStream(args.outPath)) {
                
                // Eliminar hojas no deseadas en el XSSFWorkbook (por si acaso)
                int idxPresentacionX = xssfWorkbook.getSheetIndex("Presentación");
                if (idxPresentacionX >= 0) {
                    xssfWorkbook.removeSheetAt(idxPresentacionX);
                }
                int idxPresentacionX2 = xssfWorkbook.getSheetIndex("Presentacion");
                if (idxPresentacionX2 >= 0) {
                    xssfWorkbook.removeSheetAt(idxPresentacionX2);
                }
                int idxPresentacionX3 = xssfWorkbook.getSheetIndex("PRESENTACION");
                if (idxPresentacionX3 >= 0) {
                    xssfWorkbook.removeSheetAt(idxPresentacionX3);
                }
                int idxPresentacionX4 = xssfWorkbook.getSheetIndex("presentaci\u00f3n");
                if (idxPresentacionX4 >= 0) {
                    xssfWorkbook.removeSheetAt(idxPresentacionX4);
                }

                // Añadir metadatos personalizados a la hoja "Licitaciones"
                POIXMLProperties props = xssfWorkbook.getProperties();
                POIXMLProperties.CustomProperties customProps = props.getCustomProperties();
                customProps.addProperty("Name", "Licitaciones");
                customProps.addProperty("Data", "Table");
                customProps.addProperty("Item", "Licitaciones");
                customProps.addProperty("Kind", "Sheet");
                customProps.addProperty("Hidden", false);

                // Forzar la creación de componentes necesarios si faltan
                if (xssfWorkbook.getNumberOfSheets() == 0) {
                    xssfWorkbook.createSheet("Licitaciones");
                }
                
                // Asegurar que haya al menos un estilo definido
                if (xssfWorkbook.getNumCellStyles() == 0) {
                    xssfWorkbook.createCellStyle();
                }

                // Escribir el archivo final con XSSF (compatible con Power BI)
                xssfWorkbook.write(finalFos);
            }

            // 3. Eliminar archivo temporal
            tempFile.delete();
            
            System.out.println("Total: " + numeroEntries + " entries procesadas, " + entriesProcesadas.size() + " únicas");

        } catch (JAXBException e) {
            // Error al procesar el fichero ATOM
            throw e;
        } catch (FileNotFoundException e) {
            // Error al generar el fichero de salida
            throw e;
        } catch (Exception e) {
            // Error inesperado
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void procesarEntry(EntryType entry, SXSSFSheet sheet, GregorianCalendar fechaDeleted, ArrayList<DatosLicitacionGenerales> buscadorDatosSeleecionables) {
        Cell cell;
        ContractFolderStatusType contractFolder = ((JAXBElement<ContractFolderStatusType>) entry.getAny().get(0)).getValue();

        Row row = sheet.createRow(sheet.getLastRowNum()+1);
        int cellnum = 0;

        cell = row.createCell(cellnum++);
        cell.setCellValue(entry.getId().getValue().substring(entry.getId().getValue().lastIndexOf("/")+1));
        cell = row.createCell(cellnum++);
        cell.setCellValue(entry.getLink().get(0).getHref());

        GregorianCalendar updated = entry.getUpdated().getValue().toGregorianCalendar();

        if (fechaDeleted == null || fechaDeleted.compareTo(updated) < 0) {
            cell = row.createCell(cellnum++);
            cell.setCellValue((LocalDateTime)entry.getUpdated().getValue().toGregorianCalendar().toZonedDateTime().toLocalDateTime());
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFechaLarga());
            cell = row.createCell(cellnum++);
            cell.setCellValue("VIGENTE");
        } else {
            cell = row.createCell(cellnum++);
            cell.setCellValue((LocalDateTime)fechaDeleted.toZonedDateTime().toLocalDateTime());
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFechaLarga());
            cell = row.createCell(cellnum++);
            if (((fechaDeleted.getTimeInMillis() - updated.getTimeInMillis())/1000/3660/24/365) > 5){
                cell.setCellValue("ARCHIVADA");
            } else {
                cell.setCellValue("ANULADA");
            }
        }

        for (DatosLicitacionGenerales dato: buscadorDatosSeleecionables) {

            Object datoCodice = dato.valorCodice(contractFolder);
            cell = row.createCell(cellnum++);
            if (datoCodice instanceof BigDecimal) {
                cell.setCellValue((double) ((BigDecimal)datoCodice).doubleValue());
            } else if (datoCodice instanceof String) {
                // Solo aplicar limpieza al campo OBJETO_CONTRATO (descripción)
                if (dato == DatosLicitacionGenerales.OBJETO_CONTRATO
                ) {
                    cell.setCellValue((String) datoCodice);
                    //cell.setCellValue(limpiarSaltosDeLinea((String) datoCodice));
                } else {
                    cell.setCellValue((String) datoCodice);
                }
            } else if (datoCodice instanceof GregorianCalendar) {
                cell.setCellValue((LocalDateTime) ((GregorianCalendar)datoCodice).toZonedDateTime().toLocalDateTime());
            } else if (datoCodice instanceof Boolean) {
                cell.setCellValue((Boolean) datoCodice);
            }
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFormato(dato.getFormato()));
        }
    }

    @SuppressWarnings("unchecked")
    private void procesarEntryResultados(EntryType entry, SXSSFSheet sheet, GregorianCalendar fechaDeleted, ArrayList<DatosResultados> buscadorDatosResultados) {
        Cell cell;
        ContractFolderStatusType contractFolder = ((JAXBElement<ContractFolderStatusType>) entry.getAny().get(0)).getValue();

        if(contractFolder.getTenderResult() != null) {
            for (int indice = 0; indice < contractFolder.getTenderResult().size(); indice++) {
                Row row = sheet.createRow(sheet.getLastRowNum()+1);
                int cellnum = 0;

                cell = row.createCell(cellnum++);
                cell.setCellValue(entry.getId().getValue().substring(entry.getId().getValue().lastIndexOf("/")+1));
                cell = row.createCell(cellnum++);
                cell.setCellValue(entry.getLink().get(0).getHref());

                GregorianCalendar updated = entry.getUpdated().getValue().toGregorianCalendar();
                if (fechaDeleted == null || fechaDeleted.compareTo(updated) < 0) {
                    cell = row.createCell(cellnum++);
                    cell.setCellValue((LocalDateTime)entry.getUpdated().getValue().toGregorianCalendar().toZonedDateTime().toLocalDateTime());
                    cell.setCellStyle(SpreeadSheetManager.getCellStyleFechaLarga());
                } else {
                    cell = row.createCell(cellnum++);
                    cell.setCellValue((LocalDateTime)fechaDeleted.toZonedDateTime().toLocalDateTime());
                    cell.setCellStyle(SpreeadSheetManager.getCellStyleFechaLarga());
                }

                for (DatosResultados dato: buscadorDatosResultados) {
                    Object datoCodice = dato.valorCodice(contractFolder, indice);
                    cell = row.createCell(cellnum++);
                    if (datoCodice instanceof BigDecimal) {
                        cell.setCellValue((double) ((BigDecimal)datoCodice).doubleValue());
                    } else if (datoCodice instanceof String) {
                        // DatosResultados no tiene OBJETO_CONTRATO, no aplicar limpieza
                        cell.setCellValue((String) datoCodice);
                    } else if (datoCodice instanceof GregorianCalendar) {
                        cell.setCellValue((LocalDateTime) ((GregorianCalendar)datoCodice).toZonedDateTime().toLocalDateTime());
                    } else if (datoCodice instanceof Boolean) {
                        cell.setCellValue((Boolean) datoCodice);
                    }
                    cell.setCellStyle(SpreeadSheetManager.getCellStyleFormato(dato.getFormato()));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void procesarEntryCompleta(EntryType entry, SXSSFSheet sheet, GregorianCalendar fechaDeleted,
                                       ArrayList<DatosLicitacionGenerales> buscadorDatosSeleccionables,
                                       ArrayList<DatosResultados> buscadorDatosResultados) {
        Cell cell;
        ContractFolderStatusType contractFolder = ((JAXBElement<ContractFolderStatusType>) entry.getAny().get(0)).getValue();

        if(contractFolder.getTenderResult().size() > 0) {
            for (int indice = 0; indice < contractFolder.getTenderResult().size(); indice++) {
                procesarEntry(entry, sheet, fechaDeleted, buscadorDatosSeleccionables);
                Row row = sheet.getRow(sheet.getLastRowNum());
                int cellnum = buscadorDatosSeleccionables.size()+4;
                for (DatosResultados dato: buscadorDatosResultados) {
                    Object datoCodice = dato.valorCodice(contractFolder, indice);
                    cell = row.createCell(cellnum++);
                    if (datoCodice instanceof BigDecimal) {
                        cell.setCellValue((double) ((BigDecimal)datoCodice).doubleValue());
                    } else if (datoCodice instanceof String) {
                        // DatosResultados no tiene OBJETO_CONTRATO, no aplicar limpieza
                        cell.setCellValue((String) datoCodice);
                    } else if (datoCodice instanceof GregorianCalendar) {
                        cell.setCellValue((LocalDateTime) ((GregorianCalendar)datoCodice).toZonedDateTime().toLocalDateTime());
                    } else if (datoCodice instanceof Boolean) {
                        cell.setCellValue((Boolean) datoCodice);
                    }
                    cell.setCellStyle(SpreeadSheetManager.getCellStyleFormato(dato.getFormato()));
                }
            }
        } else {
            procesarEntry(entry, sheet, fechaDeleted, buscadorDatosSeleccionables);
        }
    }

    @SuppressWarnings("unchecked")
    private void procesarEncargo(EntryType entry, SXSSFSheet sheet, GregorianCalendar fechaDeleted, ArrayList<DatosEMP> buscadorDatosSelecionables) {
        Cell cell;
        ContractFolderStatusType contractFolder = ((JAXBElement<ContractFolderStatusType>) entry.getAny().get(0)).getValue();

        Row row = sheet.createRow(sheet.getLastRowNum()+1);
        int cellnum = 0;

        cell = row.createCell(cellnum++);
        cell.setCellValue(entry.getId().getValue().substring(entry.getId().getValue().lastIndexOf("/")+1));
        cell = row.createCell(cellnum++);
        cell.setCellValue(entry.getLink().get(0).getHref());

        GregorianCalendar updated = entry.getUpdated().getValue().toGregorianCalendar();

        if (fechaDeleted == null || fechaDeleted.compareTo(updated) < 0) {
            cell = row.createCell(cellnum++);
            cell.setCellValue((LocalDateTime)entry.getUpdated().getValue().toGregorianCalendar().toZonedDateTime().toLocalDateTime());
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFechaLarga());
            cell = row.createCell(cellnum++);
            cell.setCellValue("VIGENTE");
        } else {
            cell = row.createCell(cellnum++);
            cell.setCellValue((LocalDateTime)fechaDeleted.toZonedDateTime().toLocalDateTime());
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFechaLarga());
            cell = row.createCell(cellnum++);
            if (((fechaDeleted.getTimeInMillis() - updated.getTimeInMillis())/1000/3660/24/365) > 5){
                cell.setCellValue("ARCHIVADA");
            } else {
                cell.setCellValue("ANULADA");
            }
        }

        for (DatosEMP dato: buscadorDatosSelecionables) {
            Object datoCodice = dato.valorCodice(contractFolder);
            cell = row.createCell(cellnum++);
            if (datoCodice instanceof BigDecimal) {
                cell.setCellValue((double) ((BigDecimal)datoCodice).doubleValue());
            } else if (datoCodice instanceof String) {
                // Solo aplicar limpieza al campo OBJETO_CONTRATO (descripción)
                if (dato == DatosEMP.OBJETO_CONTRATO) {
                    cell.setCellValue(limpiarSaltosDeLinea((String) datoCodice));
                } else {
                    cell.setCellValue((String) datoCodice);
                }
            } else if (datoCodice instanceof GregorianCalendar) {
                cell.setCellValue((LocalDateTime) ((GregorianCalendar)datoCodice).toZonedDateTime().toLocalDateTime());
            } else if (datoCodice instanceof Boolean) {
                cell.setCellValue((Boolean) datoCodice);
            }
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFormato(dato.getFormato()));
        }
    }

    @SuppressWarnings("unchecked")
    private void procesarCPM(EntryType entry, SXSSFSheet sheet, GregorianCalendar fechaDeleted, ArrayList<DatosCPM> buscadorDatosSelecionables) {
        Cell cell;
        PreliminaryMarketConsultationStatusType preliminaryMarketConsultationStatusType = ((JAXBElement<PreliminaryMarketConsultationStatusType>) entry.getAny().get(0)).getValue();

        Row row = sheet.createRow(sheet.getLastRowNum()+1);
        int cellnum = 0;

        cell = row.createCell(cellnum++);
        cell.setCellValue(entry.getId().getValue().substring(entry.getId().getValue().lastIndexOf("/")+1));
        cell = row.createCell(cellnum++);
        cell.setCellValue(entry.getLink().get(0).getHref());

        GregorianCalendar updated = entry.getUpdated().getValue().toGregorianCalendar();

        if (fechaDeleted == null || fechaDeleted.compareTo(updated) < 0) {
            cell = row.createCell(cellnum++);
            cell.setCellValue((LocalDateTime)entry.getUpdated().getValue().toGregorianCalendar().toZonedDateTime().toLocalDateTime());
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFechaLarga());
            cell = row.createCell(cellnum++);
            cell.setCellValue("VIGENTE");
        } else {
            cell = row.createCell(cellnum++);
            cell.setCellValue((LocalDateTime)fechaDeleted.toZonedDateTime().toLocalDateTime());
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFechaLarga());
            cell = row.createCell(cellnum++);
            if (((fechaDeleted.getTimeInMillis() - updated.getTimeInMillis())/1000/3660/24/365) > 5){
                cell.setCellValue("ARCHIVADA");
            } else {
                cell.setCellValue("ANULADA");
            }
        }

        for (DatosCPM dato: buscadorDatosSelecionables) {
            Object datoCodice = dato.valorCodice(preliminaryMarketConsultationStatusType);
            cell = row.createCell(cellnum++);
            if (datoCodice instanceof BigDecimal) {
                cell.setCellValue((double) ((BigDecimal)datoCodice).doubleValue());
            } else if (datoCodice instanceof String) {
                // Solo aplicar limpieza al campo OBJETO_CONTRATO (descripción)
                if (dato == DatosCPM.OBJETO_CONTRATO) {
                    cell.setCellValue(limpiarSaltosDeLinea((String) datoCodice));
                } else {
                    cell.setCellValue((String) datoCodice);
                }
            } else if (datoCodice instanceof GregorianCalendar) {
                cell.setCellValue((LocalDateTime) ((GregorianCalendar)datoCodice).toZonedDateTime().toLocalDateTime());
            } else if (datoCodice instanceof Boolean) {
                cell.setCellValue((Boolean) datoCodice);
            }
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFormato(dato.getFormato()));
        }
    }

    private void insertarTitulos(SpreeadSheetManager spreeadSheetManager,
                                 ArrayList<DatosLicitacionGenerales> seleccionLicitacionGenerales,
                                 ArrayList<DatosResultados> seleccionLicitacionResultados,
                                 ArrayList<DatosEMP> seleccionEncargosMediosPropios,
                                 ArrayList<DatosCPM> seleccionConsultasPreliminares) {
        SXSSFSheet hoja;
        Row row;
        int cellnum;
        Cell cell;

        // LICITACIONES
        hoja = spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.LICITACIONES);
        row = hoja.createRow(0);
        cellnum = 0;
        cell = row.createCell(cellnum++); cell.setCellValue("Identificador"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
        cell = row.createCell(cellnum++); cell.setCellValue("Link licitaci\u00F3n"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
        cell = row.createCell(cellnum++); cell.setCellValue("Fecha actualizaci\u00F3n"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
        cell = row.createCell(cellnum++); cell.setCellValue("Vigente/Anulada/Archivada"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
        for (DatosLicitacionGenerales dato : seleccionLicitacionGenerales) {
            cell = row.createCell(cellnum++); cell.setCellValue(dato.getTiulo()); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
        }
        if (spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.RESULTADOS) == null) {
            for (DatosResultados dato : seleccionLicitacionResultados) {
                cell = row.createCell(cellnum++); cell.setCellValue(dato.getTiulo()); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            }
        }

        // RESULTADOS
        if (spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.RESULTADOS) != null) {
            hoja = spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.RESULTADOS);
            row = hoja.createRow(0);
            cellnum = 0;
            cell = row.createCell(cellnum++); cell.setCellValue("Identificador"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            cell = row.createCell(cellnum++); cell.setCellValue("Link licitaci\u00F3n"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            cell = row.createCell(cellnum++); cell.setCellValue("Fecha actualizaci\u00F3n"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            for (DatosResultados dato : seleccionLicitacionResultados) {
                cell = row.createCell(cellnum++); cell.setCellValue(dato.getTiulo()); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            }
        }

        // EMP
        if (spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.EMP) != null) {
            hoja = spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.EMP);
            row = hoja.createRow(0);
            cellnum = 0;
            cell = row.createCell(cellnum++); cell.setCellValue("Identificador"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            cell = row.createCell(cellnum++); cell.setCellValue("Link Encargo"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            cell = row.createCell(cellnum++); cell.setCellValue("Fecha actualizaci\u00F3n"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            cell = row.createCell(cellnum++); cell.setCellValue("Vigente/Anulada/Archivada"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            for (DatosEMP dato : seleccionEncargosMediosPropios) {
                cell = row.createCell(cellnum++); cell.setCellValue(dato.getTiulo()); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            }
        }

        // CPM
        if (spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.CPM) != null) {
            hoja = spreeadSheetManager.getWorkbook().getSheet(SpreeadSheetManager.CPM);
            row = hoja.createRow(0);
            cellnum = 0;
            cell = row.createCell(cellnum++); cell.setCellValue("Identificador"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            cell = row.createCell(cellnum++); cell.setCellValue("Link Consulta"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            cell = row.createCell(cellnum++); cell.setCellValue("Fecha actualizaci\u00F3n"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            cell = row.createCell(cellnum++); cell.setCellValue("Vigente/Anulada/Archivada"); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            for (DatosCPM dato : seleccionConsultasPreliminares) {
                cell = row.createCell(cellnum++); cell.setCellValue(dato.getTiulo()); cell.setCellStyle(SpreeadSheetManager.getCellStyleTitulo());
            }
        }
    }

    private static class Args {
        final List<String> inPaths;
        final String outPath;
        final boolean dosTablas;
        final boolean sinEMP;
        final boolean sinCPM;
        final boolean valid;
        final int exitCode;

        private Args(List<String> inPaths, String outPath, boolean dosTablas, boolean sinEMP, boolean sinCPM, boolean valid, int exitCode) {
            this.inPaths = inPaths;
            this.outPath = outPath;
            this.dosTablas = dosTablas;
            this.sinEMP = sinEMP;
            this.sinCPM = sinCPM;
            this.valid = valid;
            this.exitCode = exitCode;
        }

        static Args parse(String[] args) {
            if (args == null || args.length == 0) {
                // Si no hay argumentos, intentamos usar las rutas por defecto editables en la clase
                boolean haveDefaults = AtomToExcelCLI.DEFAULT_IN_PATH != null && !AtomToExcelCLI.DEFAULT_IN_PATH.isEmpty()
                                     && AtomToExcelCLI.DEFAULT_OUT_PATH != null && !AtomToExcelCLI.DEFAULT_OUT_PATH.isEmpty();
                if (haveDefaults) {
                    List<String> defaultPaths = new ArrayList<>();
                    defaultPaths.add(AtomToExcelCLI.DEFAULT_IN_PATH);
                    return new Args(defaultPaths, AtomToExcelCLI.DEFAULT_OUT_PATH, false, false, false, true, 0);
                }
                return new Args(new ArrayList<>(), null, false, false, false, false, 1);
            }
            List<String> inPaths = new ArrayList<>();
            String out = null;
            boolean dosTablas = false, sinEMP = false, sinCPM = false;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--help": return new Args(new ArrayList<>(), null, false, false, false, false, 0);
                    case "--in": if (i+1 < args.length) inPaths.add(args[++i]); break;
                    case "--out": if (i+1 < args.length) out = args[++i]; break;
                    case "--dos-tablas": dosTablas = true; break;
                    case "--sin-emp": sinEMP = true; break;
                    case "--sin-cpm": sinCPM = true; break;
                    default: break;
                }
            }
            boolean ok = !inPaths.isEmpty() && out != null;
            // Si faltan argumentos, usamos defaults si están configurados
            if (!ok && AtomToExcelCLI.DEFAULT_IN_PATH != null && !AtomToExcelCLI.DEFAULT_IN_PATH.isEmpty()
                    && AtomToExcelCLI.DEFAULT_OUT_PATH != null && !AtomToExcelCLI.DEFAULT_OUT_PATH.isEmpty()) {
                if (inPaths.isEmpty()) {
                    inPaths.add(AtomToExcelCLI.DEFAULT_IN_PATH);
                }
                if (out == null) {
                    out = AtomToExcelCLI.DEFAULT_OUT_PATH;
                }
                ok = true;
            }
            return new Args(inPaths, out, dosTablas, sinEMP, sinCPM, ok, ok ? 0 : 1);
        }

        String usage() {
            return "Uso:\n" +
                   "  --in <path>        Fichero ATOM o ZIP (puede repetirse para múltiples archivos)\n" +
                   "  --out <path.xlsx>  Fichero Excel de salida\n" +
                   "  [--dos-tablas]     Separar licitaciones y resultados\n" +
                   "  [--sin-emp]        No incluir hoja EMP\n" +
                   "  [--sin-cpm]        No incluir hoja CPM\n" +
                   "  [--help]           Mostrar esta ayuda\n" +
                   "\nSi --in es un .zip, se descomprimirá automáticamente\n" +
                   "y se buscará el .atom con el mismo nombre.\n" +
                   "\nPuedes especificar múltiples --in para combinar varias fuentes ATOM\n" +
                   "en un único archivo Excel.\n" +
                   "\nTambién puedes editar en la clase:\n" +
                   "  DEFAULT_IN_PATH  y DEFAULT_OUT_PATH para ejecutar sin argumentos";
        }
    }
}