"""Monta os pacotes de fundo e de protetor de tela do TV Hub com imagens de domínio público.

Fotos do Wikimedia Commons, só as que já vêm maiores que 1920x1080: reduzir mantém nítido,
ampliar borra. Fundo e protetor usam a tela inteira, então valem a mesma regra e a mesma
proporção; pintura em retrato ficou de fora porque cortá-la para 16:9 mostra só um pedaço dela.

    python tools/baixar-pacotes.py pacotes                # baixa em ./pacotes/{fundo,protetor}/<tema>/
    python tools/baixar-pacotes.py pacotes --push         # e envia para a TV
    python tools/baixar-pacotes.py pacotes --so-enviar    # só envia o que já está em ./pacotes
"""
import argparse
import io
import logging
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import requests
from PIL import Image

W, H = 1920, 1080
UA = {"User-Agent": "tv-hub-pacotes/1.0", "AIC-User-Agent": "tv-hub-pacotes/1.0"}
TV_DIR = "/sdcard/Android/data/com.petrobot.hume/files/Pictures"

# Fundo: fotos do Wikimedia Commons. Só entram originais maiores que a tela, para a imagem ser
# reduzida e nunca ampliada — foi o que estragou os pacotes de pintura e da NASA que vieram antes.
COMMONS_PACKS = [
    ("fundo", "Natureza", [
        "Category:Featured pictures of landscapes",
        "Category:Featured pictures of nature",
        "Category:Quality images of landscapes",
    ], 14),
    # O protetor pega tela cheia, entao precisa da mesma regra de tamanho que o fundo. Categorias
    # diferentes de proposito: nao adianta a tela de descanso repetir a foto que ja estava no fundo.
    ("protetor", "Vistas", [
        "Category:Featured pictures of coasts",
        "Category:Featured pictures of cityscapes",
        "Category:Featured pictures of lakes",
        "Category:Featured pictures of waterfalls",
        "Category:Featured pictures of astronomy",
    ], 16),
]
MIN_W, MIN_H = 2560, 1440          # o dobro da tela de largura: sobra detalhe depois do corte
AR_MIN, AR_MAX = 1.55, 2.00        # perto de 16:9; panorama de 5:1 vira uma tira sem assunto
# Um unico tamanho de thumbnail serve para toda essa faixa: 2560 de largura da 1280 de altura
# mesmo no mais largo (2.00), e 2560 ja passa dos 1920 da tela. Entao nada precisa ser ampliado.
PEDIDO = 2560

log = logging.getLogger("pacotes")


def safe(s, n=70):
    s = re.sub(r'[\\/:*?"<>|\n\r\t]+', " ", s or "").strip()
    return re.sub(r"\s+", " ", s)[:n].rstrip(" .")


def fit(img, cover):
    """cover: corta para 16:9 em tela cheia (fundo). Senão: cabe inteira em 1920x1080 (quadro)."""
    img = img.convert("RGB")
    if cover:
        s = max(W / img.width, H / img.height)
        img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
        l, t = (img.width - W) // 2, (img.height - H) // 2
        return img.crop((l, t, l + W, t + H))
    img.thumbnail((W, H), Image.LANCZOS)
    return img


def save(raw, dest, cover):
    img = Image.open(io.BytesIO(raw))
    if cover and (img.width < W or img.height < H):
        log.warning("  pulei %s: %dx%d menor que a tela", dest.name, img.width, img.height)
        return False
    img = fit(img, cover)
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest, "JPEG", quality=85, optimize=True, progressive=True)
    log.info("  %s  (%dx%d, %d KB)", dest.name, img.width, img.height, dest.stat().st_size // 1024)
    return True


def commons_api(**params):
    params.setdefault("format", "json")
    params.setdefault("formatversion", "2")
    r = requests.get("https://commons.wikimedia.org/w/api.php", params=params, headers=UA, timeout=45)
    r.raise_for_status()
    return r.json()


def autor(meta):
    """O campo Artist do Commons vem com HTML; aqui vira um nome simples."""
    bruto = (meta.get("Artist") or {}).get("value", "")
    nome = re.sub(r"<[^>]+>", " ", bruto)
    nome = re.sub(r"&[a-z]+;", " ", nome)
    return safe(nome, 40) or "Commons"


def commons(out, folder, theme, cats, total):
    """Fotos do Commons, só as que já nascem maiores que a tela."""
    log.info("%s/%s", folder, theme)
    # Conta o que ja esta na pasta: rodar de novo completa o que falta em vez de empilhar fotos.
    pasta = out / folder / theme
    got = len(list(pasta.glob("*.jpg"))) if pasta.is_dir() else 0
    if got >= total:
        log.info("  ja tem %d, nada a baixar", got)
        return
    vistos = set()
    # Uma cota por categoria, senao a primeira com muita foto enche o pacote sozinha e o tema fica
    # repetitivo. Quem nao preencher a sua cota sobra para a rodada seguinte, sem cota.
    cota = max(1, -(-total // len(cats)))
    for limite in (cota, total):   # primeira rodada com cota, segunda solta para completar
        for cat in cats:
            daqui = 0
            cont = {}
            while got < total and daqui < limite:
                d = commons_api(action="query", generator="categorymembers", gcmtitle=cat,
                                gcmlimit="50", gcmtype="file", prop="imageinfo",
                                iiprop="url|size|extmetadata", iiurlwidth=str(PEDIDO), **cont)
                for pg in d.get("query", {}).get("pages", []):
                    if got >= total or daqui >= limite or pg["title"] in vistos:
                        continue   # a cota vale foto a foto: uma pagina traz 50 de uma vez so
                    vistos.add(pg["title"])
                    ii = (pg.get("imageinfo") or [{}])[0]
                    w, h = ii.get("width", 0), ii.get("height", 0)
                    if w < MIN_W or h < MIN_H:
                        continue
                    ar = w / h
                    if not AR_MIN <= ar <= AR_MAX:
                        continue
                    meta = ii.get("extmetadata", {})
                    nome = safe(pg["title"][5:].rsplit(".", 1)[0])
                    dest = out / folder / theme / ("%s - %s.jpg" % (autor(meta), nome))
                    if dest.exists():
                        continue   # ja contada la em cima
                    try:
                        raw = requests.get(ii["thumburl"], headers=UA, timeout=90).content
                        if save(raw, dest, cover=True):   # tela cheia nos dois casos
                            got += 1
                            daqui += 1
                    except Exception as e:  # thumbnail fora do ar ou formato que o Pillow nao le
                        log.warning("  pulei %s: %s", nome[:40], e)
                if got >= total or daqui >= limite or "continue" not in d:
                    break
                cont = d["continue"]
    if got < total:
        log.warning("  %s: so %d de %d", theme, got, total)


def push(out, adb, tv):
    with tempfile.TemporaryDirectory() as tmp:
        # adb.exe não lida com caminhos acima de 260 caracteres: envia de uma cópia com nome curto.
        short = Path(tmp) / "p.jpg"
        for theme_dir in sorted(p for p in out.glob("*/*") if p.is_dir()):
            remote = f"{TV_DIR}/{theme_dir.parent.name}/{theme_dir.name}"
            subprocess.run([adb, "-s", tv, "shell", f"mkdir -p '{remote}'"], check=True)
            files = sorted(theme_dir.glob("*.jpg"))
            for f in files:
                shutil.copyfile(f, short)
                subprocess.run([adb, "-s", tv, "push", str(short), f"{remote}/{f.name}"], check=True,
                               stdout=subprocess.DEVNULL)
            log.info("enviado %s (%d imagens)", remote, len(files))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out", type=Path, help="pasta de saída")
    ap.add_argument("--push", action="store_true", help="envia os pacotes para a TV pelo adb")
    ap.add_argument("--adb", default="adb", help="caminho do adb (padrão: adb no PATH)")
    ap.add_argument("--tv", default="192.168.1.147:5555", help="endereço adb da TV")
    ap.add_argument("--so-enviar", action="store_true", help="não baixa, só envia o que já está na pasta")
    ap.add_argument("-v", "--verbose", action="store_true")
    a = ap.parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO, format="%(message)s")
    try:
        if not a.so_enviar:
            for pack in COMMONS_PACKS:
                commons(a.out, *pack)
        if a.push or a.so_enviar:
            push(a.out, a.adb, a.tv)
    except (requests.RequestException, subprocess.CalledProcessError, OSError) as e:
        log.error("falhou: %s", e)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
