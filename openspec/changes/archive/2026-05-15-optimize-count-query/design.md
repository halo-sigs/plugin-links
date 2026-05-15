## Context

`LinkPublicQueryServiceImpl.count()` uses `client.listBy(Link.class, listOptions, PageRequestImpl.ofSize(1))` to obtain the total count of links. This creates a full `ListResult<Link>` object with an empty item list, then extracts `getTotal()`. The same class already uses `client.countBy(Link.class, new ListOptions())` in the `random()` method, proving that a direct count API exists and works.

## Goals / Non-Goals

**Goals:**
- Replace the inefficient list-then-extract-total pattern with a direct count query.
- Simplify the reactive pipeline from `flatMap` + `Mono.just` to a single `map`.

**Non-Goals:**
- Changing any public API contract or response shape.
- Modifying the filter criteria (still exclude deleted links via `metadata.deletionTimestamp isNull`).
- Adding new dependencies.

## Decisions

### 1. Use `countBy()` instead of `listBy(..., ofSize(1))`
- **Rationale**: `countBy()` is the purpose-built API for counting. It avoids constructing a `ListResult`, populating an empty item list, and parsing page metadata.
- **Alternative considered**: Keep the current approach — rejected because it is demonstrably less efficient.

### 2. Use `map(Long::intValue)` for the type conversion
- **Rationale**: `countBy()` returns `Mono<Long>`. The public method signature requires `Mono<Integer>`. `map(Long::intValue)` is a simple, safe conversion for counts that are well within the `Integer` range for a link collection.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| `Long` to `int` truncation on extremely large counts | Not a realistic concern for a friend-link plugin; counts will not exceed `Integer.MAX_VALUE` |
| `countBy()` behaves subtly differently from `listBy(...).getTotal()` | Both use the same underlying index query with identical `ListOptions`; semantics are the same |
