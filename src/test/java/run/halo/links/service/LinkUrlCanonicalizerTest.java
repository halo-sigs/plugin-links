package run.halo.links.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkUrlCanonicalizerTest {

    @Test
    void shouldNormalizeOnlyConservativeComparisonParts() {
        assertThat(LinkUrlCanonicalizer.canonicalKey(
            " HTTPS://Example.COM:443#fragment "))
            .contains("https://example.com/");
        assertThat(LinkUrlCanonicalizer.canonicalKey(
            "http://Example.COM:80/path?q=1#fragment"))
            .contains("http://example.com/path?q=1");
    }

    @Test
    void shouldPreserveSchemeNonDefaultPortPathAndQuery() {
        assertThat(LinkUrlCanonicalizer.canonicalKey(
            "http://example.com:8080/a/?q=1"))
            .contains("http://example.com:8080/a/?q=1");
        assertThat(LinkUrlCanonicalizer.canonicalKey(
            "https://example.com/a"))
            .isNotEqualTo(LinkUrlCanonicalizer.canonicalKey(
                "http://example.com/a"));
        assertThat(LinkUrlCanonicalizer.canonicalKey(
            "https://example.com/a%20b?q=x%20y"))
            .contains("https://example.com/a%20b?q=x%20y");
    }

    @Test
    void shouldRejectNonHttpAndHostlessUrls() {
        assertThat(LinkUrlCanonicalizer.canonicalKey("ftp://example.com")).isEmpty();
        assertThat(LinkUrlCanonicalizer.canonicalKey("/relative")).isEmpty();
        assertThat(LinkUrlCanonicalizer.canonicalKey("not a url")).isEmpty();
    }
}
