/*******************************************************************************
 * Copyright 2021 Subdirección General de Coordinación de la Contratación Electronica - Dirección General Del Patrimonio Del Estado - Subsecretaría de Hacienda - Ministerio de Hacienda - Administración General del Estado - Gobierno de España
 * 
 * Licencia con arreglo a la EUPL, Versión 1.2 o –en cuanto sean aprobadas por la Comisión Europea– versiones posteriores de la EUPL (la «Licencia»);
 * Solo podrá usarse esta obra si se respeta la Licencia.
 * Puede obtenerse una copia de la Licencia en:
 * 
 * https://joinup.ec.europa.eu/software/page/eupl
 * 
 * Salvo cuando lo exija la legislación aplicable o se acuerde por escrito, el programa distribuido con arreglo a la Licencia se distribuye «TAL CUAL», SIN GARANTÍAS NI CONDICIONES DE NINGÚN TIPO, ni expresas ni implícitas.
 * Véase la Licencia en el idioma concreto que rige los permisos y limitaciones que establece la Licencia.
 ******************************************************************************/
package es.age.dgpe.placsp.risp.parser.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;

import org.dgpe.codice.common.caclib.CommodityClassificationType;
import org.dgpe.codice.common.caclib.PartyIdentificationType;
import org.dgpe.codice.common.cbclib.FundingProgramCodeType;

import es.age.dgpe.placsp.risp.parser.utils.genericode.GenericodeTypes;
import ext.place.codice.common.caclib.AdditionalPublicationDocumentReferenceType;
import ext.place.codice.common.caclib.AdditionalPublicationStatusType;
import ext.place.codice.common.caclib.ContractFolderStatusType;
import ext.place.codice.common.caclib.NoticeInfoType;
 
public enum DatosLicitacionGenerales{
	PRIMERA_PUBLICACION("Primera publicaci\u00f3n", EnumFormatos.FECHA_CORTA) {
	@Override
		public GregorianCalendar valorCodice(ContractFolderStatusType contractFolder) {
			GregorianCalendar primeraPublicacion = null;

			try {
				// Se recorren los validnoticeinfo
				for (NoticeInfoType noticeInfo : contractFolder.getValidNoticeInfo()) {
					try {
						// No se tiene en cuenta si es anuncio previo
						if (noticeInfo.getNoticeTypeCode().getValue().compareTo("DOC_PIN") != 0) {
							// Se recorren los medios de publicacion
							for (AdditionalPublicationStatusType additionalPublicationStatus : noticeInfo
									.getAdditionalPublicationStatus()) {
								// Se obtiene la fecha más antigua
								for (AdditionalPublicationDocumentReferenceType additionalPublicationDocumentReference : additionalPublicationStatus
										.getAdditionalPublicationDocumentReference()) {
									GregorianCalendar fecha = additionalPublicationDocumentReference.getIssueDate().getValue().toGregorianCalendar();
									if (primeraPublicacion == null
											|| primeraPublicacion.compareTo(fecha) == DatatypeConstants.GREATER) {
										primeraPublicacion = fecha;
									}
								}
							}
						}
					} catch (Exception e) {
						//El ATOM cumple con el esquema, pero no con los requisito
					}
				}
				return primeraPublicacion;
			} catch (Exception e) {
				return null;
			}
		}
	},
	ESTADO ("Estado"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder){
			try {
				String estado = GenericodeTypes.ESTADO.getValue(contractFolder.getContractFolderStatusCode().getValue());
				return estado;
			}catch (Exception e) {
				return null;
			}
		}
	},
	NUMERO_EXPEDIENTE ("N\u00famero de expediente"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder){
			try {
				String numExpediente = contractFolder.getContractFolderID().getValue();
				return numExpediente;
			}catch (Exception e) {
				return null;
			}
		}
	},
	OBJETO_CONTRATO ("Objeto del Contrato"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder){
			try {
				String objeto = contractFolder.getProcurementProject().getName().get(0).getValue();
				return objeto;
			}catch (Exception e) {
				return null;
			}
		}
	},
	ID_TED ("Identificador \u00fanico TED"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder){
			try {
				String objeto = contractFolder.getUUID().get(0).getValue();
				return objeto;
			}catch (Exception e) {
				return null;
			}
		}
	},
	VALOR_ESTIMADO ("Valor estimado del contrato", EnumFormatos.MONEDA){
		@Override
		public BigDecimal valorCodice(ContractFolderStatusType contractFolder){
			try {
				BigDecimal valorEstimado = contractFolder.getProcurementProject().getBudgetAmount().getEstimatedOverallContractAmount().getValue();
				return valorEstimado;
			}catch (Exception e) {
				return null;
			}
			
		}
	},
	PRESUPUESTO_BASE_SIN_IMPUESTOS ("Presupuesto base sin impuestos", EnumFormatos.MONEDA){
		@Override
		public BigDecimal valorCodice(ContractFolderStatusType contractFolder) {
			try {
				BigDecimal presupuestoConImpuestos = contractFolder.getProcurementProject().getBudgetAmount().getTaxExclusiveAmount().getValue();
				return presupuestoConImpuestos;
			}catch (Exception e) {
				return null;
			}
		}
	},
	PRESUPUESTO_BASE_CON_IMPUESTOS ("Presupuesto base con impuestos", EnumFormatos.MONEDA){
		@Override
		public BigDecimal valorCodice(ContractFolderStatusType contractFolder) {
			try {
				BigDecimal presupuestoSinImpuestos = contractFolder.getProcurementProject().getBudgetAmount().getTotalAmount().getValue();
				return presupuestoSinImpuestos;	
			}catch (Exception e) {
				return null;
			}
		}
	},
	CPV ("CPV"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			String valoresCPV = "";
			try {
				for (CommodityClassificationType commodity : contractFolder.getProcurementProject().getRequiredCommodityClassification()) {
					valoresCPV += commodity.getItemClassificationCode().getValue() + SEPARADOR;
				}
				return valoresCPV;
			}catch(Exception e) {
				return valoresCPV;
			}			
		}
	},
	TIPO_CONTRATO ("Tipo de contrato"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return GenericodeTypes.TIPO_CONTRATO.getValue(contractFolder.getProcurementProject().getTypeCode().getValue());
			}catch(Exception e) {
				return null;
			}			
		}
	},
	CONTRATO_MIXTO ("Contrato mixto"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				if(contractFolder.getProcurementProject().getMixContractIndicator().isValue()) {
					return "Sí";
				}else {
					return "No";
				}
			} catch (Exception e) {
				return null;
			}
		}
	},
	LUGAR_EJECUCION ("Lugar de ejecuci\u00f3n"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			String codigo = "";
			String descripcion = "";
			try {
				codigo = contractFolder.getProcurementProject().getRealizedLocation().getCountrySubentityCode().getValue();				
			}catch(Exception e) {
				codigo = "";
			}			
			try {
				descripcion = contractFolder.getProcurementProject().getRealizedLocation().getCountrySubentity().getValue();				
			}catch(Exception e) {
				descripcion = "";
			}
			
			if (codigo == "" && descripcion == "") {
				//Se intenta obtener el codigo del país
				try {
					codigo = contractFolder.getProcurementProject().getRealizedLocation().getAddress().getCountry().getIdentificationCode().getValue();
				}catch(Exception e) {
					codigo = "";
				}			
				try {
					descripcion = contractFolder.getProcurementProject().getRealizedLocation().getAddress().getCountry().getName().getValue();
				}catch(Exception e) {
					descripcion = "";
				}
				
			}
			
			return codigo + " - " + descripcion;
		}
	},
	ORGANO_CONTRATACION ("\u00d3rgano de Contrataci\u00f3n"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return contractFolder.getLocatedContractingParty().getParty().getPartyName().get(0).getName().getValue();
			}catch(Exception e) {
				return null;
			}			
		}
	},
	ID_PLATAFORMA_OC ("ID OC en PLACSP"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {//Si es perfil en PLACSP --> ID_PLATAFORMA, si es desde agregadas --> ID_PLAT+"-"+ID_OC_PLAT
				String idPlatOC = "";
				for (PartyIdentificationType partyIdentificationType : contractFolder.getLocatedContractingParty().getParty().getPartyIdentification()) {
					if (partyIdentificationType.getID().getSchemeName().compareTo("ID_PLATAFORMA") == 0){
						idPlatOC = partyIdentificationType.getID().getValue();
					}
					if (partyIdentificationType.getID().getSchemeName().compareTo("ID_OC_PLAT") == 0){
						idPlatOC = contractFolder.getLocatedContractingParty().getParty().getAgentParty().getPartyIdentification().get(0).getID().getValue()
								+"-"
								+partyIdentificationType.getID().getValue();
					}
				}
				return idPlatOC;
			}catch(Exception e) {
				return null;
			}			
		}
	},
	NIF_OC ("NIF OC"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				String nif = "";
				for (PartyIdentificationType partyIdentificationType : contractFolder.getLocatedContractingParty().getParty().getPartyIdentification()) {
					if (partyIdentificationType.getID().getSchemeName().compareTo("NIF") == 0){
						nif = partyIdentificationType.getID().getValue();
					}
				}
				return nif;
			}catch(Exception e) {
				return null;
			}			
		}
	},
	DIR3 ("DIR3"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				String dir3 = "";
				for (PartyIdentificationType partyIdentificationType : contractFolder.getLocatedContractingParty().getParty().getPartyIdentification()) {
					if (partyIdentificationType.getID().getSchemeName().compareTo("DIR3") == 0){
						dir3 = partyIdentificationType.getID().getValue();
					}
				}				
				return dir3;
			}catch(Exception e) {
				return null;
			}			
		}
	},
	ENLACE_PERFIL_CONTRATANTE ("Enlace al Perfil de Contratante del OC"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return contractFolder.getLocatedContractingParty().getBuyerProfileURIID().getValue();
			}catch(Exception e) {
				return null;
			}			
		}
	},
	TIPO_ADMINISTRACION ("Tipo de Administraci\u00f3n"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {				
				return GenericodeTypes.TIPO_ADMINISTRACION.getValue(contractFolder.getLocatedContractingParty().getContractingPartyTypeCode().getValue());
			}catch(Exception e) {
				return null;
			}			
		}
	},
	CODIGO_POSTAL ("C\u00f3digo Postal"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {			
				return contractFolder.getLocatedContractingParty().getParty().getPostalAddress().getPostalZone().getValue();
			}catch(Exception e) {
				return null;
			}			
		}
	},
	TIPO_PROCEDIMIENTO ("Tipo de procedimiento"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return GenericodeTypes.TIPO_PROCEDIMIENTO.getValue(contractFolder.getTenderingProcess().getProcedureCode().getValue());
			} catch (Exception e) {
				return null;
			}
		}
	},
	SISTEMA_CONTRATACION ("Sistema de contrataci\u00f3n"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return GenericodeTypes.SISTEMA_CONTRATACION.getValue(contractFolder.getTenderingProcess().getContractingSystemCode().getValue());
			} catch (Exception e) {
				return null;
			}
		}
	},
	TRAMITACION ("Tramitaci\u00f3n"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return GenericodeTypes.TRAMITACION.getValue(contractFolder.getTenderingProcess().getUrgencyCode().getValue());
			} catch (Exception e) {
				return null;
			}
		}
	},
	PRESENTACION_OFERTA ("Forma de presentaci\u00f3n de la oferta"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return GenericodeTypes.PRESENTACION_OFERTA.getValue(contractFolder.getTenderingProcess().getSubmissionMethodCode().getValue());
			} catch (Exception e) {
				return null;
			}
		}
	},
	FECHA_PRESENTACION_OFERTAS ("Fecha de presentaci\u00f3n de ofertas",  EnumFormatos.FECHA_LARGA){
		@Override
		public GregorianCalendar valorCodice(ContractFolderStatusType contractFolder) {
			GregorianCalendar fechaFinal = new GregorianCalendar();
			try {
				//Se intenta recuper la fehca de fin de presentación de ofertas
				XMLGregorianCalendar finPresentacionDia = contractFolder.getTenderingProcess().getTenderSubmissionDeadlinePeriod().getEndDate().getValue();
				XMLGregorianCalendar finPresentacionHora = contractFolder.getTenderingProcess().getTenderSubmissionDeadlinePeriod().getEndTime().getValue();
	
				LocalDate localDate = LocalDate.of(finPresentacionDia.getYear(), finPresentacionDia.getMonth(), finPresentacionDia.getDay());
				LocalTime localTime = LocalTime.of(finPresentacionHora.getHour(), finPresentacionHora.getMinute(), finPresentacionHora.getSecond());
				LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
				
				fechaFinal = GregorianCalendar.from(localDateTime.atZone(ZoneId.of("Europe/Paris")));
				
				return fechaFinal;
			} catch (Exception e) {
				return null;
			}
		}
	},
	FECHA_PRESENTACION_SOLICITUDES ("Fecha de presentaci\u00f3n de solicitudes de participaci\u00f3n",  EnumFormatos.FECHA_LARGA){
		@Override
		public GregorianCalendar valorCodice(ContractFolderStatusType contractFolder) {
			GregorianCalendar fechaFinal = new GregorianCalendar();
			try {
				//Se intenta recuperar la fecha de fin de presentación de solicituddes
				XMLGregorianCalendar finPresentacionDia = contractFolder.getTenderingProcess().getParticipationRequestReceptionPeriod().getEndDate().getValue();
				XMLGregorianCalendar finPresentacionHora = contractFolder.getTenderingProcess().getParticipationRequestReceptionPeriod().getEndTime().getValue();
				
				LocalDate localDate = LocalDate.of(finPresentacionDia.getYear(), finPresentacionDia.getMonth(), finPresentacionDia.getDay());
				LocalTime localTime = LocalTime.of(finPresentacionHora.getHour(), finPresentacionHora.getMinute(), finPresentacionHora.getSecond());
				LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
				
				fechaFinal = GregorianCalendar.from(localDateTime.atZone(ZoneId.of("Europe/Paris")));
				
				return fechaFinal;

			} catch (Exception e) {
				return null;
			}
		}
	},
	ES_REG_SARA ("Directiva de aplicaci\u00f3n"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return contractFolder.getTenderingTerms().getProcurementLegislationDocumentReference().getID().getValue();
			} catch (Exception e) {
				return null;
			}
		}
	},
	CONTRATO_SARA_UMBRAL ("Contrato SARA/Umbral"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				if(contractFolder.getTenderingProcess().getOverThresholdIndicator().isValue()) {
					return "Sí";
				}
					
				else {
					return "No";
				}
			} catch (Exception e) {
				return null;
			}
		}
	},
	FINANCIACION_EUROPEA ("Financiaci\u00f3n Europea y fuente"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				String financiacionEuropea = "";
				String fuenteFinanciacion = "";
				
				
				for (FundingProgramCodeType fundingProgramCodeType : contractFolder.getTenderingTerms().getFundingProgramCode()) {
					if (fundingProgramCodeType.getValue().compareTo("EU") == 0) financiacionEuropea = "Sí";
					if (fundingProgramCodeType.getValue().compareTo("NO-EU") == 0) financiacionEuropea = "No";
					if (fundingProgramCodeType.getValue().compareTo("REU") == 0 ||
						fundingProgramCodeType.getValue().compareTo("FEDER") == 0 ||
						fundingProgramCodeType.getValue().compareTo("FSE+") == 0 ||
						fundingProgramCodeType.getValue().compareTo("FEADER") == 0 ||
						fundingProgramCodeType.getValue().compareTo("FEMP") == 0 ||
						fundingProgramCodeType.getValue().compareTo("PRTR") == 0 ||
						fundingProgramCodeType.getValue().compareTo("OFE") == 0){
							//Hay fuente de financiación
						fuenteFinanciacion = GenericodeTypes.CODIGO_FINANCIACION.getValue(fundingProgramCodeType.getValue());
					}
						
				}
					
				if (fuenteFinanciacion.length() > 0) financiacionEuropea += " - " + fuenteFinanciacion;
				return financiacionEuropea;
			} catch (Exception e) {
				return null;
			}
		}
	},
	FINANCIACION_EUROPEA_DESCRIPCION("Descripci\u00f3n de la financiaci\u00f3n europea"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return contractFolder.getTenderingTerms().getFundingProgram().get(0).getValue();
			} catch (Exception e) {
				return null;
			}
		}
	},
	SUBASTA_ELECTRONICA ("Subasta electr\u00f3nica"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				if(contractFolder.getTenderingProcess().getAuctionTerms().getAuctionConstraintIndicator().isValue()) {
					return "Sí";
				}else {
					return "No";
				}
			} catch (Exception e) {
				return null;
			}
		}
	},
	SUBCONTRACION_PERMITIDA_DESC ("Subcontrataci\u00f3n permitida"){
		@Override
		public String valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return contractFolder.getTenderingTerms().getAllowedSubcontractTerms().get(0).getDescription().get(0).getValue();
			} catch (Exception e) {
				return null;
			}
		}
	},
	SUBCONTRACION_PERMITIDA_RATE ("Subcontrataci\u00f3n permitida porcentaje"){
		@Override
		public BigDecimal valorCodice(ContractFolderStatusType contractFolder) {
			try {
				return contractFolder.getTenderingTerms().getAllowedSubcontractTerms().get(0).getRate().getValue();
			} catch (Exception e) {
				return null;
			}
		}
	};

	private final static String SEPARADOR = ";";
	
	private final String titulo;
	private final EnumFormatos formato;

	DatosLicitacionGenerales(String name, EnumFormatos format) {
		this.titulo = name;
		this.formato = format;
	}
	
	DatosLicitacionGenerales(String name){
		this.titulo = name;
		this.formato = EnumFormatos.TEXTO;
	}
	
	
	public String getTiulo() {	
		return titulo;
	}
	
	public EnumFormatos getFormato() {
		return formato;
	}
	
	
	public abstract Object valorCodice(ContractFolderStatusType contractFolder);
	

}
