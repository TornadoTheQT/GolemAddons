import { defineConfig } from "vitest/config";

// The Worker only uses standard web APIs (fetch, Request, Response, crypto.subtle, streams), which Node provides,
// so the tests run in plain Node with fake KV, cache and fetch instead of a full workerd runtime.
export default defineConfig({
	test: {
		include: ["test/**/*.test.ts"],
		environment: "node",
	},
});
