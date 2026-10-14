package run.halo.links.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkImageReferenceTest {

    @ParameterizedTest
    @ValueSource(strings = {"/upload/cover.png", "https://example.com/cover.png",
        "http://example.com/cover.png"})
    void acceptsAbsoluteAndSiteRelativeImageReferences(String value) {
        assertThat(LinkImageReference.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"//example.com/cover.png", "javascript:alert(1)", "ftp://example.com/a"})
    void rejectsUnsafeImageReferences(String value) {
        assertThat(LinkImageReference.isValid(value)).isFalse();
    }
}
