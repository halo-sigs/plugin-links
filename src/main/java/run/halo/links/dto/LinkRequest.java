package run.halo.links.dto;

import java.util.Objects;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import run.halo.links.security.SafeUrlFetcher;

public class LinkRequest {

    public static LinkDetailDTO getLinkDetail(String linkUrl) {
        Document document = SafeUrlFetcher.fetch(linkUrl, SafeUrlFetcher.FetchOptions.html(linkUrl))
            .document();

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
