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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="CodeList",namespace="http://docs.oasis-open.org/codelist/ns/genericode/1.0/")
public class CodeList {
	@XmlElement(name="SimpleCodeList")
	protected SimpleCodeList simpleCodeList;
	
	public SimpleCodeList getSimpleCodeList() {
		return simpleCodeList;
	}
	
	public void setSimpleCodeList(SimpleCodeList simpleCodeList) {
		this.simpleCodeList = simpleCodeList;
	}
	
}
