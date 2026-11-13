import assert from "node:assert/strict";
import { test } from "node:test";
import worker from "./worker.mjs";

const request = (body) => new Request("https://example.invalid/amazon", { method: "POST", body });

test("rejects malformed requests and missing credentials before reaching Amazon", async () => {
  assert.equal((await worker.fetch(request("{}"), {})).status, 400);
  assert.equal((await worker.fetch(request("x".repeat(4097)), {})).status, 400);
  assert.equal((await worker.fetch(request('{"userId":"test","receiptId":"receipt"}'), {})).status, 503);
});

test("only grants the matching entitlement for the selected receipt environment", async (t) => {
  const receipt = { userId: "test/user", receiptId: "receipt" };
  const result = { productId: "planavora.premium", productType: "ENTITLED", receiptId: "receipt", cancelDate: null };
  const upstream = t.mock.method(globalThis, "fetch");
  for (const lat of [false, true]) {
    for (const fields of [
      { betaProduct: false, testTransaction: false },
      { betaProduct: true, testTransaction: true },
      { betaProduct: false, testTransaction: true },
      { betaProduct: lat, testTransaction: lat, cancelDate: 1 },
      { betaProduct: lat, testTransaction: lat, productId: "wrong" },
    ]) {
      upstream.mock.mockImplementation(async (url, options) => {
        assert.equal(new URL(url).hostname, "appstore-sdk.amazon.com");
        assert.match(url, /\/user\/test%2Fuser\/receiptId\/receipt$/);
        assert.equal(options.redirect, "manual");
        return Response.json({ ...result, ...fields });
      });
      const response = await worker.fetch(request(JSON.stringify(receipt)), {
        AMAZON_RVS_SECRET: "fixture-secret",
        AMAZON_RVS_LAT: lat ? "1" : "0",
      });
      assert.equal(response.status, 200);
      assert.equal((await response.json()).valid, fields.betaProduct === lat && fields.testTransaction === lat
        && !fields.cancelDate && !fields.productId);
    }
  }
});
