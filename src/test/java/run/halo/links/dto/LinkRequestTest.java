package run.halo.links.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerErrorException;

class LinkRequestTest {

    @Test
    void shouldBlockDirectAccessToPrivateIp() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("http://192.168.1.1/"))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldBlockDirectAccessToLoopback() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("http://127.0.0.1:8090/actuator"))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldBlockNonHttpScheme() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("file:///etc/passwd"))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldFetchPublicUrlSuccessfully() {
        LinkDetailDTO detail = LinkRequest.getLinkDetail("https://example.com");
        assertThat(detail.getTitle()).isNotBlank();
    }
}
