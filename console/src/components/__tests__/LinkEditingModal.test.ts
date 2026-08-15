import type { Link } from "@/api/generated";
import { QK_LINK_FEED_HIDDEN_ITEMS, QK_LINK_FEED_ITEMS } from "@/composables/use-link-feed";
import { QK_LINK_FEED_ITEM_SUMMARY } from "@/composables/use-link-feed-item-summary";
import { QK_LINK_FEED_UNREAD_SUMMARY } from "@/composables/use-link-feed-unread-summary";
import type { LinkFormState } from "@/types";
import { Dialog } from "@halo-dev/components";
import { VueQueryPlugin } from "@tanstack/vue-query";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createFeedTestQueryClient } from "../../composables/link-feed-test-utils";
import LinkEditingModal from "../LinkEditingModal.vue";

const componentMocks = vi.hoisted(() => ({
  deleteLink: vi.fn(),
  patchLink: vi.fn(),
  startInitialLinkFeedRefresh: vi.fn(),
  startLinkVerification: vi.fn(),
}));

vi.mock("@/api", () => ({
  linksCoreApiClient: {
    link: {
      deleteLink: componentMocks.deleteLink,
      patchLink: componentMocks.patchLink,
    },
  },
}));

vi.mock("@/composables/link-feed-initial-refresh", () => ({
  startInitialLinkFeedRefresh: componentMocks.startInitialLinkFeedRefresh,
}));

vi.mock("@/composables/link-verification", () => ({
  startLinkVerification: componentMocks.startLinkVerification,
}));

const dialogSpy = vi.spyOn(Dialog, "warning");

describe("LinkEditingModal RSS unsubscribe", () => {
  beforeEach(() => {
    componentMocks.patchLink.mockResolvedValue({ data: {} });
  });

  it("confirms an enabled-to-disabled transition before saving", async () => {
    const { wrapper } = mountModal(link(true));

    submitForm(wrapper, formState(false));
    await flushPromises();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    const options = dialogSpy.mock.calls[0][0] as {
      description?: string;
      onConfirm?: () => Promise<void> | void;
    };
    expect(options.description).toContain("收藏、稍后阅读和已隐藏");
    expect(options.description).toContain("永久删除");
    expect(componentMocks.patchLink).not.toHaveBeenCalled();

    await options.onConfirm?.();
    await flushPromises();

    expect(componentMocks.patchLink).toHaveBeenCalledTimes(1);
    expect(componentMocks.patchLink).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "link-a",
        jsonPatchInner: expect.arrayContaining([
          {
            op: "add",
            path: "/spec/rss",
            value: {
              enabled: false,
              feedUrls: ["https://example.com/feed.xml"],
            },
          },
        ]),
      }),
    );
  });

  it("does not save when unsubscribe confirmation is cancelled", async () => {
    const { wrapper } = mountModal(link(true));

    submitForm(wrapper, formState(false));
    await flushPromises();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    expect(componentMocks.patchLink).not.toHaveBeenCalled();
  });

  it("does not confirm edits to an already disabled subscription", async () => {
    const { wrapper } = mountModal(link(false));

    submitForm(wrapper, formState(false));
    await flushPromises();

    expect(dialogSpy).not.toHaveBeenCalled();
    expect(componentMocks.patchLink).toHaveBeenCalledTimes(1);
  });

  it("invalidates all RSS data queries after confirmed unsubscribe", async () => {
    const { wrapper, invalidateSpy } = mountModal(link(true));

    submitForm(wrapper, formState(false));
    await flushPromises();
    const options = dialogSpy.mock.calls[0][0] as { onConfirm?: () => Promise<void> | void };
    await options.onConfirm?.();
    await flushPromises();

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [QK_LINK_FEED_ITEMS] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [QK_LINK_FEED_HIDDEN_ITEMS] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [QK_LINK_FEED_UNREAD_SUMMARY] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [QK_LINK_FEED_ITEM_SUMMARY] });
  });
});

function mountModal(linkValue: Link) {
  const queryClient = createFeedTestQueryClient();
  const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");
  const wrapper = mount(LinkEditingModal, {
    props: { link: linkValue },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: {
        LinkForm: {
          name: "LinkForm",
          emits: ["submit"],
          template: "<div />",
        },
      },
    },
  });
  return { wrapper, invalidateSpy };
}

function submitForm(wrapper: VueWrapper, data: LinkFormState) {
  wrapper.findComponent({ name: "LinkForm" }).vm.$emit("submit", data);
}

function link(rssEnabled: boolean): Link {
  return {
    metadata: {
      name: "link-a",
      annotations: {},
    },
    spec: {
      url: "https://example.com",
      displayName: "Example",
      rss: {
        enabled: rssEnabled,
        feedUrls: ["https://example.com/feed.xml"],
      },
    },
  } as Link;
}

function formState(rssEnabled: boolean): LinkFormState {
  return {
    url: "https://example.com",
    displayName: "Example",
    rss: {
      enabled: rssEnabled,
      feedUrls: ["https://example.com/feed.xml"],
    },
  };
}
