package es.age.dgpe.placsp.risp.parser.downloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clase responsable de extraer enlaces de páginas web usando JSoup.
 * Especializada en buscar enlaces de archivos ZIP con patrón de año-mes.
 */
public class WebScraper {

    private static final Pattern ANYO_MES_PATTERN = Pattern.compile("_(\\d{6})\\.zip$");

    /**
     * Extrae el primer enlace que coincida con el patrón YYYYMM.zip de una página web.
     * 
     * @param url URL de la página web a analizar
     * @return URL del primer archivo ZIP encontrado, o null si no se encuentra
     * @throws IOException si hay error al conectar con la página
     */
    public String extraerPrimerEnlaceAnyoMes(String url) throws IOException {
        Document doc = Jsoup.connect(url).get();
        Elements links = doc.select("a[href]");
        
        for (Element link : links) {
            String href = link.attr("abs:href");
            Matcher matcher = ANYO_MES_PATTERN.matcher(href);
            if (matcher.find()) {
                return href;
            }
        }
        
        return null;
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
