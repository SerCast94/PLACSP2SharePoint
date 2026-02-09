/*******************************************************************************
 * Copyright 2021 Subdirecciï¿½n General de Coordinaciï¿½n de la Contrataciï¿½n Electronica - Direcciï¿½n General Del Patrimonio Del Estado - Subsecretarï¿½a de Hacienda - Ministerio de Hacienda - Administraciï¿½n General del Estado - Gobierno de Espaï¿½a
 * 
 * Licencia con arreglo a la EUPL, Versiï¿½n 1.2 o ï¿½en cuanto sean aprobadas por la Comisiï¿½n Europeaï¿½ versiones posteriores de la EUPL (la ï¿½Licenciaï¿½);
 * Solo podrï¿½ usarse esta obra si se respeta la Licencia.
 * Puede obtenerse una copia de la Licencia en:
 * 
 * https://joinup.ec.europa.eu/software/page/eupl
 * 
 * Salvo cuando lo exija la legislaciï¿½n aplicable o se acuerde por escrito, el programa distribuido con arreglo a la Licencia se distribuye ï¿½TAL CUALï¿½, SIN GARANTï¿½AS NI CONDICIONES DE NINGï¿½N TIPO, ni expresas ni implï¿½citas.
 * Vï¿½ase la Licencia en el idioma concreto que rige los permisos y limitaciones que establece la Licencia.
 ******************************************************************************/
package es.age.dgpe.placsp.risp.parser.model;

import java.io.IOException;


import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import es.age.dgpe.placsp.risp.parser.utils.Config;
 
public class SpreeadSheetManager {
	
	// Logger eliminado - usamos System.err para errores
	
	private SXSSFWorkbook workbook = new SXSSFWorkbook(); 
	private static CellStyle cellStyleTexto;
	private static CellStyle cellStyleNumeroEntero;
	private static CellStyle cellStyleFechaLarga;
	private static CellStyle cellStyleFechaDia;
	private static CellStyle cellStyleMoneda;
	private static CellStyle cellStyleTitulo;

	public static final String LICITACIONES = "Licitaciones";
	public static final String RESULTADOS = "Resultados";
	public static final String EMP = "Encargos a medios propios";
	public static final String CPM = "Consultas Preliminares";
	
		
	
	/**
	 * @return the workbook
	 */
	public SXSSFWorkbook getWorkbook() {
		return workbook;
	}


	/**
	 * @param workbook the workbook to set
	 */
	public void setWorkbook(SXSSFWorkbook workbo) {
		workbook = workbo;
	}



	/**
	 * @return the cellStyleFechaLarga
	 */
	public static CellStyle getCellStyleFechaLarga() {
		return cellStyleFechaLarga;
	}
	
	/**
	 * @return the cellStyleTitulo
	 */
	public static CellStyle getCellStyleTitulo() {
		return cellStyleTitulo;
	}


	/**
	 * Creaciï¿½n de objeto
	 */
	public SpreeadSheetManager(boolean dosHojasLicitaciones, boolean hojaEMP, boolean hojaCPM) throws Exception{
		
		
		// Se crea a partir de una plantilla y se aï¿½aden dos hojas
		try {
			String rutaPlantilla = Config.getProperty("open-placsp.template.xlsx");
			
			workbook = new SXSSFWorkbook(new XSSFWorkbook( OPCPackage.open(SpreeadSheetManager.class.getResourceAsStream(rutaPlantilla))), 5);

			workbook.createSheet(LICITACIONES);
			if(dosHojasLicitaciones) {
				//Se aï¿½aden dos hojas al woorkbook
				workbook.createSheet(RESULTADOS); 
			}
			
			if (hojaEMP) {
				workbook.createSheet(EMP);	
			}
			
			if (hojaCPM) {
				workbook.createSheet(CPM);	
			}
			
			// Se definen los estilos que se van a utilizar
			
			// Texto
			cellStyleTexto = workbook.createCellStyle();
			CreationHelper createHelperTexto = workbook.getCreationHelper();
			cellStyleTexto.setDataFormat(createHelperTexto.createDataFormat().getFormat("@"));
						
			// Numero
			cellStyleNumeroEntero = workbook.createCellStyle();
			CreationHelper createHelperNumero = workbook.getCreationHelper();
			cellStyleNumeroEntero.setDataFormat(createHelperNumero.createDataFormat().getFormat("#,##0"));

			// Fecha larga
			cellStyleFechaLarga = workbook.createCellStyle();
			CreationHelper createHelper = workbook.getCreationHelper();
			cellStyleFechaLarga.setDataFormat(createHelper.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

			// Fecha corta
			cellStyleFechaDia = workbook.createCellStyle();
			CreationHelper createHelperFechaCorta = workbook.getCreationHelper();
			cellStyleFechaDia.setDataFormat(createHelperFechaCorta.createDataFormat().getFormat("dd/mm/yyyy"));

			// Moneda
			cellStyleMoneda = workbook.createCellStyle();
			CreationHelper createHelperMoneda = workbook.getCreationHelper();
			cellStyleMoneda.setDataFormat(createHelperMoneda.createDataFormat().getFormat("#,##0.00 \u20AC"));
			
			//Titulo
			cellStyleTitulo = workbook.createCellStyle();
			Font font= workbook.createFont();
		    font.setFontHeightInPoints((short)12);
		    font.setBold(true);
			cellStyleTitulo.setFont(font);
			cellStyleTitulo.setBorderBottom(BorderStyle.THIN);  
			cellStyleTitulo.setBottomBorderColor(IndexedColors.BLACK.getIndex());
			cellStyleTitulo.setWrapText(true);
			

		}catch (InvalidFormatException | IOException e) {
			System.err.println("Se produjo un error al cargar la plantilla");
			e.printStackTrace();
			throw e;
		}catch (Exception e) {
			System.err.println("Se produjo un error al cargar la plantilla");
			e.printStackTrace();
			throw e;
		}

	}

	/**
	 * Funciï¿½n que devuleve el estilo de XLSX asociado al tipo formato
	 * @param 
	 * @return
	 */
	public static CellStyle getCellStyleFormato(EnumFormatos formato) {
		switch(formato)
		{
		   case MONEDA:
		      return cellStyleMoneda;   
		   case FECHA_CORTA:
		      return cellStyleFechaDia;
		   case FECHA_LARGA:
			   return cellStyleFechaLarga;
		   case NUMERO:
			   return cellStyleNumeroEntero;
		   default : 
		      return cellStyleTexto;
		}
	}
	
	public void updateColumnsSize() {	
		for (int i = 1; i < workbook.getNumberOfSheets(); i++) {
			for (int j=0; j < workbook.getSheetAt(i).getRow(0).getLastCellNum(); j++){
				workbook.getSheetAt(i).setColumnWidth(j,Integer.valueOf(Config.getProperty("open-placsp.template.columnsize"))*256);
			}
		}		
	}
	
	
	public void insertarFiltro(int datosSeleccionables, int datosResultados, int datosEMP, int datosCPM){
		
		if (workbook.getSheet(RESULTADOS) != null) {
			//Hay dos tablas
			workbook.getSheet(LICITACIONES).setAutoFilter(new CellRangeAddress(0, 0, 0, datosSeleccionables + 3));
			workbook.getSheet(LICITACIONES).createFreezePane(0, 1);
			workbook.getSheet(RESULTADOS).setAutoFilter(new CellRangeAddress(0, 0, 0, datosResultados + 2));
			workbook.getSheet(RESULTADOS).createFreezePane(0, 1);
		}else {
			//Hay una tabla
			workbook.getSheet(LICITACIONES).setAutoFilter(new CellRangeAddress(0, 0, 0, datosSeleccionables + datosResultados  + 3));
			workbook.getSheet(LICITACIONES).createFreezePane(0, 1);
		}
		
		if (workbook.getSheet(EMP) != null) {
			workbook.getSheet(EMP).setAutoFilter(new CellRangeAddress(0, 0, 0, datosEMP + 3));
			workbook.getSheet(EMP).createFreezePane(0, 1);
		}
		
		if (workbook.getSheet(CPM) != null) {
			workbook.getSheet(CPM).setAutoFilter(new CellRangeAddress(0, 0, 0, datosCPM + 3));
			workbook.getSheet(CPM).createFreezePane(0, 1);
		}
	}
	

}
