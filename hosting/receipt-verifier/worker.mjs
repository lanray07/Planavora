const sku = "planavora.premium";

function reply(status, valid = false, headers = {}) {
  return Response.json({ valid, sku }, {
    status,
    headers: { "Cache-Control": "no-store", ...headers },
  });
}

async function readReceipt(request) {
  const reader = request.body?.getReader();
  if (!reader) return null;
  const bytes = new Uint8Array(4096);
  let length = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (length + value.length > bytes.length) {
        await reader.cancel();
        return null;
      }
      bytes.set(value, length);
      length += value.length;
    }
    const receipt = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes.subarray(0, length)));
    const validId = (id) => typeof id === "string" && id.length > 0 && id.length <= 512;
    return validId(receipt?.userId) && validId(receipt?.receiptId) ? receipt : null;
  } catch {
    return null;
  }
}

export default {
  async fetch(request, env) {
    if (new URL(request.url).pathname !== "/amazon") return reply(404);
    if (request.method !== "POST") return reply(405, false, { Allow: "POST" });
    const receipt = await readReceipt(request);
    if (!receipt) return reply(400);

    const secret = env.AMAZON_RVS_SECRET?.trim();
    if (!secret) return reply(503);
    const parts = [secret, receipt.userId, receipt.receiptId].map(encodeURIComponent);
    const url = `https://appstore-sdk.amazon.com/version/1.0/verifyReceiptId/developer/${parts[0]}/user/${parts[1]}/receiptId/${parts[2]}`;
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 8000);
      let response;
      try {
        response = await fetch(url, { redirect: "manual", signal: controller.signal });
      } finally {
        clearTimeout(timer);
      }
      if ([400, 410, 497].includes(response.status)) return reply(200);
      if (!response.ok) {
        console.warn("Amazon RVS returned HTTP", response.status);
        return reply(503);
      }
      const result = await response.json();
      const lat = env.AMAZON_RVS_LAT === "1";
      const valid = result.productId === sku && result.productType === "ENTITLED"
        && result.receiptId === receipt.receiptId && result.cancelDate === null
        && result.betaProduct === lat && (lat || result.testTransaction === false);
      return reply(200, valid);
    } catch (error) {
      console.warn("Amazon RVS request or response failed", error instanceof TypeError ? "TypeError" : "OtherError");
      return reply(503);
    }
  },
};
