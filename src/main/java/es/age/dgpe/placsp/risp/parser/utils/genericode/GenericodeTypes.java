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

import java.util.HashMap;

public enum GenericodeTypes {
	ESTADO("/gc/SyndicationContractFolderStatusCode-2.04.gc"),
	TIPO_CONTRATO("/gc/ContractCode-2.08.gc"),
	TIPO_PROCEDIMIENTO("/gc/SyndicationTenderingProcessCode-2.07.gc"),
	SISTEMA_CONTRATACION("/gc/ContractingSystemTypeCode-2.08.gc"),
	TRAMITACION("/gc/DiligenceTypeCode-1.04.gc"),
	PRESENTACION_OFERTA("/gc/TenderDeliveryCode-1.04.gc"),
	RESULTADO("/gc/TenderResultCode-2.09.gc"),
	TIPO_ADMINISTRACION("/gc/ContractingAuthorityCode-2.10.gc"),
	CODIGO_FINANCIACION("/gc/FundingProgramCode-2.08.gc"),
	ESTADO_CONSULTA_PRELIMINAR("/gc/PreliminaryMarketConsultationStatusCode-2.09.gc"),
	TIPO_CONSULTA_PRELIMINAR("/gc/PreliminaryMarketConsultationTypeCode-2.09.gc");
	
	private HashMap<String, String> codes = null;
	
	GenericodeTypes(String nombreGenericode){
		try {
			codes = GenericodeManager.generateMap(GenericodeTypes.class.getResourceAsStream(nombreGenericode));
		} catch (Exception e) {
			System.err.println("Error cargando codigos " + nombreGenericode + ": " + e.getMessage());
		}
	}
	
	public String getValue(String key) {
		if (codes.containsKey(key)) {
			return codes.get(key);
		}
		else {
			return key;
		}
		
		
	}

}
