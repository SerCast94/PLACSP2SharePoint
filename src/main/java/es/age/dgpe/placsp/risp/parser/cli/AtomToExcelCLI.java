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
import java.nio.file.Paths;
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
import org.purl.atompub.tombstones._1.DeletedEntryType;
import org.w3._2005.atom.EntryType;
import org.w3._2005.atom.FeedType;
import org.w3._2005.atom.LinkType;

import es.age.dgpe.placsp.risp.parser.model.DatosCPM;
import es.age.dgpe.placsp.risp.parser.model.DatosEMP;
import es.age.dgpe.placsp.risp.parser.model.DatosLicitacionGenerales;
import es.age.dgpe.placsp.risp.parser.model.DatosResultados;
import es.age.dgpe.placsp.risp.parser.model.SpreeadSheetManager;
import ext.place.codice.common.caclib.ContractFolderStatusType;
import ext.place.codice.common.caclib.PreliminaryMarketConsultationStatusType;

/**
 * CLI tool to convert PLACSP RISP ATOM files to Excel using existing logic.
 * Soporta múltiples archivos ATOM de entrada que se combinan en un único Excel.
 *
 * Usage:
 *   --in <path.atom>       Path to an ATOM file (puede repetirse para múltiples archivos)
 *   --out <path.xlsx>      Output Excel path
 *   --dos-tablas           Output licitaciones + resultados in two sheets
 *   --sin-emp              Do not include EMP sheet
 *   --sin-cpm              Do not include CPM sheet
 */
public class AtomToExcelCLI {

    private static Unmarshaller atomUnMarshaller;

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
                    System.out.println("Usando archivo extraído: " + extractedAtom);
                    actualInPaths.add(extractedAtom);
                } else {
                    actualInPaths.add(inPath);
                }
            }

            Args actualArgs = new Args(actualInPaths, parsed.outPath, parsed.dosTablas, parsed.sinEMP, parsed.sinCPM, true, 0);
            new AtomToExcelCLI().convert(actualArgs);
            System.out.println("Conversión completada: " + parsed.outPath);
        } catch (Exception e) {
            System.err.println("Error en la conversión: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        } finally {
            // Limpiar directorio temporal si se creó
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

        throw new FileNotFoundException("No se encontró archivo .atom en el ZIP");
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
            // Stream de salida
            output_file = new FileOutputStream(new File(args.outPath));

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

            spreeadSheetManager.insertarFiltro(seleccionLicitacionGenerales.size(), seleccionLicitacionResultados.size(), seleccionEncargosMediosPropios.size(), seleccionConsultasPreliminares.size());

            spreeadSheetManager.getWorkbook().write(output_file);
            output_file.close();
            spreeadSheetManager.getWorkbook().close();
            
            System.out.println("Total: " + numeroEntries + " entries procesadas, " + entriesProcesadas.size() + " únicas");

        } catch (JAXBException e) {
            String auxError = "Error al procesar el fichero ATOM. No se puede continuar con el proceso.";
            throw e;
        } catch (FileNotFoundException e) {
            String auxError = "Error al generar el fichero de salida. No se pudo crear o abrir el fichero indicado.";
            throw e;
        } catch (Exception e) {
            String auxError = "Error inesperado, revise la configuración y el log...";
            throw e;
        }
    }

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
                cell.setCellValue((String) datoCodice);
            } else if (datoCodice instanceof GregorianCalendar) {
                cell.setCellValue((LocalDateTime) ((GregorianCalendar)datoCodice).toZonedDateTime().toLocalDateTime());
            } else if (datoCodice instanceof Boolean) {
                cell.setCellValue((Boolean) datoCodice);
            }
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFormato(dato.getFormato()));
        }
    }

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
                cell.setCellValue((String) datoCodice);
            } else if (datoCodice instanceof GregorianCalendar) {
                cell.setCellValue((LocalDateTime) ((GregorianCalendar)datoCodice).toZonedDateTime().toLocalDateTime());
            } else if (datoCodice instanceof Boolean) {
                cell.setCellValue((Boolean) datoCodice);
            }
            cell.setCellStyle(SpreeadSheetManager.getCellStyleFormato(dato.getFormato()));
        }
    }

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
                cell.setCellValue((String) datoCodice);
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
