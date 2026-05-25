## Context

RSS refresh currently stores `ETag` and `Last-Modified` values on each
configured feed URL under `Link.status.rss.feeds[]`. The refresh path sends
those validators when the embedded item cache has at least one cached item for
that link/feed URL, and skips them only when the cache is empty.

That protects fresh installs and recovered empty caches, but it does not
protect long-lived caches from upstream servers that keep returning incorrect
`304 Not Modified` responses. Because a `304` refresh is still a successful
refresh, `lastSuccessAt` is not a reliable age marker for the validators
themselves.

## Goals / Non-Goals

**Goals:**

- Force periodic full feed fetches even when cached validators exist.
- Track validator freshness per configured feed URL.
- Preserve efficient conditional requests while validators are fresh.
- Keep stale-validator handling independent from item cache retention.
- Cover existing status records that do not yet have validator freshness data.

**Non-Goals:**

- Add JSON Feed or RSS-in-JSON support.
- Add user-configurable refresh intervals or Console settings.
- Change the item cache identity algorithm.
- Change the RSS reader UI.

## Decisions

### Store validator freshness in per-feed status

Add a nullable `validatorUpdatedAt` timestamp to `Link.RssFeedStatus`. It
records when the current `ETag` / `Last-Modified` values were last obtained
from a real `200 OK` response.

Alternative considered: infer freshness from `lastSuccessAt`. This does not
work because `304 Not Modified` is a successful refresh and would keep
refreshing the timestamp without ever fetching a body.

### Use an eight-day forced-full-fetch window

If `validatorUpdatedAt` is missing or older than eight days, the refresh should
fetch without `If-None-Match` or `If-Modified-Since`. Eight days is long enough
to preserve bandwidth benefits for healthy feeds while ensuring a weekly-ish
real fetch for feeds behind broken validators.

Alternative considered: force a full fetch on every scheduled refresh. That is
simple, but it gives up conditional-request efficiency and is unnecessarily
noisy for well-behaved feeds.

### Refresh validator age only after a 200 response

When a feed returns `200 OK`, update `etag`, `lastModified`, and
`validatorUpdatedAt` from the response. When it returns `304 Not Modified`,
keep the previous validators and previous `validatorUpdatedAt`.

Alternative considered: refresh `validatorUpdatedAt` on every successful
refresh. That would make a broken server's repeated `304` responses perpetually
trusted.

### Treat missing freshness as stale

Existing `Link` resources may already have `etag` / `lastModified` values but
no `validatorUpdatedAt`. Those records should skip conditional headers once,
perform a full fetch, and then store freshness metadata if the response returns
validators.

Alternative considered: initialize missing freshness from `lastSuccessAt`. That
would preserve conditional behavior for old records, but it can also import the
same bad age ambiguity that this change is designed to remove.

### Do not send obviously invalid Last-Modified values

If the stored `Last-Modified` value contains `2038`, skip
`If-Modified-Since` for that refresh. This keeps one known class of bad server
timestamp from influencing refresh behavior while still allowing `ETag` to be
sent when it is otherwise fresh.

## Risks / Trade-offs

- [Risk] The first refresh after upgrade may use more bandwidth for feeds with
  old validators but no freshness timestamp. -> Mitigation: this happens once
  per feed URL and only fetches the bounded feed document already allowed by
  the SSRF/response-size guard.
- [Risk] A feed that returns no validators will continue doing full fetches.
  -> Mitigation: conditional requests are impossible without usable validators;
  the existing content hash and item upsert behavior still avoid duplicate
  cached items.
- [Risk] The eight-day window may not fit every site. -> Mitigation: keep it as
  an internal constant for now; only introduce settings if users report a real
  need.

## Migration Plan

No explicit migration job is required. Existing statuses without
`validatorUpdatedAt` are treated as stale during the next refresh and will be
updated naturally after a `200 OK` response that includes `ETag` or
`Last-Modified`.

Rollback is safe: older code ignores the extra status field and can continue
using stored validators as before.
