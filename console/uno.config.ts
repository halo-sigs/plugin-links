import { defineConfig, presetWind3, transformerCompileClass } from "unocss";

export default defineConfig({
  presets: [presetWind3()],
  theme: {
    breakpoints: {
      sm: "640px",
      md: "768px",
      lg: "1024px",
      xl: "1280px",
      "2xl": "1536px",
      "3xl": "1920px",
      "4xl": "2400px",
      "5xl": "3000px",
    },
  },
  transformers: [transformerCompileClass()],
});
