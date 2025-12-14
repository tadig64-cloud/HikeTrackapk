#!/usr/bin/env python3
import argparse, json, os, re, time, zipfile, datetime
from pathlib import Path
import xml.etree.ElementTree as ET
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

PLACEHOLDER_RE = re.compile(
    r'%(?:\d+\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z]|%%'
)

TAG_RE = re.compile(r'<[^>]+>')  # basic HTML-like tags inside strings

def http_json(method, url, payload=None, timeout=30):
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = Request(url, data=data, headers=headers, method=method)
    with urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))

def list_languages(endpoint):
    try:
        langs = http_json("GET", endpoint.rstrip("/") + "/languages")
        # each item: {"code":"en","name":"English",...}
        return {l.get("code"): l for l in langs if isinstance(l, dict) and l.get("code")}
    except Exception:
        return {}

def protect(text):
    # Protect placeholders and tags so the translator doesn't break them
    ph_map = {}
    tag_map = {}

    def ph_repl(m):
        token = f"__PH{len(ph_map)}__"
        ph_map[token] = m.group(0)
        return token

    def tag_repl(m):
        token = f"__TAG{len(tag_map)}__"
        tag_map[token] = m.group(0)
        return token

    t = PLACEHOLDER_RE.sub(ph_repl, text)
    t = TAG_RE.sub(tag_repl, t)
    return t, ph_map, tag_map

def unprotect(text, ph_map, tag_map):
    t = text
    # restore tags first (order doesn't matter much)
    for token, val in tag_map.items():
        t = t.replace(token, val)
    for token, val in ph_map.items():
        t = t.replace(token, val)
    return t

def translate(endpoint, source, target, text):
    if not text.strip():
        return text
    protected, ph_map, tag_map = protect(text)
    payload = {
        "q": protected,
        "source": source,
        "target": target,
        "format": "text"
    }
    res = http_json("POST", endpoint.rstrip("/") + "/translate", payload=payload)
    translated = res.get("translatedText", "")
    out = unprotect(translated, ph_map, tag_map)
    return out if out else text

def should_translate_elem(elem):
    # Skip if translatable="false"
    if elem.attrib.get("translatable") == "false":
        return False
    # tools:translatable="false" (namespace)
    for k,v in elem.attrib.items():
        if k.endswith("translatable") and v == "false":
            return False
    return True

def has_translatable_content(root):
    # any string / string-array / plurals present
    return (root.find("string") is not None) or (root.find("string-array") is not None) or (root.find("plurals") is not None)

def backup_res(project_path: Path):
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    out = project_path / f"translations_backup_{ts}.zip"
    res = project_path / "app" / "src" / "main" / "res"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        if res.exists():
            for p in res.rglob("*"):
                if p.is_file():
                    z.write(p, p.relative_to(project_path))
    return out

def write_xml(root, out_path: Path):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    # Write with utf-8 xml header
    xml_bytes = ET.tostring(root, encoding="utf-8")
    content = b'<?xml version="1.0" encoding="utf-8"?>\n' + xml_bytes
    out_path.write_bytes(content)

def parse_locales(locales_config_path: Path):
    root = ET.parse(locales_config_path).getroot()
    ns = {"android": "http://schemas.android.com/apk/res/android"}
    locales = []
    for loc in root.findall("locale"):
        name = loc.attrib.get("{http://schemas.android.com/apk/res/android}name")
        if name:
            locales.append(name)
    return locales

def android_locale_to_folder(locale: str):
    # e.g. pt-BR -> values-pt-rBR ; zh-CN -> values-zh-rCN
    parts = locale.replace('_','-').split('-')
    if len(parts) == 1:
        return f"values-{parts[0]}"
    lang = parts[0]
    region = parts[1]
    return f"values-{lang}-r{region.upper()}"

def map_to_libre_code(locale: str, available_codes: set):
    # Map Android locale to LibreTranslate code
    loc = locale.replace('_','-').lower()
    if loc in available_codes:
        return loc
    # common mappings
    if loc == "pt-br":
        if "pt" in available_codes:
            return "pt"
    if loc == "zh-cn":
        if "zh" in available_codes:
            return "zh"
        if "zh_cn" in available_codes:
            return "zh_cn"
    # try just language
    lang = loc.split('-')[0]
    if lang in available_codes:
        return lang
    return None

def translate_file(endpoint, source_code, target_code, in_path: Path, out_path: Path):
    try:
        tree = ET.parse(in_path)
        root = tree.getroot()
    except Exception:
        return False

    changed = False

    # strings
    for s in root.findall("string"):
        if not should_translate_elem(s):
            continue
        text = s.text or ""
        # keep empty or non-text nodes
        if text.strip():
            new = translate(endpoint, source_code, target_code, text)
            if new != text:
                s.text = new
                changed = True

    # string arrays
    for arr in root.findall("string-array"):
        if not should_translate_elem(arr):
            continue
        for item in arr.findall("item"):
            text = item.text or ""
            if text.strip():
                new = translate(endpoint, source_code, target_code, text)
                if new != text:
                    item.text = new
                    changed = True

    # plurals
    for pl in root.findall("plurals"):
        if not should_translate_elem(pl):
            continue
        for item in pl.findall("item"):
            text = item.text or ""
            if text.strip():
                new = translate(endpoint, source_code, target_code, text)
                if new != text:
                    item.text = new
                    changed = True

    # If no translatable nodes, don't write
    if not has_translatable_content(root):
        return False

    write_xml(root, out_path)
    return True

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", required=True, help="Project root (where app/ is)")
    ap.add_argument("--source", default="fr", help="Source language (default fr)")
    ap.add_argument("--endpoint", default="http://localhost:5000", help="LibreTranslate endpoint")
    ap.add_argument("--locales-config", required=True, help="Path to locales_config.xml")
    ap.add_argument("--sleep", type=float, default=0.05, help="Sleep between requests (seconds)")
    args = ap.parse_args()

    project = Path(args.project).resolve()
    res = project / "app" / "src" / "main" / "res"
    values_fr = res / "values"
    locales_cfg = project / args.locales_config

    if not values_fr.exists():
        raise SystemExit(f"Impossible de trouver {values_fr}")

    if not locales_cfg.exists():
        raise SystemExit(f"Impossible de trouver {locales_cfg}")

    print("Endpoint:", args.endpoint)
    langs = list_languages(args.endpoint)
    if langs:
        print("Langues dispo (LibreTranslate):", ", ".join(sorted(langs.keys())))
    else:
        print("ATTENTION: Impossible de lire /languages. Le serveur LibreTranslate tourne bien ? http://localhost:5000")

    # Backup
    backup = backup_res(project)
    print("Backup cree:", backup.name)

    locales = parse_locales(locales_cfg)
    # Remove source locale
    locales = [l for l in locales if l.lower() != args.source.lower()]

    # list all values/*.xml
    files = sorted([p for p in values_fr.glob("*.xml") if p.is_file()])

    total_written = 0
    for loc in locales:
        folder = android_locale_to_folder(loc)
        target_dir = res / folder
        # determine libre code
        libre_code = map_to_libre_code(loc, set(langs.keys()) if langs else set())
        if libre_code is None and langs:
            print(f"[SKIP] {loc}: pas de modele/ langue dispo dans LibreTranslate (dossier {folder})")
            continue

        # If /languages wasn't available, try with just lang part
        target_code = libre_code if libre_code else loc.split("-")[0].lower()
        print(f"\n=== {loc} -> {folder} (Libre: {target_code}) ===")

        wrote_any = 0
        for f in files:
            in_path = f
            out_path = target_dir / f.name
            ok = translate_file(args.endpoint, args.source.lower(), target_code, in_path, out_path)
            if ok:
                wrote_any += 1
                total_written += 1
            time.sleep(args.sleep)

        print(f"Fichiers ecrits: {wrote_any}")

    print("\nTermine. Total fichiers ecrits:", total_written)
    print("Conseil: Android Studio > Build > Clean puis Rebuild.")

if __name__ == "__main__":
    main()
