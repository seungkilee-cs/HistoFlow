import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import eslint from "@nabla/vite-plugin-eslint";

export default defineConfig({
  plugins: [react(), eslint()],
  server: {
    port: 3000,
    open: true,
  },
  build: {
    outDir: "build",
  },
});
