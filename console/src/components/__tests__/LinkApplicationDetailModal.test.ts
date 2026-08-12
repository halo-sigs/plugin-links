import type { LinkApplication } from "@/api/generated";
import { Dialog, Toast } from "@halo-dev/components";
import { beforeEach, describe, expect, it, rstest } from "@rstest/core";
import { VueQueryPlugin } from "@tanstack/vue-query";
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { createFeedTestQueryClient } from "../../composables/link-feed-test-utils";
import LinkApplicationDetailModal from "../LinkApplicationDetailModal.vue";

const apiMocks = rstest.hoisted(() => ({
  approveLinkApplication: rstest.fn(),
  getLinkApplicationOriginComment: rstest.fn(),
  queryLinkGroups: rstest.fn(),
  patchComment: rstest.fn(),
  createReply: rstest.fn(),
  listReplies: rstest.fn(),
  permissionHas: rstest.fn(),
  modalClose: rstest.fn(),
}));

rstest.mock("@/api", () => ({
  linksPublicApiClient: {
    linkGroup: { queryLinkGroups: apiMocks.queryLinkGroups },
  },
  linksConsoleApiClient: {
    application: {
      approveLinkApplication: apiMocks.approveLinkApplication,
      getLinkApplicationOriginComment: apiMocks.getLinkApplicationOriginComment,
    },
  },
  linksCoreApiClient: {},
  linkAiApiClient: {},
}));

rstest.mock("@halo-dev/api-client", () => ({
  axiosInstance: {},
  coreApiClient: {
    content: {
      comment: { patchComment: apiMocks.patchComment },
    },
  },
  consoleApiClient: {
    content: {
      comment: { createReply: apiMocks.createReply },
      reply: { listReplies: apiMocks.listReplies },
    },
  },
}));

rstest.mock("@halo-dev/ui-shared", () => ({
  utils: {
    permission: { has: apiMocks.permissionHas },
    date: { format: (value: string) => value },
  },
}));

const VModalStub = defineComponent({
  name: "VModal",
  setup(_, { slots, expose }) {
    expose({ close: apiMocks.modalClose });
    return () => h("div", [slots.default?.(), slots.footer?.()]);
  },
});

const FormKitStub = defineComponent({
  name: "FormKit",
  props: ["type", "modelValue", "disabled", "placeholder", "help", "label"],
  emits: ["update:modelValue"],
  setup(props, { emit }) {
    return () => {
      const input =
        props.type === "checkbox"
          ? h("input", {
              type: "checkbox",
              checked: props.modelValue,
              disabled: props.disabled,
              onChange: (event: Event) => emit("update:modelValue", (event.target as HTMLInputElement).checked),
            })
          : h("textarea", {
              value: props.modelValue,
              disabled: props.disabled,
              placeholder: props.placeholder,
              onInput: (event: Event) => emit("update:modelValue", (event.target as HTMLTextAreaElement).value),
            });
      return h("div", [input, props.help ? h("span", props.help) : null]);
    };
  },
});

const toastSuccessSpy = rstest.spyOn(Toast, "success").mockImplementation(() => undefined);
const dialogWarningSpy = rstest.spyOn(Dialog, "warning").mockImplementation(() => undefined);

describe("LinkApplicationDetailModal origin comment orchestration", () => {
  beforeEach(() => {
    apiMocks.permissionHas.mockReturnValue(true);
    apiMocks.queryLinkGroups.mockResolvedValue({ data: [] });
    apiMocks.getLinkApplicationOriginComment.mockResolvedValue({
      data: { name: "comment-a", raw: "互链吗？", approved: false, hidden: false },
    });
  });

  it("approves the link before approving the selected source comment", async () => {
    const order: string[] = [];
    apiMocks.approveLinkApplication.mockImplementation(async () => {
      order.push("link");
      return { data: {} };
    });
    apiMocks.patchComment.mockImplementation(async () => {
      order.push("comment");
      return { data: {} };
    });
    const { wrapper } = mountModal();

    await clickButton(wrapper, "继续审批");

    expect(order).toEqual(["link", "comment"]);
    expect(apiMocks.patchComment).toHaveBeenCalledTimes(1);
    expect(toastSuccessSpy).toHaveBeenCalledWith("已通过申请，并通过来源评论");
    expect(apiMocks.modalClose).toHaveBeenCalledTimes(1);
  });

  it("prevents every comment request when link approval fails", async () => {
    apiMocks.approveLinkApplication.mockRejectedValue({ isAxiosError: true, response: { status: 400 } });
    const { wrapper } = mountModal();

    await clickButton(wrapper, "继续审批");

    expect(apiMocks.patchComment).not.toHaveBeenCalled();
    expect(apiMocks.createReply).not.toHaveBeenCalled();
    expect(apiMocks.modalClose).not.toHaveBeenCalled();
  });

  it("keeps the modal open in approved mode on comment failure and retries only the comment", async () => {
    apiMocks.approveLinkApplication.mockResolvedValue({ data: {} });
    apiMocks.patchComment.mockRejectedValueOnce({ isAxiosError: true, response: { status: 409 } });
    const { wrapper } = mountModal();

    await clickButton(wrapper, "继续审批");

    expect(apiMocks.modalClose).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("链接已通过");
    expect(wrapper.text()).toContain("通过来源评论失败");
    expect(wrapper.text()).toContain("链接已通过且不受影响");
    expect(wrapper.find("button").text()).not.toContain("继续审批");

    apiMocks.patchComment.mockResolvedValue({ data: {} });
    await clickButton(wrapper, "处理评论");

    expect(apiMocks.approveLinkApplication).toHaveBeenCalledTimes(1);
    expect(apiMocks.patchComment).toHaveBeenCalledTimes(2);
    expect(toastSuccessSpy).toHaveBeenCalledWith("已通过申请，并通过来源评论");
    expect(apiMocks.modalClose).toHaveBeenCalledTimes(1);
  });

  it("disables comment controls after Halo rejects a comment mutation with 403", async () => {
    apiMocks.approveLinkApplication.mockResolvedValue({ data: {} });
    apiMocks.patchComment.mockRejectedValueOnce({ isAxiosError: true, response: { status: 403 } });
    const { wrapper } = mountModal();

    await clickButton(wrapper, "继续审批");

    expect(apiMocks.modalClose).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("链接已通过");
    expect(wrapper.text()).toContain("缺少友链管理");

    const retryButton = wrapper.findAll("button").find((item) => item.text() === "处理评论");
    expect(retryButton?.attributes("disabled")).toBeDefined();
    await retryButton?.trigger("click");
    await flushPromises();
    expect(apiMocks.patchComment).toHaveBeenCalledTimes(1);
  });

  it("submits a reply after link approval without a preceding approval patch", async () => {
    apiMocks.approveLinkApplication.mockResolvedValue({ data: {} });
    apiMocks.createReply.mockResolvedValue({ data: {} });
    const { wrapper } = mountModal();

    await flushPromises();
    await wrapper.find("textarea").setValue("欢迎交换友链");
    await clickButton(wrapper, "继续审批");

    expect(apiMocks.patchComment).not.toHaveBeenCalled();
    expect(apiMocks.createReply).toHaveBeenCalledTimes(1);
    expect(apiMocks.createReply.mock.calls[0][0].replyRequest.raw).toBe("欢迎交换友链");
    expect(toastSuccessSpy).toHaveBeenCalledWith("已通过申请，并回复来源评论");
    expect(apiMocks.modalClose).toHaveBeenCalledTimes(1);
  });

  it("requires confirmation before resubmitting an indeterminate reply", async () => {
    apiMocks.approveLinkApplication.mockResolvedValue({ data: {} });
    apiMocks.createReply.mockRejectedValueOnce({ isAxiosError: true, response: undefined });
    apiMocks.listReplies.mockResolvedValue({ data: { items: [] } });
    const { wrapper } = mountModal();

    await flushPromises();
    await wrapper.find("textarea").setValue("欢迎交换友链");
    await clickButton(wrapper, "继续审批");

    expect(apiMocks.createReply).toHaveBeenCalledTimes(1);
    expect(apiMocks.modalClose).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("回复结果未知");
    expect(wrapper.text()).toContain("不会自动重试");

    await clickButton(wrapper, "处理评论");

    expect(dialogWarningSpy).toHaveBeenCalledTimes(1);
    expect(apiMocks.createReply).toHaveBeenCalledTimes(1);

    const options = dialogWarningSpy.mock.calls[0][0] as { onConfirm?: () => void };
    apiMocks.createReply.mockResolvedValue({ data: {} });
    options.onConfirm?.();
    await flushPromises();

    expect(apiMocks.createReply).toHaveBeenCalledTimes(2);
    expect(apiMocks.modalClose).toHaveBeenCalledTimes(1);
  });

  it("never offers comment actions without the required permissions", async () => {
    apiMocks.permissionHas.mockReturnValue(false);
    apiMocks.approveLinkApplication.mockResolvedValue({ data: {} });
    const { wrapper } = mountModal();

    expect(wrapper.text()).toContain("缺少友链管理");

    await clickButton(wrapper, "继续审批");

    expect(apiMocks.patchComment).not.toHaveBeenCalled();
    expect(apiMocks.createReply).not.toHaveBeenCalled();
    expect(toastSuccessSpy).toHaveBeenCalledWith("已通过申请");
    expect(apiMocks.modalClose).toHaveBeenCalledTimes(1);
  });

  it("approves a form-origin application without reporting a comment failure", async () => {
    apiMocks.approveLinkApplication.mockResolvedValue({ data: {} });
    const { wrapper } = mountModal(formApplication());

    await clickButton(wrapper, "继续审批");

    expect(apiMocks.getLinkApplicationOriginComment).not.toHaveBeenCalled();
    expect(apiMocks.patchComment).not.toHaveBeenCalled();
    expect(apiMocks.createReply).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain("失败");
    expect(toastSuccessSpy).toHaveBeenCalledWith("已通过申请");
    expect(apiMocks.modalClose).toHaveBeenCalledTimes(1);
  });

  it("disables link approval while the source comment state is still loading", async () => {
    apiMocks.getLinkApplicationOriginComment.mockReturnValue(new Promise(() => {}));
    const { wrapper } = mountModal();

    await flushPromises();
    const button = wrapper.findAll("button").find((item) => item.text() === "继续审批");
    expect(button?.attributes("disabled")).toBeDefined();
  });
});

function mountModal(application: LinkApplication = approvingCommentApplication()) {
  const queryClient = createFeedTestQueryClient();
  const wrapper = mount(LinkApplicationDetailModal, {
    props: { application },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: {
        Modal: VModalStub,
        FormKit: FormKitStub,
        LinkApplicationOriginDetails: { template: "<div />" },
        LinkApplicationSourceBadge: { template: "<div />" },
        RouterLink: { template: "<a><slot /></a>" },
      },
    },
  });
  return { wrapper };
}

async function clickButton(wrapper: ReturnType<typeof mount>, label: string) {
  await flushPromises();
  const button = wrapper.findAll("button").find((item) => item.text() === label);
  expect(button, `button "${label}"`).toBeDefined();
  await button?.trigger("click");
  await flushPromises();
}

function approvingCommentApplication(): LinkApplication {
  return {
    apiVersion: "core.halo.run/v1alpha1",
    kind: "LinkApplication",
    metadata: { name: "app-a" },
    spec: {
      displayName: "Example",
      url: "https://example.com",
      status: "APPROVING",
      origin: { type: "COMMENT", comment: { name: "comment-a" } },
    },
  } as LinkApplication;
}

function formApplication(): LinkApplication {
  return {
    apiVersion: "core.halo.run/v1alpha1",
    kind: "LinkApplication",
    metadata: { name: "app-form" },
    spec: {
      displayName: "Form Example",
      url: "https://form.example.com",
      status: "APPROVING",
      origin: { type: "FORM" },
    },
  } as LinkApplication;
}
