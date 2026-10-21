package run.halo.links.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.Role;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@ExtendWith(MockitoExtension.class)
class LinkPrivateAccessCheckerTest {

    @Mock
    ReactiveExtensionClient client;

    @Test
    void shouldDenyAnonymousUser() {
        var checker = new LinkPrivateAccessChecker(client);
        var authentication = new AnonymousAuthenticationToken("key", "anonymousUser",
            List.of(new SimpleGrantedAuthority("ROLE_anonymous")));

        StepVerifier.create(checker.canViewPrivateLinks()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldDenyWhenNoSecurityContext() {
        var checker = new LinkPrivateAccessChecker(client);

        StepVerifier.create(checker.canViewPrivateLinks())
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldAllowUserWithManageRole() {
        var checker = new LinkPrivateAccessChecker(client);
        var authentication = new UsernamePasswordAuthenticationToken("admin", "password",
            List.of(new SimpleGrantedAuthority("ROLE_role-template-link-manage")));

        StepVerifier.create(checker.canViewPrivateLinks()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldAllowUserWhoseRoleDependsOnManageRole() {
        when(client.listAll(any(), any(), any())).thenReturn(Flux.just(role(
            "role-template-link-admin", "[\"role-template-link-manage\"]")));
        var checker = new LinkPrivateAccessChecker(client);
        var authentication = new UsernamePasswordAuthenticationToken("admin", "password",
            List.of(new SimpleGrantedAuthority("ROLE_role-template-link-admin")));

        StepVerifier.create(checker.canViewPrivateLinks()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldDenyUserWithUnrelatedRole() {
        when(client.listAll(any(), any(), any())).thenReturn(Flux.just(role(
            "role-template-post-view", "[]")));
        var checker = new LinkPrivateAccessChecker(client);
        var authentication = new UsernamePasswordAuthenticationToken("user", "password",
            List.of(new SimpleGrantedAuthority("ROLE_role-template-post-view")));

        StepVerifier.create(checker.canViewPrivateLinks()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .expectNext(false)
            .verifyComplete();
    }

    static Role role(String name, String dependencies) {
        var role = new Role();
        role.setMetadata(new Metadata());
        role.getMetadata().setName(name);
        role.getMetadata().setAnnotations(
            java.util.Map.of(Role.ROLE_DEPENDENCIES_ANNO, dependencies));
        return role;
    }
}
