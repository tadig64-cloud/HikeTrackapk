#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import os
import re
import sys
import json
import shutil
from pathlib import Path
from typing import Dict, Tuple
import xml.etree.ElementTree as ET
import urllib.request

PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[+-]?(?:\d+)?(?:\.\d+)?[a-zA-Z]|%%")
BRACE_PH_RE = re.compile(r"\{\d+\}")
TAG_RE = re.compile(r"<[^>]+>")

def http_post_json(url: str, payload: Dict) -> Dict:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw = resp.read().decode("utf-8", errors="replace")
        return json.loads(raw)

def http_get(url: str) -> str:
    with urllib.request.urlopen(url, timeout=60) as resp:
        return resp.read().decode("utf-8", errors="replace")

def check_endpoint(endpoint: str) -> None:
    url = endpoint.rstrip("/") + "/languages"
    txt = http_get(url)
    langs = json.loads(txt)
    codes = [x.get("code") for x in langs if isinstance(x, dict)]
    if not codes:
        raise RuntimeError("LibreTranslate répond mais pas de langues retournées.")
    print(f"[OK] LibreTranslate dispo. Langues: {', '.join(codes)}")

def mask_text(s: str):
    token_map: Dict[str, str] = {}
    idx = 0

    def tok(val: str) -> str:
        nonlocal idx
        key = f"__TOK{idx}__"
        idx += 1
        token_map[key] = val
        return key

    s = s.replace("\\n", tok("\\n"))
    s = s.replace("\\t", tok("\\t"))
    s = s.replace("\\'", tok("\\'"))

    s = TAG_RE.sub(lambda m: tok(m.group(0)), s)
    s = PLACEHOLDER_RE.sub(lambda m: tok(m.group(0)), s)
    s = BRACE_PH_RE.sub(lambda m: tok(m.group(0)), s)

    return s, token_map

def unmask_text(s: str, token_map: Dict[str, str]) -> str:
    for k, v in token_map.items():
        s = s.replace(k, v)
    return s

def should_skip_value(val: str) -> bool:
    if val is None:
        return True
    if val.strip() == "":
        return True
    if "http://" in val or "https://" in val:
        return True
    return False

def translate_text(endpoint: str, text: str, source: str, target: str) -> str:
    url = endpoint.rstrip("/") + "/translate"
    payload = {"q": text, "source": source, "target": target, "format": "text"}
    res = http_post_json(url, payload)
    out = res.get("translatedText")
    if out is None:
        raise RuntimeError(f"Traduction échouée: {res}")
    return out

def indent(elem: ET.Element, level: int = 0) -> None:
    i = "\n" + level * "  "
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = i + "  "
        for child in elem:
            indent(child, level + 1)
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
    else:
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = i

def load_xml(path: Path) -> ET.ElementTree:
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    data = path.read_bytes()
    return ET.ElementTree(ET.fromstring(data, parser=parser))

def is_resources_xml(tree: ET.ElementTree) -> bool:
    return tree.getroot().tag == "resources"

def iter_text_nodes(root: ET.Element):
    for child in list(root):
        if child.tag == "string":
            yield child, "string"
        elif child.tag == "plurals":
            for item in child.findall("item"):
                yield item, "plural_item"
        elif child.tag == "string-array":
            for item in child.findall("item"):
                yield item, "array_item"

def translate_file(endpoint: str, src_xml: Path, out_xml: Path, source_lang: str, target_lang: str,
                   cache: Dict[Tuple[str,str,str], str], skip_names: set):
    tree = load_xml(src_xml)
    if not is_resources_xml(tree):
        return 0, 0

    root = tree.getroot()
    translated = 0
    total = 0

    for el, kind in iter_text_nodes(root):
        total += 1

        if el.get("translatable") == "false":
            continue

        if kind == "string":
            name = el.get("name", "")
            if name in skip_names:
                continue

        val = el.text or ""
        if should_skip_value(val):
            continue

        masked, token_map = mask_text(val)
        key = (masked, source_lang, target_lang)

        if key not in cache:
            cache[key] = translate_text(endpoint, masked, source_lang, target_lang)

        out = unmask_text(cache[key], token_map)
        el.text = out
        translated += 1

    out_xml.parent.mkdir(parents=True, exist_ok=True)
    indent(root)
    tree.write(out_xml, encoding="utf-8", xml_declaration=True)
    return translated, total

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", required=True, help="Chemin vers app/src/main/res")
    ap.add_argument("--source-dir", required=True, help="Dossier source (ex: .../values-fr)")
    ap.add_argument("--targets", required=True, help="Cibles ex: en,es,de")
    ap.add_argument("--endpoint", default="http://127.0.0.1:5000")
    ap.add_argument("--source-lang", default="fr")
    ap.add_argument("--clean-target-dirs", action="store_true")
    ap.add_argument("--write-base", default=None, choices=[None, "en", "fr", "de", "es"])
    ap.add_argument("--skip-names", default="app_name", help="Noms à ne pas traduire, comma")
    args = ap.parse_args()

    res_dir = Path(args.res).resolve()
    src_dir = Path(args.source_dir).resolve()

    if not res_dir.exists():
        print(f"[ERREUR] res introuvable: {res_dir}")
        sys.exit(2)
    if not src_dir.exists():
        print(f"[ERREUR] source-dir introuvable: {src_dir}")
        sys.exit(2)

    check_endpoint(args.endpoint)

    targets = [t.strip() for t in args.targets.split(",") if t.strip()]
    skip_names = set([s.strip() for s in (args.skip_names or "").split(",") if s.strip()])

    src_xmls = [p for p in src_dir.rglob("*.xml") if p.is_file()]
    if not src_xmls:
        print(f"[ERREUR] Aucun XML trouvé dans {src_dir}")
        sys.exit(2)

    print(f"[INFO] Source: {src_dir} ({len(src_xmls)} XML)")
    print(f"[INFO] Cibles: {', '.join(targets)}")

    if args.clean_target_dirs:
        for t in targets:
            if t == args.source_lang:
                continue
            tdir = res_dir / f"values-{t}"
            if tdir.exists():
                print(f"[CLEAN] Suppression {tdir}")
                shutil.rmtree(tdir)

    cache: Dict[Tuple[str,str,str], str] = {}
    totals = {t: {"translated": 0, "total": 0} for t in targets}

    for t in targets:
        if t == args.source_lang:
            continue
        out_dir = res_dir / f"values-{t}"
        for src_xml in src_xmls:
            out_xml = out_dir / src_xml.name
            tr, tot = translate_file(args.endpoint, src_xml, out_xml, args.source_lang, t, cache, skip_names)
            totals[t]["translated"] += tr
            totals[t]["total"] += tot

    if args.write_base:
        base_dir = res_dir / "values"
        base_dir.mkdir(parents=True, exist_ok=True)
        chosen = args.write_base
        if chosen == args.source_lang:
            for src_xml in src_xmls:
                shutil.copy2(src_xml, base_dir / src_xml.name)
        else:
            from_dir = res_dir / f"values-{chosen}"
            if not from_dir.exists():
                print(f"[ERREUR] values-{chosen} n'existe pas.")
            else:
                for p in from_dir.glob("*.xml"):
                    shutil.copy2(p, base_dir / p.name)

    print("\n=== RÉSUMÉ ===")
    for t in targets:
        if t == args.source_lang:
            continue
        print(f"{t}: {totals[t]['translated']} traduits / {totals[t]['total']} éléments vus")
    print("OK.")

if __name__ == "__main__":
    main()
