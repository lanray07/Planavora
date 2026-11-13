"""Amazon receipt verifier WSGI entry point.

Deploy behind HTTPS with request rate limiting. Set AMAZON_RVS_SECRET only on the server.
Use store live mode by default. Use AMAZON_RVS_SANDBOX=1 only in a separate QA deployment.
"""
import json
import os
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, build_opener

SKU = "planavora.premium"
BASE = "https://appstore-sdk.amazon.com"


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def verify(user, receipt):
    secret = os.environ["AMAZON_RVS_SECRET"]
    prefix = "/sandbox" if os.environ.get("AMAZON_RVS_SANDBOX") == "1" else ""
    values = [quote(value, safe="") for value in (secret, user, receipt)]
    url = f"{BASE}{prefix}/version/1.0/verifyReceiptId/developer/{values[0]}/user/{values[1]}/receiptId/{values[2]}"
    try:
        with build_opener(NoRedirect()).open(url, timeout=8) as response:
            result = json.loads(response.read(16384))
    except HTTPError as error:
        if error.code in (400, 410):
            return False
        raise
    return (result.get("productId") == SKU and result.get("productType") == "ENTITLED"
            and result.get("cancelDate") is None and result.get("receiptId") == receipt
            and (prefix or result.get("testTransaction") is False))


def application(environ, start_response):
    status, valid = "400 Bad Request", False
    if environ.get("REQUEST_METHOD") == "POST":
        try:
            size = int(environ.get("CONTENT_LENGTH") or 0)
            if not 0 < size <= 4096:
                raise ValueError
            request = json.loads(environ["wsgi.input"].read(size))
            user, receipt = request["userId"], request["receiptId"]
            if any(not isinstance(value, str) or not 1 <= len(value) <= 512 for value in (user, receipt)):
                raise ValueError
        except (KeyError, TypeError, ValueError):
            pass
        else:
            try:
                valid = bool(verify(user, receipt))
                status = "200 OK"
            except (URLError, TimeoutError, KeyError, ValueError):
                status = "503 Service Unavailable"
    else:
        status = "405 Method Not Allowed"
    response = json.dumps({"valid": valid, "sku": SKU}).encode()
    start_response(status, [("Content-Type", "application/json"), ("Cache-Control", "no-store"),
                            ("Content-Length", str(len(response)))])
    return [response]
