/*******************************************************************************
 * Copyright 2021 Subdirecci�n General de Coordinaci�n de la Contrataci�n Electronica - Direcci�n General Del Patrimonio Del Estado - Subsecretar�a de Hacienda - Ministerio de Hacienda - Administraci�n General del Estado - Gobierno de Espa�a
 * 
 * Licencia con arreglo a la EUPL, Versi�n 1.2 o �en cuanto sean aprobadas por la Comisi�n Europea� versiones posteriores de la EUPL (la �Licencia�);
 * Solo podr� usarse esta obra si se respeta la Licencia.
 * Puede obtenerse una copia de la Licencia en:
 * 
 * https://joinup.ec.europa.eu/software/page/eupl
 * 
 * Salvo cuando lo exija la legislaci�n aplicable o se acuerde por escrito, el programa distribuido con arreglo a la Licencia se distribuye �TAL CUAL�, SIN GARANT�AS NI CONDICIONES DE NING�N TIPO, ni expresas ni impl�citas.
 * V�ase la Licencia en el idioma concreto que rige los permisos y limitaciones que establece la Licencia.
 ******************************************************************************/
package es.age.dgpe.placsp.risp.parser.utils.genericode;

import java.io.FileInputStream;
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
