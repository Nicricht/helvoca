"""Real, user-provided Playground regression, with fictional business data.

The previous Gemini Playground output must not be treated as a successful
commercial import: 26/26 products and prices were correct, but it hallucinated
commercial rules and product availability.
"""
import json
from pathlib import Path
import unittest
from benchmark_import import compare

FIXTURES=Path(__file__).resolve().parent/"fixtures"
TRUTH=json.loads((FIXTURES/"mercado_del_patio_angled_truth.json").read_text(encoding="utf-8"))
OBSERVED=json.loads((FIXTURES/"mercado_del_patio_angled_actual_playground.json").read_text(encoding="utf-8"))


class PlaygroundRealResponseRegression(unittest.TestCase):
    def test_catalog_accurate_but_entire_response_must_fail_safe_review(self):
        by_name={item["name"]:item for item in OBSERVED["products"]}
        self.assertEqual(26,len(by_name))
        for reference in TRUTH["products"]:
            self.assertEqual(reference["price"],by_name[reference["name"]]["price"])
        self.assertEqual(8,len(OBSERVED["extras"]))
        self.assertEqual(4,len(OBSERVED["promotions"]))
        checked=compare(TRUTH,OBSERVED)
        self.assertFalse(checked["passed"])
        self.assertGreaterEqual(checked["criticalErrors"],28)
        fields={error["field"] for error in checked["errors"]}
        self.assertIn("promotions.HAPPY HOUR DULCE.startTime",fields)
        self.assertIn("promotions.PROMO BRUNCH 2x1.conditions",fields)
        self.assertIn("products.Brownie tibio con helado.hasVisiblePhoto",fields)
        self.assertIn("products.Tostadas de palta + huevo pochado.availability",fields)

    def test_not_a_provider_api_or_billing_telemetry_record(self):
        self.assertNotIn("usageMetadata",OBSERVED)
        self.assertNotIn("response_id",OBSERVED)


if __name__=="__main__":
    unittest.main()
