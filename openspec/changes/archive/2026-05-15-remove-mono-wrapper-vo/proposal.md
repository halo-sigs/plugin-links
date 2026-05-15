## Why

`LinkPublicQueryServiceImpl` wraps two pure, synchronous, side-effect-free conversion methods (`toLinkVo` and `toGroupVo`) in `Mono.fromSupplier`. This adds unnecessary reactive scheduling overhead and forces callers to use `flatMap`/`concatMap` where a simple `map` would suffice.

## What Changes

- Change `toLinkVo(Link)` return type from `Mono<LinkVo>` to `LinkVo`
- Change `toGroupVo(LinkGroup)` return type from `Mono<LinkGroupVo>` to `LinkGroupVo`
- Update all call sites from `flatMap(this::toLinkVo)` / `concatMap(this::toGroupVo)` to `map(this::toLinkVo)` / `map(this::toGroupVo)`
- Remove unused `Mono` import if it becomes unnecessary

## Capabilities

### New Capabilities
- *(none)*

### Modified Capabilities
- *(none — pure internal refactoring with no spec-level behavior change)*

## Impact

- **Backend**: `LinkPublicQueryServiceImpl.java` only
- **Frontend**: None
- **API**: None
- **Dependencies**: None
