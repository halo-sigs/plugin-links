import { linkApplicationCommentActionsView } from "@/utils/link-application-comment-actions";
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { defineComponent, h } from "vue";
import LinkApplicationOriginCommentActions from "../LinkApplicationOriginCommentActions.vue";

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

function buildView(overrides: Partial<Parameters<typeof linkApplicationCommentActionsView>[0]> = {}) {
  return linkApplicationCommentActionsView({
    originType: "COMMENT",
    applicationStatus: "PENDING",
    commentState: "ready",
    approved: false,
    hidden: false,
    ...overrides,
  });
}

function mountActions(options: {
  view?: ReturnType<typeof buildView>;
  canManageComments?: boolean;
  approve?: boolean;
  replyText?: string;
}) {
  const wrapper = mount(LinkApplicationOriginCommentActions, {
    props: {
      view: options.view ?? buildView(),
      canManageComments: options.canManageComments ?? true,
      approve: options.approve ?? false,
      replyText: options.replyText ?? "",
      "onUpdate:approve": (value: boolean) => wrapper.setProps({ approve: value }),
      "onUpdate:replyText": (value: string) => wrapper.setProps({ replyText: value }),
    },
    global: {
      stubs: {
        FormKit: FormKitStub,
      },
    },
  });
  return { wrapper };
}

describe("LinkApplicationOriginCommentActions", () => {
  it("renders nothing for form-origin or rejected applications", () => {
    for (const view of [buildView({ originType: "FORM" }), buildView({ applicationStatus: "REJECTED" })]) {
      const { wrapper } = mountActions({ view });
      expect(wrapper.find("section").exists()).toBe(false);
    }
  });

  it("explains and disables controls without the required permissions", async () => {
    const { wrapper } = mountActions({ canManageComments: false });

    expect(wrapper.text()).toContain("缺少友链管理");
    expect(wrapper.text()).toContain("评论管理");
    expect(wrapper.find("input[type=checkbox]").attributes("disabled")).toBeDefined();
    expect(wrapper.find("textarea").attributes("disabled")).toBeDefined();

    await wrapper.find("input[type=checkbox]").setValue(true);
    expect(wrapper.emitted("update:approve")).toBeUndefined();
  });

  it("warns that a hidden comment stays hidden", () => {
    const { wrapper } = mountActions({ view: buildView({ hidden: true }) });
    expect(wrapper.text()).toContain("评论处于隐藏状态");
    expect(wrapper.text()).toContain("不会改变其隐藏状态");
  });

  it("explains a deleted source without blocking link review", () => {
    const { wrapper } = mountActions({ view: buildView({ commentState: "deleted" }) });
    expect(wrapper.text()).toContain("来源评论不可用");
    expect(wrapper.text()).toContain("不影响链接审核");
    expect(wrapper.find("input[type=checkbox]").exists()).toBe(false);
    expect(wrapper.find("textarea").exists()).toBe(false);
  });

  it("does not offer approval for an already approved comment", () => {
    const { wrapper } = mountActions({ view: buildView({ approved: true }) });
    expect(wrapper.find("input[type=checkbox]").exists()).toBe(false);
    expect(wrapper.find("textarea").exists()).toBe(true);
  });

  it("disables the approval checkbox while a reply is entered", async () => {
    const { wrapper } = mountActions({});
    const checkbox = wrapper.find("input[type=checkbox]");
    expect(checkbox.attributes("disabled")).toBeUndefined();

    await wrapper.find("textarea").setValue("收到，稍后处理");
    expect(wrapper.find("input[type=checkbox]").attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("回复后评论将自动通过");
  });

  it("summarizes the selected link and comment actions", async () => {
    const { wrapper } = mountActions({ approve: true });
    expect(wrapper.text()).toContain("通过申请，并通过来源评论");

    await wrapper.find("input[type=checkbox]").setValue(false);
    expect(wrapper.text()).toContain("仅通过申请，不处理来源评论");
  });

  it("never promises comment actions in the summary without the required permissions", () => {
    const { wrapper } = mountActions({ canManageComments: false, approve: true, replyText: "收到" });
    expect(wrapper.text()).toContain("仅通过申请，不处理来源评论");
    expect(wrapper.text()).not.toContain("并通过来源评论");
    expect(wrapper.text()).not.toContain("并回复来源评论");
  });

  it("emits the selected intent from the standalone button on approved applications", async () => {
    const { wrapper } = mountActions({
      view: buildView({ applicationStatus: "APPROVED" }),
      approve: true,
    });

    await wrapper.find("button").trigger("click");

    const submitted = wrapper.emitted("submit");
    expect(submitted).toHaveLength(1);
    expect(submitted?.[0]).toEqual([{ approve: true, replyText: "" }]);
  });

  it("keeps the standalone button disabled until an action is selected", async () => {
    const { wrapper } = mountActions({
      view: buildView({ applicationStatus: "APPROVED", approved: true }),
    });

    const button = wrapper.find("button");
    expect(button.attributes("disabled")).toBeDefined();
    await button.trigger("click");
    expect(wrapper.emitted("submit")).toBeUndefined();

    await wrapper.find("textarea").setValue("欢迎交换友链");
    await wrapper.find("button").trigger("click");
    expect(wrapper.emitted("submit")?.[0]).toEqual([{ approve: false, replyText: "欢迎交换友链" }]);
  });
});
