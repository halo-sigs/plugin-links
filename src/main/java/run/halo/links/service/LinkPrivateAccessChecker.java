package run.halo.links.service;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.AnonymousUserConst;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import static run.halo.app.extension.index.query.Queries.in;

/**
 * Checks whether the current user is allowed to view private links on theme-facing pages.
 *
 * @author halo
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LinkPrivateAccessChecker {

    static final String LINK_MANAGE_ROLE_NAME = "role-template-link-manage";

    static final String SUPER_ROLE_NAME = "super-role";

    private static final String ROLE_PREFIX = "ROLE_";

    private final ReactiveExtensionClient client;

    /**
     * Whether the current user has link management permission. Anonymous users and users
     * without the permission resolve to {@code false}.
     */
    public Mono<Boolean> canViewPrivateLinks() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .filter(auth -> !AnonymousUserConst.isAnonymousUser(auth.getName()))
            .map(LinkPrivateAccessChecker::roleNames)
            .flatMap(this::containsManageRole)
            .defaultIfEmpty(false);
    }

    private static Set<String> roleNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(LinkPrivateAccessChecker::toRoleName)
            .collect(Collectors.toSet());
    }

    private static String toRoleName(String authority) {
        return authority.startsWith(ROLE_PREFIX)
            ? authority.substring(ROLE_PREFIX.length())
            : authority;
    }

    private Mono<Boolean> containsManageRole(Set<String> roleNames) {
        if (roleNames.contains(LINK_MANAGE_ROLE_NAME) || roleNames.contains(SUPER_ROLE_NAME)) {
            return Mono.just(true);
        }
        if (roleNames.isEmpty()) {
            return Mono.just(false);
        }
        var options = ListOptions.builder()
            .andQuery(in("metadata.name", roleNames.stream().map(name -> (Object) name).toList()))
            .build();
        return client.listAll(Role.class, options, Sort.unsorted())
            .collectMap(role -> role.getMetadata().getName())
            .map(rolesByName -> hasManageRole(roleNames, rolesByName));
    }

    private static boolean hasManageRole(Set<String> roleNames, Map<String, Role> rolesByName) {
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<>(roleNames);
        while (!queue.isEmpty()) {
            var name = queue.poll();
            if (!visited.add(name)) {
                continue;
            }
            if (LINK_MANAGE_ROLE_NAME.equals(name) || SUPER_ROLE_NAME.equals(name)) {
                return true;
            }
            var role = rolesByName.get(name);
            if (role != null) {
                queue.addAll(dependencies(role));
            }
        }
        return false;
    }

    private static List<String> dependencies(Role role) {
        var json = MetadataUtil.nullSafeAnnotations(role).get(Role.ROLE_DEPENDENCIES_ANNO);
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            return JsonMapper.shared().readValue(json, new TypeReference<List<String>>() {
            });
        } catch (RuntimeException e) {
            log.debug("Failed to read role dependencies of {}.", role.getMetadata().getName(), e);
            return List.of();
        }
    }
}
