import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
// import eslint from "@nabla/vite-plugin-eslint";

export default defineConfig({
  plugins: [react()], // Disabled eslint() plugin - causes circular structure error with react-hooks
  server: {
    port: 3000,
    open: true,
  },
  build: {
    outDir: "build",
  },
});
