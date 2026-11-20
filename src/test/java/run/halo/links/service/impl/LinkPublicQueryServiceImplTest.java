package run.halo.links.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkGroup;
import run.halo.links.service.LinkPrivateAccessChecker;

class LinkPublicQueryServiceImplTest {

    private static LinkPrivateAccessChecker checker(boolean canViewPrivate) {
        var checker = mock(LinkPrivateAccessChecker.class);
        when(checker.canViewPrivateLinks()).thenReturn(Mono.just(canViewPrivate));
        return checker;
    }

    private static LinkGroup group(String name, LinkGroup.LinkGroupVisible visible) {
        var group = new LinkGroup();
        var metadata = new Metadata();
        metadata.setName(name);
        group.setMetadata(metadata);
        var spec = new LinkGroup.LinkGroupSpec();
        spec.setDisplayName(name);
        spec.setVisible(visible);
        group.setSpec(spec);
        return group;
    }

    private static Link link(String name, String groupName) {
        var link = new Link();
        var metadata = new Metadata();
        metadata.setName(name);
        link.setMetadata(metadata);
        var spec = new Link.LinkSpec();
        spec.setDisplayName(name);
        spec.setUrl("https://example.com");
        spec.setGroupName(groupName);
        link.setSpec(spec);
        return link;
    }

    @Test
    void shouldExcludePrivateGroupsFromGroupListingForUnauthorizedUser() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.listAll(eq(LinkGroup.class), any(), any(Sort.class)))
            .thenReturn(Flux.empty());
        var service = new LinkPublicQueryServiceImpl(client, checker(false));

        StepVerifier.create(service.listAllGroups(ListOptions.builder().build()))
            .assertNext(groups -> assertThat(groups).isEmpty())
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(ListOptions.class);
        org.mockito.Mockito.verify(client).listAll(eq(LinkGroup.class), captor.capture(),
            any(Sort.class));
        assertThat(String.valueOf(captor.getValue().getFieldSelector()))
            .contains("spec.visible")
            .contains(LinkGroup.LinkGroupVisible.PUBLIC.name());
    }

    @Test
    void shouldKeepGroupListingUnfilteredForPrivilegedUser() {
        var client = mock(ReactiveExtensionClient.class);
        var privateGroup = group("private", LinkGroup.LinkGroupVisible.PRIVATE);
        when(client.listAll(eq(LinkGroup.class), any(), any(Sort.class)))
            .thenReturn(Flux.just(privateGroup));
        var service = new LinkPublicQueryServiceImpl(client, checker(true));

        StepVerifier.create(service.listAllGroups(ListOptions.builder().build()))
            .assertNext(groups -> assertThat(groups).hasSize(1))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(ListOptions.class);
        org.mockito.Mockito.verify(client).listAll(eq(LinkGroup.class), captor.capture(),
            any(Sort.class));
        assertThat(String.valueOf(captor.getValue().getFieldSelector()))
            .doesNotContain("spec.visible");
    }

    @Test
    void shouldExcludeLinksInPrivateGroupsForUnauthorizedUser() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.listAll(eq(LinkGroup.class), any(), any(Sort.class)))
            .thenReturn(Flux.just(group("secret", LinkGroup.LinkGroupVisible.PRIVATE)));
        when(client.listAll(eq(Link.class), any(), any(Sort.class)))
            .thenReturn(Flux.empty());
        var service = new LinkPublicQueryServiceImpl(client, checker(false));

        StepVerifier.create(service.listAll(ListOptions.builder().build(), Sort.unsorted()))
            .assertNext(links -> assertThat(links).isEmpty())
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(ListOptions.class);
        org.mockito.Mockito.verify(client).listAll(eq(Link.class), captor.capture(),
            any(Sort.class));
        var selector = String.valueOf(captor.getValue().getFieldSelector());
        assertThat(selector)
            .contains("spec.visible")
            .contains(Link.LinkVisible.PUBLIC.name())
            .contains("spec.groupName")
            .contains("secret");
    }

    @Test
    void shouldNotExcludeAnyGroupWhenNoPrivateGroupExists() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.listAll(eq(LinkGroup.class), any(), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Link.class), any(), any(Sort.class)))
            .thenReturn(Flux.empty());
        var service = new LinkPublicQueryServiceImpl(client, checker(false));

        StepVerifier.create(service.listAll(ListOptions.builder().build(), Sort.unsorted()))
            .assertNext(links -> assertThat(links).isEmpty())
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(ListOptions.class);
        org.mockito.Mockito.verify(client).listAll(eq(Link.class), captor.capture(),
            any(Sort.class));
        var selector = String.valueOf(captor.getValue().getFieldSelector());
        assertThat(selector).contains("spec.visible");
        assertThat(selector).doesNotContain("spec.groupName");
    }

    @Test
    void shouldKeepLinkListingUnfilteredForPrivilegedUser() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.listAll(eq(Link.class), any(), any(Sort.class)))
            .thenReturn(Flux.just(link("link-a", "secret")));
        var service = new LinkPublicQueryServiceImpl(client, checker(true));

        StepVerifier.create(service.listAll(ListOptions.builder().build(), Sort.unsorted()))
            .assertNext(links -> assertThat(links).hasSize(1))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(ListOptions.class);
        org.mockito.Mockito.verify(client).listAll(eq(Link.class), captor.capture(),
            any(Sort.class));
        assertThat(String.valueOf(captor.getValue().getFieldSelector()))
            .doesNotContain("spec.visible");
    }

    @Test
    void shouldResolvePrivateGroupNames() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.listAll(eq(LinkGroup.class), any(), any(Sort.class)))
            .thenReturn(Flux.just(group("secret", LinkGroup.LinkGroupVisible.PRIVATE)));
        var service = new LinkPublicQueryServiceImpl(client, checker(false));

        StepVerifier.create(service.privateGroupNames())
            .assertNext(names -> assertThat(names).containsExactly("secret"))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(ListOptions.class);
        org.mockito.Mockito.verify(client).listAll(eq(LinkGroup.class), captor.capture(),
            any(Sort.class));
        assertThat(String.valueOf(captor.getValue().getFieldSelector()))
            .contains("spec.visible")
            .contains(LinkGroup.LinkGroupVisible.PRIVATE.name());
    }
}
