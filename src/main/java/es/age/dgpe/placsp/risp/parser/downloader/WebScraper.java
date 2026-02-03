package es.age.dgpe.placsp.risp.parser.downloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import es.age.dgpe.placsp.risp.parser.utils.EnvConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clase responsable de extraer enlaces de páginas web usando JSoup.
 * Especializada en buscar enlaces de archivos ZIP con patrón de año-mes.
 * 
 * El patrón de búsqueda es configurable desde el archivo .env (ZIP_LINK_PATTERN)
 */
public class WebScraper {

    // Patrón cargado desde configuración
    private final Pattern anyoMesPattern;

    /**
     * Constructor que carga el patrón desde configuración.
     */
    public WebScraper() {
        this.anyoMesPattern = EnvConfig.getZipLinkPattern();
    }

    /**
     * Extrae el enlace más reciente que coincida con el patrón YYYYMM.zip de una página web.
     * Compara las fechas (YYYYMM) y devuelve el enlace con la fecha más alta.
     * 
     * @param url URL de la página web a analizar
     * @return URL del archivo ZIP más reciente encontrado, o null si no se encuentra
     * @throws IOException si hay error al conectar con la página
     */
    public String extraerPrimerEnlaceAnyoMes(String url) throws IOException {
        List<String> enlaces = extraerEnlacesAnyoMes(url, 1);
        return enlaces.isEmpty() ? null : enlaces.get(0);
    }

    /**
     * Extrae los N enlaces más recientes que coincidan con el patrón YYYYMM.zip de una página web.
     * Los enlaces se devuelven ordenados de más antiguo a más reciente.
     * 
     * @param url URL de la página web a analizar
     * @param cantidad Número máximo de enlaces a devolver
     * @return Lista de URLs de archivos ZIP ordenados de más antiguo a más reciente
     * @throws IOException si hay error al conectar con la página
     */
    public List<String> extraerEnlacesAnyoMes(String url, int cantidad) throws IOException {
        Document doc = Jsoup.connect(url).get();
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
        
        // Ordenar por fecha descendente (más reciente primero)
        enlacesConFecha.sort((a, b) -> Integer.compare(b[0], a[0]));
        
        // Tomar los N más recientes
        List<String> resultado = new ArrayList<>();
        for (int i = 0; i < Math.min(cantidad, enlacesConFecha.size()); i++) {
            resultado.add(enlacesOrdenados.get(enlacesConFecha.get(i)[1]));
        }
        
        // Invertir para que estén de más antiguo a más reciente
        List<String> resultadoFinal = new ArrayList<>();
        for (int i = resultado.size() - 1; i >= 0; i--) {
            resultadoFinal.add(resultado.get(i));
        }
        
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
