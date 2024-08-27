package run.halo.links;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.web.server.ServerErrorException;

/**
 * @author LIlGG
 */
public class LinkRequest {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/58.0.3029.110 Safari/537.3";

    public static LinkDetailDTO getLinkDetail(String linkUrl) {
        Document document;
        try {
            document = Jsoup.connect(linkUrl)
                .followRedirects(true)
                .maxBodySize(1024 * 1024 * 20)
                .timeout(10000)
                .headers(
                    Map.of(
                        "User-Agent", USER_AGENT,
                        "Referer", linkUrl,
                        "Accept", "text/html,application/xhtml+xml,application/xml"
                    )
                )
                .get();
        } catch (IOException e) {
            throw new ServerErrorException("Failed to get link detail", e);
        }

        LinkDetailDTO linkDetailDTO = new LinkDetailDTO();
        linkDetailDTO.setTitle(document.title());
        linkDetailDTO.setDescription(document.select("meta[name=description]")
            .attr("content"));
        Element iconElement = document.selectFirst("link[rel=icon]");
        if (Objects.nonNull(iconElement)) {
            linkDetailDTO.setIcon(iconElement.absUrl("href"));
        }
        Element imageElement = document.selectFirst("meta[property=og:image]");
        if (Objects.nonNull(imageElement)) {
            linkDetailDTO.setImage(imageElement.absUrl("content"));
        }
        return linkDetailDTO;
    }
}
