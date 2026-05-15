package run.halo.links.dto;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.web.server.ServerErrorException;
import run.halo.links.security.LinkSecurityUtils;
import run.halo.links.security.RedirectHandler;

public class LinkRequest {

    private final static String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/58.0.3029.110 Safari/537.3";

    private final static int TIMEOUT_MS = 10_000;
    private final static int MAX_BODY_SIZE = 1024 * 1024 * 20;

    public static LinkDetailDTO getLinkDetail(String linkUrl) {
        URL url;
        try {
            url = new URL(linkUrl);
        } catch (MalformedURLException e) {
            throw new ServerErrorException("Invalid URL", e);
        }

        try {
            LinkSecurityUtils.validateUrl(url);
        } catch (IllegalArgumentException e) {
            throw new ServerErrorException("URL blocked for security reasons", e);
        }

        Map<String, String> headers = Map.of(
            "User-Agent", USER_AGENT,
            "Referer", linkUrl,
            "Accept", "text/html,application/xhtml+xml,application/xml"
        );

        Document document;
        try {
            Connection.Response response = Jsoup.connect(url.toExternalForm())
                .followRedirects(false)
                .maxBodySize(MAX_BODY_SIZE)
                .timeout(TIMEOUT_MS)
                .headers(headers)
                .execute();

            RedirectHandler redirectHandler =
                new RedirectHandler(headers, TIMEOUT_MS, MAX_BODY_SIZE);
            document = redirectHandler.followRedirects(response);
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
