import copy
import json
from pathlib import Path
import unittest

from benchmark_import import compare

TRUTH = json.loads((Path(__file__).resolve().parent / "fixtures" /
                    "mercado_del_patio_angled_truth.json").read_text(encoding="utf-8"))


def perfect():
    products = [copy.deepcopy(item) for item in TRUTH["products"]]
    extras = [copy.deepcopy(item) for item in TRUTH["extras"]]
    promotions = [copy.deepcopy(item) for item in TRUTH["promotions"]]
    return {"business": copy.deepcopy(TRUTH["business"]), "products": products,
            "extras": extras, "promotions": promotions,
            "businessHours": copy.deepcopy(TRUTH["businessHours"]),
            "faq": [{"question": "Prueba", "answer": (
                "PATIO_GUEST en terraza desde 12:30 con débito, 15-20 min; consultar sin gluten")}],
            "generalNotices": ["Algunos productos pueden contener gluten, lácteos o frutos secos"],
            "warnings": []}


class DocumentBenchmarkTests(unittest.TestCase):
    def test_perfect_truth_set_passes(self):
        result = compare(TRUTH, perfect())
        self.assertTrue(result["passed"], result["errors"])
        self.assertEqual(0, result["criticalErrors"])
        self.assertEqual(100, result["scorePercent"])

    def test_prior_menu_promo_times_are_hallucinations(self):
        data = perfect()
        data["promotions"][1]["startTime"] = "16:00"
        data["promotions"][1]["endTime"] = "18:00"
        result = compare(TRUTH, data)
        self.assertFalse(result["passed"])
        self.assertGreaterEqual(result["criticalErrors"], 2)
        self.assertTrue(any(x["field"].endswith("HAPPY HOUR DULCE.startTime")
                            for x in result["errors"]))

    def test_prior_menu_promo_conditions_are_hallucinations(self):
        data = perfect()
        data["promotions"][2]["conditions"] = (
            "En cafés filtrados o americanos al pedir cualquier desayuno.")
        result = compare(TRUTH, data)
        self.assertEqual(1, result["criticalErrors"])

    def test_availability_and_wrong_photo_are_blocked(self):
        data = perfect()
        data["products"][0]["availability"] = "available"
        next(p for p in data["products"] if p["name"] == "Brownie tibio con helado")[
            "hasVisiblePhoto"] = True
        result = compare(TRUTH, data)
        self.assertEqual(2, result["criticalErrors"])

    def test_price_corrections_and_out_of_stock_must_be_exact(self):
        data = perfect()
        for x in data["products"]:
            if x["name"] == "Lasaña vegetariana":
                x["price"] = 7200
            if x["name"] == "Bagel salmón cream cheese":
                x["availability"] = "available"
        result = compare(TRUTH, data)
        self.assertEqual(2, result["criticalErrors"])

    def test_missing_and_invented_products_cannot_pass(self):
        data = perfect()
        data["products"].pop()
        data["extras"].append({"name": "Papas gigantes", "price": 999})
        result = compare(TRUTH, data)
        self.assertGreaterEqual(result["criticalErrors"], 2)

    def test_missing_and_invented_business_hours_cannot_pass(self):
        data = perfect()
        data["businessHours"].pop()
        result = compare(TRUTH, data)
        self.assertGreater(result["criticalErrors"], 0)

    def test_casefolded_human_labels_are_accepted(self):
        data = perfect()
        data["products"][0]["name"] = " TOSTADAS DE PALTA + HUEVO POCHADO "
        self.assertTrue(compare(TRUTH, data)["passed"])


    def test_extra_bundle_components_in_promotion_name_are_not_missing_offer(self):
        target = {"products": [], "extras": [], "promotions": [
            {"name": "HAPPY HOUR DULCE", "price": 4900,
             "startTime": None, "endTime": None}
        ]}
        model = {"products": [], "extras": [], "promotions": [
            {"name": "HAPPY HOUR DULCE: Cappuccino + cookie gigante",
             "price": 4900, "startTime": None, "endTime": None}
        ]}
        result = compare(target, model)
        self.assertTrue(result["passed"], result["errors"])

    def test_explicit_missing_eligibility_is_format_issue_not_invented_offer(self):
        target = {"products": [], "extras": [], "promotions": [
            {"name": "PROMO BRUNCH 2x1", "conditions": None,
             "days": ["lunes", "martes", "miércoles"],
             "startTime": "08:00", "endTime": "11:30"}
        ]}
        model = {"products": [], "extras": [], "promotions": [
            {"name": "PROMO BRUNCH 2x1",
             "conditions": "No se especifican productos elegibles ni condiciones del 2x1.",
             "days": ["lunes", "martes", "miércoles"],
             "startTime": "08:00", "endTime": "11:30"}
        ]}
        result = compare(target, model)
        self.assertFalse(result["passed"])
        self.assertEqual(0, result["criticalErrors"])
        self.assertEqual("format", result["errors"][0]["severity"])

    def test_missing_eligibility_disclaimer_cannot_hide_real_promo_restriction(self):
        target = {"products": [], "extras": [], "promotions": [
            {"name": "PROMO BRUNCH 2x1", "conditions": None}
        ]}
        model = {"products": [], "extras": [], "promotions": [
            {"name": "PROMO BRUNCH 2x1",
             "conditions": "No se especifican condiciones, pero solo con desayuno"}
        ]}
        result = compare(target, model)
        self.assertEqual(1, result["criticalErrors"])


if __name__ == "__main__":
    unittest.main()
