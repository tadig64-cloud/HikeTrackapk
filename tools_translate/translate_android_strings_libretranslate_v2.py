#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Translate Android string resources using a LibreTranslate endpoint.

Goals:
- Only generate localizable resources (string, string-array, plurals) in values-xx
- Preserve placeholders (%1$s, %d, ...), escaped sequences (\n, \t, ...), and inline tags (<xliff:g>, <b>, ...)
- Avoid AAPT2 errors by sanitizing backslashes and suspicious \u / \U sequences

Typical use:
  py tools_translate/translate_android_strings_libretranslate_v2.py \
    --res ./app/src/main/res \
    --source-dir ./app/src/main/res/values-fr \
    --targets en,es,de \
    --endpoint http://127.0.0.1:5000 \
    --clean-target-dirs \
    --write-base fr

Notes:
- This tool is designed for beginners: it tries to be safe rather than "perfect".
- If LibreTranslate is slow on first run (downloads models), wait until /languages responds.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Tuple
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

XLIFF_NS = "urn:oasis:names:tc:xliff:document:1.2"
ET.register_namespace("xliff", XLIFF_NS)

LOCALIZABLE_TAGS = {"string", "string-array", "plurals"}

# Android backslash escapes that are generally safe in string resources.
_ALLOWED_ESCAPES = set(["n", "t", "'", '"', "\\"])
_RE_BAD_U4 = re.compile(r"\\u(?![0-9a-fA-F]{4})")
_RE_BAD_U8 = re.compile(r"\\U(?![0-9a-fA-F]{8})")
_RE_ANY_BACKSLASH = re.compile(r"\\(.)", re.DOTALL)

# Placeholders to protect from translation.
# Covers: %s, %1$s, %1$.2f, %d, %% ... and also {0} style placeholders.
_RE_PRINTF = re.compile(r"%(?:\d+\$)?[\-\+\#\ 0\,\(]*\d*(?:\.\d+)?[a-zA-Z%]")
_RE_BRACES = re.compile(r"\{\d+\}")

@dataclass
class Tokenizer:
    mapping: Dict[str, str]
    counter: int = 0

    def token(self, original: str, kind: str) -> str:
        key = f"__{kind}{self.counter}__"
        self.counter += 1
        self.mapping[key] = original
        return key


def fix_android_text(s: str) -> str:
    if s is None:
        return s
    s = s.replace("\r\n", "\n")
    # Broken unicode escapes like \u{xxx}
    s = _RE_BAD_U4.sub(r"\\\\u", s)
    s = _RE_BAD_U8.sub(r"\\\\U", s)

    # Escape unknown backslash sequences
    def _fix_bs(m: re.Match) -> str:
        ch = m.group(1)
        if ch in _ALLOWED_ESCAPES:
            return "\\" + ch
        return "\\\\" + ch

    s = _RE_ANY_BACKSLASH.sub(_fix_bs, s)

    # If ends with single backslash, escape it
    if s.endswith("\\") and not s.endswith("\\\\"):
        s = s[:-1] + "\\\\"

    return s


def http_post_form(url: str, data: Dict[str, str], timeout: int = 120) -> Dict:
    body = urllib.parse.urlencode(data).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded", "User-Agent": "HikeTrack-i18n-tool"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read().decode("utf-8", errors="replace")
        return json.loads(raw)


def lt_translate(endpoint: str, text: str, source: str, target: str) -> str:
    url = endpoint.rstrip("/") + "/translate"
    payload = {
        "q": text,
        "source": source,
        "target": target,
        "format": "text",
    }
    out = http_post_form(url, payload)
    return out.get("translatedText", "")


def flat_string_from_string_elem(elem: ET.Element, tok: Tokenizer) -> Tuple[str, List[str]]:
    """Flatten a <string> that may contain child tags into a text with __TAGx__ tokens.

    Returns (flat_text, tag_tokens_in_order)
    """
    tag_tokens: List[str] = []
    parts: List[str] = []

    if elem.text:
        parts.append(elem.text)

    for child in list(elem):
        # Serialize the whole child tag as atomic placeholder.
        serialized = ET.tostring(child, encoding="unicode")
        t = tok.token(serialized, "TAG")
        tag_tokens.append(t)
        parts.append(t)
        if child.tail:
            parts.append(child.tail)

    return "".join(parts), tag_tokens


def restore_flat_into_string_elem(elem: ET.Element, flat: str, tag_tokens: List[str], tok_map: Dict[str, str]) -> bool:
    """Rebuild elem.text and child.tails using the translated flat string.

    Keeps existing children (original ones) but updates surrounding texts.
    Returns True if rebuild succeeded.
    """
    if not tag_tokens:
        elem.text = flat
        return True

    # Split translated string by tag tokens, preserving order.
    # We expect tokens to appear in the same order.
    remaining = flat
    segments: List[str] = []
    for t in tag_tokens:
        idx = remaining.find(t)
        if idx < 0:
            return False
        segments.append(remaining[:idx])
        remaining = remaining[idx + len(t):]
    segments.append(remaining)

    # Apply to elem and child tails
    children = list(elem)
    if len(children) != len(tag_tokens):
        return False

    elem.text = segments[0]
    for i, ch in enumerate(children):
        ch.tail = segments[i + 1]

    return True


def protect_placeholders(text: str, tok: Tokenizer) -> str:
    # Protect printf-style placeholders
    def repl_printf(m: re.Match) -> str:
        return tok.token(m.group(0), "PH")

    text = _RE_PRINTF.sub(repl_printf, text)

    # Protect {0} style placeholders
    def repl_br(m: re.Match) -> str:
        return tok.token(m.group(0), "BR")

    text = _RE_BRACES.sub(repl_br, text)

    return text


def unprotect_tokens(text: str, tok_map: Dict[str, str]) -> str:
    # Replace longer tokens first to avoid partial matches (safe anyway)
    for k in sorted(tok_map.keys(), key=len, reverse=True):
        text = text.replace(k, tok_map[k])
    return text


def is_translatable(elem: ET.Element) -> bool:
    # Skip strings explicitly marked as non-translatable
    if elem.attrib.get("translatable", "true").lower() == "false":
        return False
    return True


def collect_localizable_children(src_root: ET.Element) -> List[ET.Element]:
    out = []
    for child in list(src_root):
        tag = child.tag.split("}")[-1]
        if tag in LOCALIZABLE_TAGS:
            out.append(child)
    return out


def clone_element(elem: ET.Element) -> ET.Element:
    # Deep clone using serialization (simplest, preserves namespaces)
    return ET.fromstring(ET.tostring(elem, encoding="utf-8"))


def indent(elem: ET.Element, level: int = 0) -> None:
    i = "\n" + level * "  "
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = i + "  "
        for e in elem:
            indent(e, level + 1)
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
    else:
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = i


def write_resources_xml(path: Path, root: ET.Element) -> None:
    indent(root)
    xml = ET.tostring(root, encoding="utf-8", xml_declaration=True).decode("utf-8") + "\n"
    path.write_text(xml, encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", required=True, help="Path to app/src/main/res")
    ap.add_argument("--source-dir", required=True, help="Source values folder (values or values-fr)")
    ap.add_argument("--targets", required=True, help="Comma-separated: en,es,de")
    ap.add_argument("--endpoint", default="http://127.0.0.1:5000", help="LibreTranslate endpoint")
    ap.add_argument("--source-lang", default="fr", help="Source language code")
    ap.add_argument("--clean-target-dirs", action="store_true", help="Delete values-xx folders before writing")
    ap.add_argument("--write-base", default="", help="If set (e.g. fr): write a values-fr copy of source localizable files")
    ap.add_argument("--skip-names", default="", help="Comma-separated file basenames to skip, e.g. secrets.xml")
    args = ap.parse_args()

    res = Path(args.res).resolve()
    src_dir = Path(args.source_dir).resolve()
    if not res.exists():
        print(f"[ERREUR] res introuvable: {res}")
        return 2
    if not src_dir.exists():
        print(f"[ERREUR] source-dir introuvable: {src_dir}")
        return 2

    targets = [x.strip() for x in args.targets.split(",") if x.strip()]
    skip = {x.strip() for x in args.skip_names.split(",") if x.strip()}

    # Collect candidate XML files from source
    src_files = sorted([p for p in src_dir.glob("*.xml") if p.name not in skip])
    if not src_files:
        print(f"[ERREUR] Aucun XML dans {src_dir}")
        return 2

    print(f"[INFO] res: {res}")
    print(f"[INFO] source: {src_dir}")
    print(f"[INFO] targets: {', '.join(targets)}")

    # Optionally clean target dirs
    if args.clean_target_dirs:
        for t in targets:
            td = res / f"values-{t}"
            if td.exists():
                print(f"[INFO] Nettoyage: {td}")
                shutil.rmtree(td, ignore_errors=True)

    # Write base copy values-fr if requested
    if args.write_base:
        base_dir = res / f"values-{args.write_base}"
        base_dir.mkdir(parents=True, exist_ok=True)
        for f in src_files:
            # Keep only localizable resources
            try:
                tree = ET.parse(str(f))
            except Exception as e:
                print(f"[WARN] XML invalide, skip: {f.name} -> {e}")
                continue
            src_root = tree.getroot()
            if src_root.tag.split('}')[-1] != "resources":
                continue
            kids = collect_localizable_children(src_root)
            if not kids:
                continue
            out_root = ET.Element(src_root.tag, src_root.attrib)
            for k in kids:
                out_root.append(clone_element(k))
            write_resources_xml(base_dir / f.name, out_root)

    # Translate per target
    for tgt in targets:
        out_dir = res / f"values-{tgt}"
        out_dir.mkdir(parents=True, exist_ok=True)

        print(f"\n=== {tgt} ===")
        for f in src_files:
            try:
                tree = ET.parse(str(f))
            except Exception as e:
                print(f"[WARN] XML invalide, skip: {f.name} -> {e}")
                continue

            src_root = tree.getroot()
            if src_root.tag.split('}')[-1] != "resources":
                continue

            kids = collect_localizable_children(src_root)
            if not kids:
                continue

            out_root = ET.Element(src_root.tag, src_root.attrib)

            # For each resource element, translate its textual parts
            for k in kids:
                tag = k.tag.split('}')[-1]
                k2 = clone_element(k)

                if tag == "string":
                    if not is_translatable(k2):
                        out_root.append(k2)
                        continue

                    tok = Tokenizer(mapping={})
                    flat, tag_tokens = flat_string_from_string_elem(k2, tok)
                    flat = protect_placeholders(flat, tok)

                    # If empty or whitespace, keep
                    if not flat.strip():
                        out_root.append(k2)
                        continue

                    # Translate
                    try:
                        tr = lt_translate(args.endpoint, flat, args.source_lang, tgt)
                    except Exception as e:
                        print(f"[FAIL] {f.name}:{k2.attrib.get('name','?')} -> {e}")
                        out_root.append(k2)
                        continue

                    tr = unprotect_tokens(tr, tok.mapping)
                    tr = fix_android_text(tr)

                    ok = restore_flat_into_string_elem(k2, tr, tag_tokens, tok.mapping)
                    if not ok:
                        # Fallback: keep source (better than breaking)
                        out_root.append(clone_element(k))
                    else:
                        out_root.append(k2)

                elif tag == "string-array":
                    # Translate each <item> text
                    for item in k2.findall("item"):
                        if item.text and item.text.strip():
                            tok = Tokenizer(mapping={})
                            txt = protect_placeholders(item.text, tok)
                            try:
                                tr = lt_translate(args.endpoint, txt, args.source_lang, tgt)
                                tr = unprotect_tokens(tr, tok.mapping)
                                item.text = fix_android_text(tr)
                            except Exception:
                                # keep original
                                pass
                    out_root.append(k2)

                elif tag == "plurals":
                    for item in k2.findall("item"):
                        if item.text and item.text.strip():
                            tok = Tokenizer(mapping={})
                            txt = protect_placeholders(item.text, tok)
                            try:
                                tr = lt_translate(args.endpoint, txt, args.source_lang, tgt)
                                tr = unprotect_tokens(tr, tok.mapping)
                                item.text = fix_android_text(tr)
                            except Exception:
                                pass
                    out_root.append(k2)

                else:
                    out_root.append(k2)

            # Write file
            write_resources_xml(out_dir / f.name, out_root)
            print(f"[OK] {f.name}")

    print("\n[OK] Traduction terminée.")
    print("Si Android Studio se plaint encore, lance 03_sanitize_translations.bat.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
