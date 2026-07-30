package run.halo.links.support;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpCookie;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;

public final class ServerRequestFixtures {

    private static final List<HttpMessageReader<?>> MESSAGE_READERS =
        HandlerStrategies.withDefaults().messageReaders();

    private ServerRequestFixtures() {
    }

    public static ServerRequest request(String scheme, String remoteAddress,
        Map<String, String> cookies, Map<String, String> headers) {
        var builder = MockServerHttpRequest.get(scheme + "://example.test/links/captcha")
            .remoteAddress(new InetSocketAddress(remoteAddress, 8080));
        cookies.forEach((name, value) -> builder.cookie(new HttpCookie(name, value)));
        headers.forEach(builder::header);
        return ServerRequest.create(MockServerWebExchange.from(builder.build()), MESSAGE_READERS);
    }

    public static ServerRequest request(String remoteAddress) {
        return request("http", remoteAddress, Map.of(), Map.of());
    }
}
