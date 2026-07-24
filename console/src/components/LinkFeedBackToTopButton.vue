<script lang="ts" setup>
import { onMounted, onUnmounted, shallowRef } from "vue";
import ArrowUpFillIcon from "~icons/mingcute/arrow-up-fill?width=unset&height=unset";

const VISIBLE_SCROLL_TOP = 360;

const isVisible = shallowRef(false);
const scrollTarget = shallowRef<Element | null>(null);
let animationFrameId: number | undefined;

function isDocumentScrollTarget(target: Element | null) {
  return (
    !target || target === document.scrollingElement || target === document.documentElement || target === document.body
  );
}

function documentScrollTop() {
  return window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
}

function eventScrollTarget(event?: Event) {
  const target = event?.target;
  if (target instanceof Document) {
    return document.scrollingElement;
  }
  if (target instanceof Element) {
    return target;
  }
  return document.scrollingElement;
}

function scrollTopOf(target: Element | null) {
  if (!target || isDocumentScrollTarget(target)) {
    return documentScrollTop();
  }
  return target.scrollTop;
}

function updateScrollState(event?: Event) {
  const candidate = eventScrollTarget(event);
  const candidateScrollTop = scrollTopOf(candidate);
  const activeScrollTop = Math.max(candidateScrollTop, scrollTopOf(scrollTarget.value), documentScrollTop());

  if (candidate && candidateScrollTop > 0) {
    scrollTarget.value = candidate;
  }

  isVisible.value = activeScrollTop > VISIBLE_SCROLL_TOP;
}

function handleScroll(event?: Event) {
  if (animationFrameId) {
    return;
  }

  animationFrameId = window.requestAnimationFrame(() => {
    animationFrameId = undefined;
    updateScrollState(event);
  });
}

function preferredScrollBehavior(): ScrollBehavior {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
}

function scrollToTop() {
  const behavior = preferredScrollBehavior();
  const target = scrollTarget.value;

  if (target && !isDocumentScrollTarget(target)) {
    target.scrollTo({ top: 0, behavior });
  }

  window.scrollTo({ top: 0, behavior });
}

onMounted(() => {
  updateScrollState();
  window.addEventListener("scroll", handleScroll, { passive: true });
  window.addEventListener("resize", handleScroll, { passive: true });
  document.addEventListener("scroll", handleScroll, { capture: true, passive: true });
});

onUnmounted(() => {
  window.removeEventListener("scroll", handleScroll);
  window.removeEventListener("resize", handleScroll);
  document.removeEventListener("scroll", handleScroll, { capture: true });

  if (animationFrameId) {
    window.cancelAnimationFrame(animationFrameId);
  }
});
</script>

<template>
  <Transition name="feed-back-to-top">
    <button
      v-if="isVisible"
      type="button"
      class=":uno: feed-back-to-top"
      aria-label="返回顶部"
      title="返回顶部"
      @click="scrollToTop"
    >
      <ArrowUpFillIcon class=":uno: feed-back-to-top__icon" aria-hidden="true" />
    </button>
  </Transition>
</template>

<style scoped>
.feed-back-to-top {
  position: fixed;
  right: calc(1rem + env(safe-area-inset-right));
  bottom: calc(1rem + env(safe-area-inset-bottom));
  z-index: 40;
  display: inline-flex;
  width: 2.5rem;
  height: 2.5rem;
  align-items: center;
  justify-content: center;
  border: 1px solid rgb(228 228 231);
  border-radius: 999px;
  background: rgb(255 255 255 / 0.92);
  box-shadow:
    0 8px 22px rgb(15 23 42 / 0.1),
    0 1px 2px rgb(15 23 42 / 0.06);
  color: rgb(63 63 70);
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
  backdrop-filter: blur(12px);
}

.feed-back-to-top:hover {
  border-color: rgb(212 212 216);
  background: rgb(255 255 255);
  box-shadow:
    0 10px 24px rgb(15 23 42 / 0.12),
    0 1px 2px rgb(15 23 42 / 0.08);
  color: rgb(24 24 27);
  transform: translateY(-1px);
}

.feed-back-to-top:active {
  transform: translateY(0);
}

.feed-back-to-top:focus-visible {
  outline: 3px solid rgb(24 24 27 / 0.16);
  outline-offset: 3px;
}

.feed-back-to-top__icon {
  width: 1.125rem;
  height: 1.125rem;
}

.feed-back-to-top-enter-active,
.feed-back-to-top-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.feed-back-to-top-enter-from,
.feed-back-to-top-leave-to {
  opacity: 0;
  transform: translateY(0.5rem) scale(0.92);
}

@media (min-width: 1024px) {
  .feed-back-to-top {
    right: calc(1.5rem + env(safe-area-inset-right));
    bottom: calc(1.5rem + env(safe-area-inset-bottom));
  }
}

@media (prefers-reduced-motion: reduce) {
  .feed-back-to-top,
  .feed-back-to-top-enter-active,
  .feed-back-to-top-leave-active {
    transition: none;
  }
}
</style>
