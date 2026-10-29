"""Monta os pacotes de fundo e de protetor de tela do TV Hub com imagens de domínio público.

Pinturas: Art Institute of Chicago (CC0). Espaço: NASA Image Library (domínio público).
Cada imagem vira "Artista - Título (data).jpg", que é o que o protetor mostra como legenda.

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
import unicodedata
from pathlib import Path

import requests
from PIL import Image

W, H = 1920, 1080
UA = {"User-Agent": "tv-hub-pacotes/1.0", "AIC-User-Agent": "tv-hub-pacotes/1.0"}
TV_DIR = "/sdcard/Android/data/com.petrobot.hume/files/Pictures"

# (pasta, tema, [(artista como o AIC escreve, quantas)], só paisagem?)
AIC_PACKS = [
    ("protetor", "Impressionistas", [
        ("Claude Monet", 3), ("Pierre-Auguste Renoir", 2), ("Vincent van Gogh", 2),
        ("Gustave Caillebotte", 1), ("Georges Seurat", 1), ("Camille Pissarro", 1),
        ("Mary Cassatt", 1), ("Berthe Morisot", 1)], False),
    ("protetor", "Mestres e ukiyo-e", [
        ("Rembrandt", 2), ("El Greco", 1), ("Joseph Mallord William Turner", 2), ("Francisco Goya", 1),
        ("Katsushika Hokusai", 3), ("Utagawa Hiroshige", 3)], False),
    ("fundo", "Paisagens pintadas", [
        ("George Inness", 2), ("Thomas Cole", 2), ("Jean-Baptiste-Camille Corot", 2),
        ("Claude Monet", 2), ("Jasper Francis Cropsey", 1), ("Albert Bierstadt", 1),
        ("Alfred Sisley", 1), ("Winslow Homer", 1)], True),
]
NASA_PACK = ("fundo", "Terra vista do espaço", [
    "Earth at night from International Space Station", "aurora from International Space Station",
    "sunrise over Earth from International Space Station", "Blue Marble Earth",
    "Earth limb from International Space Station"], 12)

log = logging.getLogger("pacotes")


def safe(s, n=70):
    s = re.sub(r'[\\/:*?"<>|\n\r\t]+', " ", s or "").strip()
    return re.sub(r"\s+", " ", s)[:n].rstrip(" .")


def same_artist(wanted, found):
    """Todos os nomes pedidos aparecem no artista do acervo: "Francisco Goya" casa com
    "Francisco José de Goya y Lucientes", mas "Thomas Cole" não casa com "Timothy Cole"."""
    words = lambda s: set(re.sub(r"[-,]", " ", unicodedata.normalize("NFKD", s or "")
                                 .encode("ascii", "ignore").decode().lower()).split())
    return words(wanted) <= words(found)


assert same_artist("Francisco Goya", "Francisco José de Goya y Lucientes")
assert same_artist("Jean-Baptiste-Camille Corot", "Jean Baptiste Camille Corot")
assert not same_artist("Thomas Cole", "Timothy Cole")


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
    img = fit(Image.open(io.BytesIO(raw)), cover)
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest, "JPEG", quality=85, optimize=True, progressive=True)
    log.info("  %s  (%dx%d, %d KB)", dest.name, img.width, img.height, dest.stat().st_size // 1024)


def aic(out, folder, theme, artists, landscape_only):
    log.info("%s/%s", folder, theme)
    for artist, want in artists:
        r = requests.get("https://api.artic.edu/api/v1/artworks/search", headers=UA, timeout=30, params={
            "q": artist, "limit": 60,
            "query[bool][must][][term][is_public_domain]": "true",
            "fields": "id,title,artist_title,date_display,image_id,thumbnail,artwork_type_title"})
        r.raise_for_status()
        got = 0
        for a in r.json()["data"]:
            t = a.get("thumbnail") or {}
            w, h = t.get("width") or 0, t.get("height") or 0
            if (not a.get("image_id") or not same_artist(artist, a.get("artist_title"))
                    or a.get("artwork_type_title") not in ("Painting", "Print") or not w or not h
                    or (landscape_only and w / h < 1.3)):
                continue
            date = (a.get("date_display") or "").split(",")[0]
            name = f"{safe(a['artist_title'], 40)} - {safe(a['title'])} ({safe(date, 20)}).jpg"
            dest = out / folder / theme / name
            if not dest.exists():
                img = requests.get(f"https://www.artic.edu/iiif/2/{a['image_id']}/full/1686,/0/default.jpg",
                                   headers=UA, timeout=60)
                if img.status_code != 200:
                    log.warning("  pulei %s: HTTP %s", a["id"], img.status_code)
                    continue
                save(img.content, dest, cover=folder == "fundo")
            got += 1
            if got == want:
                break
        if got < want:
            log.warning("  %s: só %d de %d", artist, got, want)


def nasa(out, folder, theme, queries, total):
    log.info("%s/%s", folder, theme)
    seen, got = set(), 0
    for q in queries:
        r = requests.get("https://images-api.nasa.gov/search", timeout=30,
                         params={"q": q, "media_type": "image", "page_size": 30})
        r.raise_for_status()
        for it in r.json()["collection"]["items"]:
            d = it["data"][0]
            if d["nasa_id"] in seen or got >= total:
                continue
            seen.add(d["nasa_id"])
            assets = requests.get(it["href"], timeout=30).json()
            url = next((u for u in assets if u.endswith("~large.jpg")), None) or \
                next((u for u in assets if u.endswith("~orig.jpg")), None)
            if not url:
                continue
            raw = requests.get(url.replace("http://", "https://"), timeout=60).content
            with Image.open(io.BytesIO(raw)) as im:
                if im.width / im.height < 1.3 or im.width < 1280:
                    continue
            dest = out / folder / theme / f"NASA - {safe(d['title'])} ({d.get('date_created', '')[:4]}).jpg"
            if dest.exists():  # títulos genéricos se repetem ("Earth Observation")
                dest = dest.with_name(f"{dest.stem} {d['nasa_id']}.jpg")
            save(raw, dest, cover=True)
            got += 1
        if got >= total:
            break


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
            for p in AIC_PACKS:
                aic(a.out, *p)
            nasa(a.out, *NASA_PACK)
        if a.push or a.so_enviar:
            push(a.out, a.adb, a.tv)
    except (requests.RequestException, subprocess.CalledProcessError, OSError) as e:
        log.error("falhou: %s", e)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
