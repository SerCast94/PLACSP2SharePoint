package es.age.dgpe.placsp.risp.parser.downloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import es.age.dgpe.placsp.risp.parser.exceptions.DownloadException;
import es.age.dgpe.placsp.risp.parser.exceptions.NetworkException;
import es.age.dgpe.placsp.risp.parser.utils.EnvConfig;
import es.age.dgpe.placsp.risp.parser.utils.PlacspLogger;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import javax.net.ssl.SSLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clase responsable de extraer enlaces de paginas web usando JSoup.
 * Especializada en buscar enlaces de archivos ZIP con patron de aÃ±o-mes.
 * 
 * El patron de busqueda es configurable desde el archivo .env (ZIP_LINK_PATTERN)
 */
public class WebScraper {

    // Patron cargado desde configuracion
    private final Pattern anyoMesPattern;
    
    // Timeouts configurables
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    /**
     * Constructor que carga el patron desde configuracion.
     */
    public WebScraper() {
        this.anyoMesPattern = EnvConfig.getZipLinkPattern();
    }

    /**
     * Extrae el enlace mas reciente que coincida con el patron YYYYMM.zip de una pagina web.
     * Compara las fechas (YYYYMM) y devuelve el enlace con la fecha mas alta.
     * 
     * @param url URL de la pagina web a analizar
     * @return URL del archivo ZIP mas reciente encontrado, o null si no se encuentra
     * @throws NetworkException si hay error de red al conectar con la pagina
     * @throws DownloadException si no se encuentran enlaces
     */
    public String extraerPrimerEnlaceAnyoMes(String url) throws NetworkException, DownloadException {
        List<String> enlaces = extraerEnlacesAnyoMes(url, 1);
        return enlaces.isEmpty() ? null : enlaces.get(0);
    }

    /**
     * Extrae los N enlaces mas recientes que coincidan con el patron YYYYMM.zip de una pagina web.
     * Los enlaces se devuelven ordenados de mas antiguo a mas reciente.
     * 
     * @param url URL de la pagina web a analizar
     * @param cantidad Numero maximo de enlaces a devolver
     * @return Lista de URLs de archivos ZIP ordenados de mas antiguo a mas reciente
     * @throws NetworkException si hay error de red al conectar con la pagina
     * @throws DownloadException si no se encuentran enlaces
     */
    public List<String> extraerEnlacesAnyoMes(String url, int cantidad) throws NetworkException, DownloadException {
        Document doc;
        
        try {
            doc = Jsoup.connect(url)
                    .timeout(DEFAULT_TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();
        } catch (SocketTimeoutException e) {
            NetworkException ex = NetworkException.timeout(url, e);
            PlacspLogger.networkError(url, "TIMEOUT", e);
            throw ex;
        } catch (UnknownHostException e) {
            NetworkException ex = NetworkException.dnsNotResolved(url, e);
            PlacspLogger.networkError(url, "DNS_NO_RESUELTO", e);
            throw ex;
        } catch (ConnectException e) {
            NetworkException ex = NetworkException.connectionRefused(url, e);
            PlacspLogger.networkError(url, "CONEXION_RECHAZADA", e);
            throw ex;
        } catch (SSLException e) {
            NetworkException ex = NetworkException.sslError(url, e);
            PlacspLogger.networkError(url, "ERROR_SSL", e);
            throw ex;
        } catch (MalformedURLException e) {
            NetworkException ex = NetworkException.invalidUrl(url, e);
            PlacspLogger.networkError(url, "URL_INVALIDA", e);
            throw ex;
        } catch (org.jsoup.HttpStatusException e) {
            NetworkException ex = NetworkException.webDown(url, e.getStatusCode());
            PlacspLogger.networkError(url, "HTTP_" + e.getStatusCode(), e);
            throw ex;
        } catch (IOException e) {
            NetworkException ex = new NetworkException("Error de red al conectar con: " + url, e);
            PlacspLogger.networkError(url, "ERROR_IO", e);
            throw ex;
        }
        
        Elements links = doc.select("a[href]");
        
        // Lista de pares (fecha, enlace)
        List<int[]> enlacesConFecha = new ArrayList<>();
        List<String> enlacesOrdenados = new ArrayList<>();
        
        for (Element link : links) {
            String href = link.attr("abs:href");
            Matcher matcher = anyoMesPattern.matcher(href);
            if (matcher.find()) {
                int fecha = Integer.parseInt(matcher.group(1));
                // Evitar duplicados
                boolean existe = false;
                for (int[] par : enlacesConFecha) {
                    if (par[0] == fecha) {
                        existe = true;
                        break;
                    }
                }
                if (!existe) {
                    enlacesConFecha.add(new int[]{fecha, enlacesOrdenados.size()});
                    enlacesOrdenados.add(href);
                }
            }
        }
        
        if (enlacesConFecha.isEmpty()) {
            PlacspLogger.warning("No se encontraron enlaces de descarga en: " + url);
            // No lanzamos excepción aquí, simplemente devolvemos lista vacía
            return new ArrayList<>();
        }
        
        // Ordenar por fecha descendente (mas reciente primero)
        enlacesConFecha.sort((a, b) -> Integer.compare(b[0], a[0]));
        
        // Tomar los N mas recientes
        List<String> resultado = new ArrayList<>();
        for (int i = 0; i < Math.min(cantidad, enlacesConFecha.size()); i++) {
            resultado.add(enlacesOrdenados.get(enlacesConFecha.get(i)[1]));
        }
        
        // Invertir para que estÃ©n de mas antiguo a mas reciente
        List<String> resultadoFinal = new ArrayList<>();
        for (int i = resultado.size() - 1; i >= 0; i--) {
            resultadoFinal.add(resultado.get(i));
        }
        
        PlacspLogger.info("Encontrados " + resultadoFinal.size() + " enlaces en: " + url);
        return resultadoFinal;
    }

    /**
     * Extrae el nombre del archivo de una URL.
     * 
     * @param url URL completa
     * @return Nombre del archivo
     */
    public String extraerNombreArchivo(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
