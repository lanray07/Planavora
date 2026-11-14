import json
import unittest
from io import BytesIO
from unittest.mock import patch

from scripts import receipt_verifier


class ReceiptVerifierTest(unittest.TestCase):
    def request(self, mode, upstream):
        body = json.dumps({"userId": "tester", "receiptId": "receipt"}).encode()
        environment = {"AMAZON_RVS_SECRET": "fixture-secret", **mode}
        with patch.dict("os.environ", environment, clear=True), patch.object(receipt_verifier, "build_opener") as opener:
            opener.return_value.open.return_value.__enter__.return_value = BytesIO(json.dumps(upstream).encode())
            statuses = []
            response = receipt_verifier.application(
                {"REQUEST_METHOD": "POST", "CONTENT_LENGTH": str(len(body)), "wsgi.input": BytesIO(body)},
                lambda status, _: statuses.append(status),
            )
            url = opener.return_value.open.call_args
            return statuses[0], json.loads(b"".join(response))["valid"], url.args[0] if url else None

    def test_receipts_are_accepted_only_in_their_matching_environment(self):
        modes = [{}, {"AMAZON_RVS_LAT": "1"}, {"AMAZON_RVS_SANDBOX": "1"}]
        for index, mode in enumerate(modes):
            for receipt_index, (beta, test) in enumerate([(False, False), (True, True), (False, True), (True, False)]):
                with self.subTest(mode=index, receipt=receipt_index):
                    status, valid, url = self.request(mode, {
                        "productId": "planavora.premium", "productType": "ENTITLED",
                        "receiptId": "receipt", "cancelDate": None, "betaProduct": beta, "testTransaction": test,
                    })
                    self.assertEqual("200 OK", status)
                    self.assertEqual(index == receipt_index or (index == 1 and receipt_index == 3), valid)
                    self.assertEqual(index == 2, "/sandbox/" in url)

    def test_live_app_testing_rejects_incomplete_and_cancelled_receipts(self):
        valid = {"productId": "planavora.premium", "productType": "ENTITLED", "receiptId": "receipt",
                 "cancelDate": None, "betaProduct": True, "testTransaction": True}
        for change in [{"cancelDate": 1}, {"productId": "other"}, {"receiptId": "other"}, {"betaProduct": None}]:
            with self.subTest(change=change):
                self.assertFalse(self.request({"AMAZON_RVS_LAT": "1"}, valid | change)[1])
        del valid["cancelDate"]
        self.assertFalse(self.request({"AMAZON_RVS_LAT": "1"}, valid)[1])

    def test_conflicting_test_modes_fail_without_contacting_amazon(self):
        status, valid, url = self.request({"AMAZON_RVS_LAT": "1", "AMAZON_RVS_SANDBOX": "1"}, {})
        self.assertEqual("503 Service Unavailable", status)
        self.assertFalse(valid)
        self.assertIsNone(url)
