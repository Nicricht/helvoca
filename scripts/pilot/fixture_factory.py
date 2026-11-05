#!/usr/bin/env python3
"""Generate 3 synthetic menu images and a manifest. Offline, no customer data."""
import argparse
import json
from pathlib import Path

TRUTH = Path(__file__).resolve().parent / "fixtures" / "mercado_del_patio_angled_truth.json"


def generate(out):
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    original = json.loads(TRUTH.read_text(encoding="utf-8"))
    truth = json.loads(json.dumps(original))
    # There are no dish photographs in these rendered images.
    for item in truth["products"]:
        item["hasVisiblePhoto"] = False
    (out / "truth.json").write_text(
        json.dumps(truth, ensure_ascii=False, indent=2), encoding="utf-8")

    def face(size, bold=False):
        try:
            return ImageFont.truetype("DejaVuSans-Bold.ttf" if bold else
                                      "DejaVuSans.ttf", size)
        except OSError:
            return ImageFont.load_default()

    image = Image.new("RGB", (1800, 2400), "#fffaf2")
    draw = ImageDraw.Draw(image)
    draw.text((70, 22), "MERCADO DEL PATIO", font=face(65, True), fill="#23664e")
    draw.text((70, 105), "@mercadodelpatio | +56 9 5555 1122 | Moneda: $",
              font=face(26), fill="#24362c")

    def heading(label, x, y):
        draw.rectangle((x, y, x+755, y+48), fill="#ddebe0")
        draw.text((x+10, y+4), label.upper(), font=face(25, True), fill="#23664e")
        return y+62

    def item(name, amount, x, y):
        draw.text((x+5, y), name, font=face(22), fill="#24362c")
        draw.text((x+600, y), "$"+format(amount,",").replace(",","." ),
                  font=face(23,True), fill="#24362c")
        return y+44

    xy=[(70,175),(920,175)]
    for category, side in (("Desayunos y Brunch",0),("Fondos / Almuerzos",0),
                           ("Café y Bebidas",1),("Postres",1)):
        x, y = xy[side]
        y=heading(category, x,y)
        for p in truth["products"]:
            if p["category"] != category:
                continue
            y=item(p["name"],p["price"],x,y)
            if p["name"] == "Bagel salmón cream cheese":
                draw.text((x+10,y-7),"AGOTADO",font=face(17,True),fill="#a32525")
                y+=21
            if p.get("previousPrice") is not None:
                draw.text((x+10,y-6),"ANTES: $"+str(p["previousPrice"])+
                          "  AHORA: $"+str(p["price"]),
                          font=face(17,True),fill="#a32525")
                y+=24
        xy[side]=(x,y+20)
    y=max(p[1] for p in xy)+30
    y=heading("Agregados",70,y)
    for n,p in enumerate(truth["extras"]):
        item(p["name"],p["price"],70 if n<4 else 920,y+(n%4)*48)
    y+=245
    y=heading("Promociones",70,y)
    for p in truth["promotions"]:
        amount=("$"+str(p["price"])) if p.get("price") else (
            str(p["discountPercent"])+"%" if p.get("discountPercent") else "2x1")
        draw.text((80,y),p["name"]+" "+amount,font=face(25,True),fill="#24362c")
        description=p.get("conditions") or ""
        if p["name"]=="PROMO BRUNCH 2x1":
            description="Lunes a miércoles, 08:00–11:30"
        elif p["name"]=="MARTES VEGGIE":
            description="Martes: Bowl mediterráneo y ravioles de zapallo"
        draw.text((90,y+32),description,font=face(19),fill="#24362c")
        y+=84
    y=heading("Horarios",70,y+8)
    for days,period in zip(truth["businessHours"],
                           ["Lunes a viernes","Sábado","Domingo"]):
        draw.text((80,y),period+": "+days["open"]+"–"+days["close"],
                  font=face(22),fill="#24362c")
        y+=36
    y=heading("FAQ",70,y+10)
    for text in ("Wi-Fi: PATIO_GUEST","Pet friendly en terraza",
                 "Delivery desde las 12:30","Pago: débito, crédito y efectivo",
                 "Cocina: 15-20 min","Consultar opciones veganas y sin gluten"):
        draw.text((80,y),text,font=face(21),fill="#24362c")
        y+=34
    draw.text((80,min(y+5,2320)),"Alérgenos: gluten, lácteos o frutos secos.",
              font=face(21),fill="#a32525")

    result=[]
    for name,tilt,blur,glare in (("clean.jpg",0,0,False),
                                  ("tilted.jpg",2,0.4,False),
                                  ("glare.jpg",-2,0.8,True)):
        transformed=image.copy()
        if glare:
            overlay=Image.new("RGBA",transformed.size,(0,0,0,0))
            ImageDraw.Draw(overlay).polygon(
                [(850,50),(1160,50),(620,2200),(400,2200)],
                fill=(255,255,255,32))
            transformed=Image.alpha_composite(
                transformed.convert("RGBA"),overlay).convert("RGB")
        if tilt:
            transformed=transformed.rotate(
                tilt, resample=Image.Resampling.BICUBIC,fillcolor="#b79f85")
        if blur:
            transformed=transformed.filter(ImageFilter.GaussianBlur(blur))
        transformed.thumbnail((990,1450))
        dest=out/name
        for quality in (80,65,50,35):
            transformed.save(dest,quality=quality,optimize=True)
            if dest.stat().st_size<=512_000:
                break
        if dest.stat().st_size>512_000:
            raise ValueError("Pilot image exceeds 512 KB")
        result.append({"image":name,"truth":"truth.json"})
    manifest=out/"manifest.json"
    manifest.write_text(json.dumps(result,indent=2),encoding="utf-8")
    return manifest


if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--out",type=Path,required=True)
    args=parser.parse_args()
    print("Generated:",generate(args.out))
