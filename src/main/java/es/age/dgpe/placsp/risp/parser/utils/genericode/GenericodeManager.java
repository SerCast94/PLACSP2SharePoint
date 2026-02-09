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
package es.age.dgpe.placsp.risp.parser.utils.genericode;

import java.io.InputStream;
import java.util.HashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

public class GenericodeManager {
	
	/**
	 * Lee un InputStream (xml con CodeList) a un objeto Java
	 * @param xml
	 * @return
	 * @throws Exception
	 */
	public static CodeList read(InputStream xml) throws Exception {
		JAXBContext jc;
		CodeList codeList = null;
		try {
			jc = JAXBContext.newInstance(CodeList.class.getPackage().getName());
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			codeList = (CodeList) unmarshaller.unmarshal(xml);
	        xml.close();
		} catch (Exception e) {
			System.err.println("Error leyendo CodeList: " + e.getMessage());
			throw e;
		}	
		return codeList;
	}
	
	
	public static HashMap<String, String> generateMap(InputStream xmlGenericCode) throws Exception{
		CodeList codeList = read(xmlGenericCode);
		HashMap<String, String> mapGenericode = new HashMap<String, String>();
		
		//Se recorre la lista y se inserta en el map
		if (codeList != null && codeList.getSimpleCodeList() != null && codeList.getSimpleCodeList().getRows() != null) {
			for (Row row : codeList.getSimpleCodeList().getRows()) {
				String clave = null;
				String valor = null;
				if (row.getValues() != null) {
					for (Value value : row.getValues()) {
						if (value.getColumnRef().equals("code")) {
							clave = value.getSimpleValue();
						}
						else if (value.getColumnRef().equals("nombre")) {
							valor = value.getSimpleValue();
						}
					}
				}
				mapGenericode.put(clave, valor);
			}
		}
		return mapGenericode;
	}

}
