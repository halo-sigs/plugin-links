# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## Project Overview

This is **plugin-links**, a Halo 2.0 plugin for managing links (friend links / 友情链接). It provides a Console admin UI and a theme-facing `/links` page route.

The project is a **dual-stack Halo plugin**: a Java 21 Spring WebFlux backend plus a Vue 3 frontend bundled together via Gradle. It targets Halo `>=2.22.5`.

## Primary Development Workflow

Start the Halo plugin development environment first:

```bash
./gradlew haloServer
```

This starts a local Halo instance with the plugin loaded directly from source. Use
`http://localhost:8090/console/links` to test the admin UI. The default credentials are
**admin / admin**.

When changing frontend code and the change must be reflected immediately, run the frontend
dev server in a separate terminal:

```bash
cd console
pnpm dev
```

When changing backend APIs, request/response DTOs, or extension/entity fields that the
frontend consumes, regenerate the TypeScript API client before updating frontend code:

```bash
./gradlew generateApiClient
```

Frontend code must use the generated client from `console/src/api/generated/` to call backend
APIs. Do not hand-write duplicate API wrappers for generated endpoints.

## Secondary Commands

Most tasks go through Gradle. The frontend is built as part of the Gradle lifecycle.

```bash
# Install frontend dependencies (runs pnpm install in console/)
./gradlew pnpmInstall

# Build the entire plugin (frontend + backend)
./gradlew build

# Run backend tests
./gradlew test
```

Frontend-only commands (run from `console/`):

```bash
cd console

# Production build (outputs to src/main/resources/console)
pnpm build

# Type check
pnpm type-check

# Lint and fix
pnpm lint

# Format with Prettier
pnpm prettier
```

## Project Structure

```
├── build.gradle                  # Main build config. Java 21, Halo plugin devtools, node plugin
├── settings.gradle
├── console/                      # Vue 3 frontend (Rsbuild, pnpm, UnoCSS)
│   ├── src/
│   │   ├── index.ts              # Plugin entry point: registers routes, comment subject ref
│   │   ├── views/LinkList.vue    # Main admin page
│   │   ├── components/           # GroupList, LinkEditingModal, GroupEditingModal
│   │   ├── composables/use-link.ts
│   │   └── api/generated/        # Auto-generated OpenAPI client (do not edit manually)
│   ├── package.json
│   └── rsbuild.config.mjs        # Uses @halo-dev/ui-plugin-bundler-kit
└── src/main/
    ├── java/run/halo/links/
    │   ├── LinkPlugin.java       # Plugin lifecycle: registers Link & LinkGroup schemes/indexes
    │   ├── Link.java             # Extension model: LinkSpec {url, displayName, logo, description, priority, groupName}
    │   ├── LinkGroup.java        # Extension model: LinkGroupSpec {displayName, priority, links (deprecated)}
    │   ├── LinkRouter.java       # WebFlux router: /links page route + plugin API endpoints
    │   ├── LinkRequest.java      # External link detail fetching (used by /link-detail endpoint)
    │   ├── LinkDetailDTO.java
    │   ├── LinkCommentSubject.java
    │   ├── finders/
    │   │   ├── LinkFinder.java   # Theme Finder API interface
    │   │   └── impl/LinkFinderImpl.java  # @Finder("linkFinder") implementation
    │   └── vo/
    │       ├── LinkVo.java
    │       └── LinkGroupVo.java
    └── resources/
        ├── plugin.yaml           # Plugin manifest: requires ">=2.22.5"
        ├── extensions/
        │   ├── settings.yaml     # Plugin settings schema
        │   └── roleTemplate.yaml # RBAC templates
        └── logo.svg
```

## Architecture

### Backend

- **Extension Model**: `Link` and `LinkGroup` are Halo extensions (GVK `core.halo.run/v1alpha1`). They extend `AbstractExtension` and use `@GVK` annotations. Data is stored in Halo's extension store, not a separate database.
- **Indexing**: `LinkPlugin.start()` registers index specs on `spec.displayName`, `spec.description`, `spec.url`, `spec.groupName`, and `spec.priority` for both Link and LinkGroup. These indexes power the query APIs.
- **Routing**: `LinkRouter` uses Spring WebFlux functional routing (`RouterFunction`). It defines:
  - `/links` — Thymeleaf template route for the theme
  - `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/**` — plugin-specific REST API
- **Finder API**: `LinkFinderImpl` is annotated with `@Finder("linkFinder")` and exposes `listBy(group)` and `groupBy()` for theme templates. The Finder API supports an implicit "ungrouped" group for links without a `groupName`.
- **Reactive**: The backend uses Project Reactor (`Mono`, `Flux`) throughout. All Halo extension client operations are reactive.

### Frontend

- **Framework**: Vue 3 (Composition API), TypeScript, Rsbuild
- **Styling**: UnoCSS (atomic CSS via `:uno:` prefix in class names), Halo Dev Components
- **State**: Vue Query (`@tanstack/vue-query`) for server state; no global client-side store
- **Icons**: `unplugin-icons` with Iconify (`~icons/ri/...`)
- **API Client**: Auto-generated from the backend OpenAPI spec into `console/src/api/generated/`.
  The `haloPlugin.openApi.generator` block in `build.gradle` controls output. Follow the API
  client rule in `Primary Development Workflow` after backend API or entity changes. If new
  endpoints use a different path prefix (e.g., `/apis/console.api.link.halo.run/v1alpha1/**`),
  add the prefix to `haloPlugin.openApi.groupingRules.pathsToMatch` in `build.gradle` first,
  otherwise the generator will skip them.
- **Plugin Integration**: `console/src/index.ts` exports a plugin definition using `@halo-dev/ui-shared`'s `definePlugin`. It registers a route under `content` group and a `comment:subject-ref:create` extension point.

### Build Integration

Gradle's `buildFrontend` task (pnpm build) runs before `compileJava`. In production, frontend assets are emitted to `src/main/resources/console`; in dev mode they go to `build/resources/main/console`.

## Code Style

- `.editorconfig` is extensive and IntelliJ-specific. Key defaults: Java line length 100, 4-space indent, end-of-line braces, use single-class imports.
- Frontend uses Prettier with 120 print width, 2-space indent, LF line endings.
- The project currently has **no unit tests**.
