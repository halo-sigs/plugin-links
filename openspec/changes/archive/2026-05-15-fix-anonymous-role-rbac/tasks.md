## 1. Fix RBAC Configuration

- [x] 1.1 Update `roleTemplate.yaml` anonymous role: replace invalid `link-random`/`link-count` resources with `links/random` and `links/count` (subresource syntax)
- [x] 1.2 Build plugin and verify `roleTemplate.yaml` is included in the JAR

## 2. Verification

- [ ] 2.1 Start Halo with the plugin and verify anonymous requests to `/apis/api.link.halo.run/v1alpha1/links/-/count` return 200
- [ ] 2.2 Verify anonymous requests to `/apis/api.link.halo.run/v1alpha1/links/-/random` return 200
