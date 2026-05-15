## Why

`LinkPublicQueryServiceImpl.count()` currently fetches a single-page result via `client.listBy(..., PageRequestImpl.ofSize(1))` only to read `getTotal()`. This performs unnecessary query parsing and result materialization. The same `ReactiveExtensionClient` already provides `countBy()` which is used elsewhere in the same class (`random()`). Switching to `countBy()` eliminates the extra overhead.

## What Changes

- Replace `client.listBy(Link.class, listOptions, PageRequestImpl.ofSize(1))` with `client.countBy(Link.class, listOptions)` in `LinkPublicQueryServiceImpl.count()`
- Map the resulting `Long` to `Integer` via `map(Long::intValue)` instead of `flatMap(links -> Mono.just((int)links.getTotal()))`

## Capabilities

### New Capabilities
- *(none)*

### Modified Capabilities
- *(none — this is a pure implementation optimization with no spec-level behavior change)*

## Impact

- **Backend**: Single-line change in `LinkPublicQueryServiceImpl.java`
- **Frontend**: None
- **API**: None — response shape and semantics are identical
- **Dependencies**: None
