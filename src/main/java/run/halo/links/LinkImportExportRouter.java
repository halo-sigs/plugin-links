package run.halo.links;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.Link;
import run.halo.links.LinkGroup;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkImportExportRouter {

    private final ReactiveExtensionClient client;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Bean
    RouterFunction<ServerResponse> importExportRoutes() {
        return route(POST("/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/import"), this::importLinks)
            .andRoute(GET("/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/export"), this::exportLinks);
    }

    private Mono<ServerResponse> importLinks(ServerRequest request) {
        return request.bodyToMono(String.class)
            .flatMap(yamlContent -> {
                try {
                    Map<String, Object> data = yamlMapper.readValue(yamlContent, new TypeReference<>() {});
                    return processImport(data);
                } catch (Exception e) {
                    log.error("Failed to parse YAML content", e);
                    return Mono.error(new IllegalArgumentException("解析 YAML 失败: " + e.getMessage()));
                }
            })
            .flatMap(result -> ServerResponse.ok().bodyValue(Map.of("message", "导入完成", "count", result)))
            .onErrorResume(e -> ServerResponse.badRequest().bodyValue(Map.of("message", e.getMessage())));
    }

    private Mono<Integer> processImport(Map<String, Object> data) {
        return Flux.fromIterable(data.entrySet())
            .flatMap(entry -> {
                String classKey = entry.getKey();
                Map<String, Object> classData = (Map<String, Object>) entry.getValue();
                String className = (String) classData.get("class_name");
                Map<String, Object> linkList = (Map<String, Object>) classData.get("link_list");

                if (className == null || linkList == null) {
                    return Flux.empty();
                }

                // 1. 确保 LinkGroup 存在
                return ensureLinkGroup(className)
                    .flatMapMany(groupName -> Flux.fromIterable(linkList.entrySet())
                        .flatMap(linkEntry -> {
                            Map<String, Object> linkData = (Map<String, Object>) linkEntry.getValue();
                            return createLink(linkData, groupName);
                        })
                    );
            })
            .collectList()
            .map(List::size);
    }

    private Mono<String> ensureLinkGroup(String displayName) {
        String name = "group-" + UUID.randomUUID().toString().substring(0, 8);
        return client.listAll(LinkGroup.class, new ListOptions(), Sort.unsorted())
            .filter(g -> displayName.equals(g.getSpec().getDisplayName()))
            .next()
            .map(g -> g.getMetadata().getName())
            .switchIfEmpty(Mono.defer(() -> {
                LinkGroup group = new LinkGroup();
                group.setMetadata(new Metadata());
                group.getMetadata().setName(name);
                LinkGroup.LinkGroupSpec spec = new LinkGroup.LinkGroupSpec();
                spec.setDisplayName(displayName);
                group.setSpec(spec);
                return client.create(group).map(g -> g.getMetadata().getName());
            }));
    }

    private Mono<Link> createLink(Map<String, Object> linkData, String groupName) {
        Link link = new Link();
        link.setMetadata(new Metadata());
        link.getMetadata().setName("link-" + UUID.randomUUID().toString().substring(0, 8));
        
        Link.LinkSpec spec = new Link.LinkSpec();
        spec.setDisplayName((String) linkData.get("name"));
        spec.setUrl((String) linkData.get("link"));
        spec.setLogo((String) linkData.get("avatar"));
        spec.setDescription((String) linkData.get("descr"));
        spec.setGroupName(groupName);
        
        link.setSpec(spec);
        return client.create(link);
    }

    private Mono<ServerResponse> exportLinks(ServerRequest request) {
        return client.listAll(LinkGroup.class, new ListOptions(), Sort.unsorted())
            .collectList()
            .flatMap(groups -> client.listAll(Link.class, new ListOptions(), Sort.unsorted())
                .collectList()
                .flatMap(links -> {
                    try {
                        Map<String, Object> root = new LinkedHashMap<>();
                        AtomicInteger classIndex = new AtomicInteger(1);

                        for (LinkGroup group : groups) {
                            String groupName = group.getMetadata().getName();
                            List<Link> groupLinks = links.stream()
                                .filter(l -> groupName.equals(l.getSpec().getGroupName()))
                                .toList();

                            if (groupLinks.isEmpty()) continue;

                            Map<String, Object> classData = new LinkedHashMap<>();
                            classData.put("class_name", group.getSpec().getDisplayName());

                            Map<String, Object> linkList = new LinkedHashMap<>();
                            int linkIndex = 1;
                            for (Link link : groupLinks) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("name", link.getSpec().getDisplayName());
                                item.put("link", link.getSpec().getUrl());
                                item.put("avatar", link.getSpec().getLogo());
                                item.put("descr", link.getSpec().getDescription());
                                linkList.put(String.valueOf(linkIndex++), item);
                            }

                            classData.put("link_list", linkList);
                            root.put("class" + classIndex.getAndIncrement(), classData);
                        }

                        String yaml = yamlMapper.writeValueAsString(root);
                        return ServerResponse.ok()
                            .header("Content-Disposition", "attachment; filename=links.yaml")
                            .header("Content-Type", "text/yaml; charset=utf-8")
                            .bodyValue(yaml);
                    } catch (Exception e) {
                        return ServerResponse.status(500).bodyValue("导出失败: " + e.getMessage());
                    }
                })
            );
    }
}
