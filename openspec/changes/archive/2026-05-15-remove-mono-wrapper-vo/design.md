## Context

`toLinkVo` and `toGroupVo` in `LinkPublicQueryServiceImpl` are simple factory methods (`LinkVo::from`, `LinkGroupVo::from`). They do no I/O, no blocking, and no reactive operations. Wrapping them in `Mono.fromSupplier` forces every caller into `flatMap`/`concatMap`, which schedules each conversion on the reactive scheduler unnecessarily.

## Goals / Non-Goals

**Goals:**
- Remove unnecessary `Mono` wrapping from synchronous conversion methods.
- Simplify call sites from `flatMap`/`concatMap` to `map`.

**Non-Goals:**
- Changing any conversion logic or data mapping.
- Modifying `LinkVo` or `LinkGroupVo` classes.
- Affecting any public API.

## Decisions

### 1. Use synchronous return types for synchronous work
- **Rationale**: The conversion is a pure function. Reactive wrappers should only be used when there is actual asynchronicity.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Callers outside this class may reference the `Mono` return type | `toLinkVo` and `toGroupVo` are `private`; only internal callers exist |
