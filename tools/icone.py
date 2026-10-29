"""Desenha um banner de dock para um app cujo icone destoa dos outros.

A dock do TV Hub usa o banner de TV do app; quando ele nao tem, sobra o icone solto num cartao,
que fica fora do tom ao lado de Netflix, Prime Video e Apple TV. Este script gera um banner no
mesmo gabarito — fundo escuro, marca colorida e o nome em Inter — e manda para a TV.

    python tools/icone.py org.smarttube.stable SmartTube --cor E5342B --push

O launcher le de /sdcard/Android/data/com.petrobot.hume/files/Icones/<pacote>.png, sem recompilar.
"""
import argparse
import logging
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 640, 384
TV_DIR = "/sdcard/Android/data/com.petrobot.hume/files/Icones"
FONTE = Path(__file__).resolve().parent.parent / "assets" / "fonts" / "Inter-SemiBold.ttf"

log = logging.getLogger("icone")


def cor(s):
    s = s.lstrip("#")
    if not re.fullmatch(r"[0-9a-fA-F]{6}", s):
        raise argparse.ArgumentTypeError("cor em hex RRGGBB, ex.: E5342B")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def fundo(img, topo=(26, 28, 34), base=(12, 13, 17)):
    """Mesmo degrade vertical discreto dos cartoes da dock."""
    d = ImageDraw.Draw(img)
    for y in range(H):
        k = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(round(a + (b - a) * k) for a, b in zip(topo, base)))


def marca(img, rgb, x, y, lado):
    """Quadrado arredondado colorido com um play branco dentro."""
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([x, y, x + lado, y + lado], radius=round(lado * 0.26), fill=rgb)
    c, r = x + lado / 2, lado * 0.22
    d.polygon([(c - r * 0.78, y + lado / 2 - r), (c - r * 0.78, y + lado / 2 + r),
               (c + r * 0.95, y + lado / 2)], fill=(255, 255, 255))


def desenhar(nome, rgb):
    img = Image.new("RGB", (W, H))
    fundo(img)
    d = ImageDraw.Draw(img)
    lado = 104
    tamanho = 60
    while tamanho > 20:
        fonte = ImageFont.truetype(str(FONTE), tamanho)
        largura = d.textlength(nome, font=fonte)
        if lado + 26 + largura <= W - 72:
            break
        tamanho -= 2
    total = lado + 26 + largura
    x = (W - total) / 2
    marca(img, rgb, round(x), round((H - lado) / 2), lado)
    caixa = d.textbbox((0, 0), nome, font=fonte)
    d.text((x + lado + 26, (H - (caixa[3] - caixa[1])) / 2 - caixa[1]), nome,
           font=fonte, fill=(255, 255, 255))
    return img


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pacote", help="nome do pacote, ex.: org.smarttube.stable")
    ap.add_argument("nome", help="texto do banner, ex.: SmartTube")
    ap.add_argument("--cor", type=cor, default=cor("E5342B"), help="cor da marca, hex RRGGBB")
    ap.add_argument("--saida", type=Path, default=Path("icones"))
    ap.add_argument("--push", action="store_true", help="envia para a TV")
    ap.add_argument("--adb", default="adb")
    ap.add_argument("--tv", default="192.168.1.147:5555")
    a = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    if not FONTE.is_file():
        log.error("fonte nao encontrada: %s", FONTE)
        return 1
    a.saida.mkdir(parents=True, exist_ok=True)
    dest = a.saida / (a.pacote + ".png")
    desenhar(a.nome, a.cor).save(dest, "PNG")
    log.info("%s", dest)
    if a.push:
        try:
            subprocess.run([a.adb, "-s", a.tv, "shell", "mkdir -p '" + TV_DIR + "'"], check=True)
            subprocess.run([a.adb, "-s", a.tv, "push", str(dest), TV_DIR + "/" + dest.name],
                           check=True, stdout=subprocess.DEVNULL)
        except subprocess.CalledProcessError as e:
            log.error("falhou o envio: %s", e)
            return 1
        log.info("enviado; aperte Home para a dock recarregar")
    return 0


if __name__ == "__main__":
    sys.exit(main())
