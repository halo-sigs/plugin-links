package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class LinkSecurityUtilsTest {

    @Test
    void shouldBlockIpv4Loopback() {
        assertThatThrownBy(() -> LinkSecurityUtils.validateUrl("http://127.0.0.1/actuator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private/reserved");
    }

    @Test
    void shouldBlockIpv4PrivateRange10() {
        assertThatThrownBy(() -> LinkSecurityUtils.validateUrl("http://10.0.0.1/"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private/reserved");
    }

    @Test
    void shouldBlockIpv4PrivateRange172() {
        assertThatThrownBy(() -> LinkSecurityUtils.validateUrl("http://172.16.0.1/"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private/reserved");
    }

    @Test
    void shouldBlockIpv4PrivateRange192() {
        assertThatThrownBy(() -> LinkSecurityUtils.validateUrl("http://192.168.1.1/"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private/reserved");
    }

    @Test
    void shouldBlockIpv6Loopback() {
        assertThatThrownBy(() -> LinkSecurityUtils.validateUrl("http://[::1]/"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private/reserved");
    }

    @Test
    void shouldBlockIpv6LinkLocal() {
        assertThatThrownBy(() -> LinkSecurityUtils.validateUrl("http://[fe80::1]/"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private/reserved");
    }

    @Test
    void shouldAllowPublicUrl() {
        assertThatCode(() -> LinkSecurityUtils.validateUrl("https://example.com"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowPublicIp() {
        assertThatCode(() -> LinkSecurityUtils.validateUrl("http://8.8.8.8"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldBlockFileScheme() {
        assertThatThrownBy(() -> LinkSecurityUtils.validateUrl("file:///etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only HTTP and HTTPS");
    }

    @Test
    void shouldBlockFtpScheme() {
        assertThatThrownBy(() -> LinkSecurityUtils.validateUrl("ftp://internal.ftp.server/resource"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only HTTP and HTTPS");
    }

    @Test
    void isPrivateAddressShouldDetectLoopback() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        assertThat(LinkSecurityUtils.isPrivateAddress(loopback)).isTrue();
    }

    @Test
    void isPrivateAddressShouldDetectSiteLocal() throws Exception {
        InetAddress siteLocal = InetAddress.getByName("192.168.1.1");
        assertThat(LinkSecurityUtils.isPrivateAddress(siteLocal)).isTrue();
    }

    @Test
    void isPrivateAddressShouldReturnFalseForPublic() throws Exception {
        InetAddress publicAddr = InetAddress.getByName("8.8.8.8");
        assertThat(LinkSecurityUtils.isPrivateAddress(publicAddr)).isFalse();
    }

    @Test
    void shouldBlock169254Range() throws Exception {
        InetAddress apipa = InetAddress.getByName("169.254.1.1");
        assertThat(LinkSecurityUtils.isPrivateAddress(apipa)).isTrue();
    }

    @Test
    void shouldBlockIpv6UlaFc00() throws Exception {
        InetAddress ula = InetAddress.getByName("fc00::1");
        assertThat(LinkSecurityUtils.isPrivateAddress(ula)).isTrue();
    }

    @Test
    void shouldBlockIpv6UlaFd00() throws Exception {
        InetAddress ula = InetAddress.getByName("fd00::1");
        assertThat(LinkSecurityUtils.isPrivateAddress(ula)).isTrue();
    }

    @Test
    void shouldBlockAnyLocalAddress() throws Exception {
        InetAddress anyLocal = InetAddress.getByName("0.0.0.0");
        assertThat(LinkSecurityUtils.isPrivateAddress(anyLocal)).isTrue();
    }

    @Test
    void shouldBlockMulticastAddress() throws Exception {
        InetAddress multicast = InetAddress.getByName("224.0.0.1");
        assertThat(LinkSecurityUtils.isPrivateAddress(multicast)).isTrue();
    }

    @Test
    void shouldAllowIpv6PublicAddress() throws Exception {
        InetAddress publicAddr = InetAddress.getByName("2001:db8::1");
        assertThat(LinkSecurityUtils.isPrivateAddress(publicAddr)).isFalse();
    }
}
