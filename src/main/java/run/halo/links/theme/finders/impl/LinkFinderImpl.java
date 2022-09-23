package run.halo.links.theme.finders.impl;

import org.springframework.stereotype.Service;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.finders.Finder;
import run.halo.links.Link;
import run.halo.links.theme.finders.LinkFinder;

import java.util.Comparator;
import java.util.List;

@Service
@Finder("linkFinder")
public class LinkFinderImpl implements LinkFinder {
    public static final Comparator<Link> DEFAULT_COMPARATOR =
            Comparator.comparing(link -> link.getMetadata().getCreationTimestamp());

    private final ReactiveExtensionClient client;

    public LinkFinderImpl(ReactiveExtensionClient client) {
        this.client = client;
    }


    @Override
    public List<Link> listAll() {
        return client.list(Link.class, null, DEFAULT_COMPARATOR.reversed()).collectList().block();
    }
}
