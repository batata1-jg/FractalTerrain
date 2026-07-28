"""Scrape world-generation pages from minecraft.wiki into LLM-readable Markdown.

Fetches fully-rendered HTML via the MediaWiki parse API (so templates, tables and
version markers are expanded), strips chrome, converts to Markdown, and prefixes
each file with provenance + a mechanically-extracted list of version markers.
"""

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

from bs4 import BeautifulSoup
from markdownify import MarkdownConverter

UA = "FractalTerrain-docs/1.0 (offline reference build; contact: local)"
API = "https://minecraft.wiki/api.php"
OUT = sys.argv[1]

# (wiki title, output filename, category)
PAGES = [
    ("World generation", "world-generation.md", "Pipeline"),
    ("Custom world generation", "custom-world-generation.md", "Pipeline"),
    ("Terrain features", "terrain-features.md", "Pipeline"),
    ("Chunk", "chunk.md", "Pipeline"),
    ("Chunk format", "chunk-format.md", "Pipeline"),
    ("Heightmap", "heightmap.md", "Pipeline"),
    ("Noise settings", "noise-settings.md", "Noise & density"),
    ("Density function", "density-function.md", "Noise & density"),
    ("Noise router", "noise-router.md", "Noise & density"),
    ("Noise", "noise.md", "Noise & density"),
    ("Surface rule", "surface-rule.md", "Noise & density"),
    ("Configured surface builder", "configured-surface-builder.md", "Noise & density"),
    ("Biome", "biome.md", "Biomes"),
    ("Biome definition", "biome-definition.md", "Biomes"),
    ("Biome tag (Java Edition)", "biome-tag.md", "Biomes"),
    ("Feature", "feature.md", "Features & carvers"),
    ("Configured feature", "configured-feature.md", "Features & carvers"),
    ("Placed feature", "placed-feature.md", "Features & carvers"),
    ("Carver definition", "carver-definition.md", "Features & carvers"),
    ("Cave", "cave.md", "Features & carvers"),
    ("Ore", "ore.md", "Features & carvers"),
    ("River", "river.md", "Features & carvers"),
    ("Structure", "structure.md", "Structures"),
    ("Structure definition", "structure-definition.md", "Structures"),
    ("Structure set", "structure-set.md", "Structures"),
    ("Template pool", "template-pool.md", "Structures"),
    ("Processor list", "processor-list.md", "Structures"),
    ("Jigsaw Block", "jigsaw-block.md", "Structures"),
    ("Dimension definition", "dimension-definition.md", "Dimensions & presets"),
    ("Dimension type", "dimension-type.md", "Dimensions & presets"),
    ("World preset definition", "world-preset-definition.md", "Dimensions & presets"),
    ("Superflat", "superflat.md", "Dimensions & presets"),
    ("Old Customized", "old-customized.md", "Dimensions & presets"),
    ("World seed", "world-seed.md", "Seeds & packaging"),
    ("Anomalous world seeds", "anomalous-world-seeds.md", "Seeds & packaging"),
    ("Data pack", "data-pack.md", "Seeds & packaging"),
]

# Wrapper/chrome classes to delete outright.
KILL_CLASS = re.compile(
    r"\b(navbox|nmbox|toc|toctitle|mw-editsection|mw-jump-link|mw-empty-elt|"
    r"noprint|metadata|ambox|catlinks|printfooter|mw-cite-backlink|"
    r"mw-indicators|infobox-imagearea|gallerybox|thumbcaption|magnify|"
    r"video-container|audio-button|mcwiki-audio|mw-collapsible-toggle|"
    r"mw-file-element|tabber-tabs|nowrap-links|sprite-file|noscript)\b"
)

_TPL_CACHE = {}
KILL_TAG = ("script", "style", "audio", "video", "sup.reference-nonexistent")


class WikiConverter(MarkdownConverter):
    """Markdownify with wiki-specific handling: drop images, keep link text only."""

    def convert_img(self, el, text, parent_tags=None):
        return ""

    def convert_a(self, el, text, parent_tags=None):
        # Wiki-internal links carry no value offline; keep the anchor text.
        href = el.get("href", "")
        if href.startswith("/w/") or href.startswith("#") or not href:
            return text
        if href.startswith("//") or href.startswith("http"):
            href = "https:" + href if href.startswith("//") else href
            return f"[{text}]({href})" if text.strip() else ""
        return text


def md(html):
    return WikiConverter(heading_style="ATX", bullets="-", strip=["img"]).convert(html)


CACHE = "cache"


def fetch(title):
    os.makedirs(CACHE, exist_ok=True)
    key = os.path.join(CACHE, re.sub(r"[^A-Za-z0-9]+", "_", title) + ".json")
    if os.path.exists(key):
        with open(key, encoding="utf-8") as f:
            return json.load(f)
    p = _fetch_live(title)
    with open(key, "w", encoding="utf-8") as f:
        json.dump(p, f)
    time.sleep(1.0)
    return p


def _fetch_live(title):
    url = (
        f"{API}?action=parse&format=json&formatversion=2&redirects=1"
        f"&prop=text|revid|displaytitle&page={urllib.parse.quote(title)}"
    )
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.loads(r.read().decode("utf-8"))
    if "error" in data:
        raise RuntimeError(f"{title}: {data['error'].get('info')}")
    return data["parse"]


def resolve_inherits(soup, depth=0):
    """Inline `load-page` lazy transclusions (Template:Nbt inherit/...).

    These hold the shared field definitions (block state, int/height providers,
    block predicates) that the page's JSON trees depend on. MediaWiki's parse API
    returns them unexpanded because the wiki loads them client-side.
    """
    if depth > 2:
        return
    for el in soup.select("li.load-page, div.load-page, .load-page"):
        if el.decomposed or el.parent is None:
            continue
        page = el.get("data-page")
        if not page:
            continue
        if page not in _TPL_CACHE:
            try:
                _TPL_CACHE[page] = fetch(page)["text"]
                time.sleep(0.4)
            except Exception as e:  # noqa: BLE001
                print(f"   ! inherit {page}: {e}")
                _TPL_CACHE[page] = None
        html = _TPL_CACHE[page]
        if not html:
            el["class"] = []
            continue
        sub = BeautifulSoup(html, "html.parser")
        for t in sub.find_all("div", class_="mw-parser-output"):
            t.unwrap()
        resolve_inherits(sub, depth + 1)
        for n in el.select(".noscript"):
            n.decompose()
        label = el.get_text(" ", strip=True) or page.split("/")[-1]
        el.clear()
        el.append(sub.new_string(f"{label} — inherited from {page}:"))
        for child in list(sub.children):
            el.append(child.extract())


def clean(html):
    soup = BeautifulSoup(html, "html.parser")
    resolve_inherits(soup)

    for tag in soup.find_all(["script", "style", "audio", "video", "figure"]):
        tag.decompose()
    for tag in soup.find_all(attrs={"class": True}):
        if tag.decomposed or tag.parent is None:
            continue
        classes = " ".join(tag.get("class") or [])
        if KILL_CLASS.search(classes):
            tag.decompose()
    for tag in soup.find_all(id=re.compile(r"^(toc|siteSub|contentSub)$")):
        tag.decompose()
    # MediaWiki parser output wrapper noise
    for tag in soup.find_all("div", class_="mw-parser-output"):
        tag.unwrap()
    for tag in soup.find_all(["span"], class_=re.compile(r"mw-headline")):
        tag.unwrap()

    # Collapse `<li>` wrappers that hold nothing but a nested list (an artifact of
    # inlining the inherit templates) so bullets don't gain a phantom level.
    for li in soup.find_all("li"):
        if li.decomposed or li.parent is None:
            continue
        kids = [c for c in li.children if getattr(c, "name", None) or str(c).strip()]
        if len(kids) == 1 and getattr(kids[0], "name", None) in ("ul", "ol"):
            li.unwrap()
    return str(soup)


# Inline markers the wiki uses for version-conditional content.
MARKERS = [
    (re.compile(r"upcoming:?\s*(?:JE|BE|Java Edition|Bedrock Edition)?[^\]\n*]*", re.I), "upcoming"),
    (re.compile(r"until:?\s*(?:JE|BE)?\s*[\d.]+", re.I), "removed / changed since"),
    (re.compile(r"\bbefore:?\s*(?:JE|BE)\s*[\d.]+", re.I), "pre-version"),
]

TARGET = (1, 20, 1)


def _ver(tok):
    """Parse a Java version token; return a tuple, or None if not a version."""
    tok = tok.strip().strip("*_ ")
    m = re.fullmatch(r"(\d+)\.(\d+)(?:\.(\d+))?", tok)
    if not m:
        return None
    return tuple(int(g) for g in m.groups(default="0"))


def inline_report(body):
    """Lines carrying the wiki's own inline version markers, outside History."""
    hist = body.find("\n## History")
    scan = body[:hist] if hist > 0 else body
    hits = {}
    for line in scan.splitlines():
        s = line.strip()
        if not s or len(s) > 800:
            continue
        for pat, kind in MARKERS:
            m = pat.search(s)
            if m:
                hits.setdefault(kind, []).append(re.sub(r"\s+", " ", s))
                break
    return hits


def history_report(body):
    """Extract Java Edition history entries for versions newer than 1.20.1.

    The History tables are the wiki's authoritative record of when each field or
    behaviour appeared, so anything listed here postdates the mod's target version.
    """
    i = body.find("\n## History")
    if i < 0:
        return [], False
    hist = body[i:]
    # Restrict to the Java Edition table; Bedrock uses an unrelated version scheme.
    jm = re.search(r"\n###\s*\*?Java Edition\*?", hist)
    if jm:
        hist = hist[jm.end() :]
        bm = re.search(r"\n###\s*\*?Bedrock Edition\*?", hist)
        if bm:
            hist = hist[: bm.start()]

    out, cur, cur_label, upcoming = [], None, "", False
    for line in hist.splitlines():
        if not line.strip().startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        cells = [c for c in cells if c and set(c) != {"-"}]
        if not cells:
            continue
        if re.match(r"^Upcoming", cells[0], re.I):
            upcoming = True
            continue
        v = None
        for c in cells[:3]:
            v = _ver(c)
            if v:
                cur, cur_label = v, c.strip().strip("*_ ")
                break
        if cur is None or cur <= TARGET:
            continue
        desc = cells[-1]
        if _ver(desc) or len(desc) < 12:
            continue
        out.append((cur_label, upcoming, re.sub(r"\s+", " ", desc)))
    return out, upcoming


def squash(text):
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"\xa0", " ", text)
    text = re.sub(r"^\s*\|\s*\|\s*$", "", text, flags=re.M)
    return text.strip() + "\n"


def main():
    os.makedirs(OUT, exist_ok=True)
    index = []
    for title, fname, cat in PAGES:
        try:
            p = fetch(title)
        except Exception as e:  # noqa: BLE001
            print(f"FAIL {title}: {e}")
            continue
        body = squash(md(clean(p["text"])))
        inline = inline_report(body)
        changes, _ = history_report(body)

        head = [
            f"# {p['title']}",
            "",
            f"> **Source:** <https://minecraft.wiki/w/{urllib.parse.quote(title.replace(' ', '_'))}>  ",
            f"> **Revision:** {p.get('revid')} · **Retrieved:** 2026-07-28  ",
            "> **Target version:** this text describes the *latest* Minecraft release. "
            "FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe "
            "behaviour that does not exist in the target version. See the flags below, and "
            "treat the page's own **History** section as authoritative.",
            "",
            "",
        ]

        if changes:
            head += [
                "## ⚠ Post-1.20.1 changes on this page",
                "",
                f"_{len(changes)} Java Edition history entr{'y' if len(changes) == 1 else 'ies'} "
                "newer than 1.20.1, extracted from this page's History table. Anything described "
                "here is **not** in 1.20.1 and the page body may document it as if it were current._",
                "",
            ]
            for label, upcoming, desc in changes[:40]:
                tag = " *(unreleased)*" if upcoming else ""
                head.append(f"- **{label}**{tag} — {desc[:400]}")
            if len(changes) > 40:
                head.append(f"- _…{len(changes) - 40} more; see the History section below._")
            head.append("")

        if inline:
            head += [
                "## Inline version markers",
                "",
                "_The wiki's own inline `upcoming` / `until` annotations, outside History._",
                "",
            ]
            for kind, items in inline.items():
                uniq = list(dict.fromkeys(items))
                head.append(f"**{kind}** — {len(items)} occurrence(s):")
                head.append("")
                for line in uniq[:12]:
                    head.append(f"- {line[:260]}")
                if len(uniq) > 12:
                    head.append(f"- _…{len(uniq) - 12} more_")
                head.append("")

        if changes or inline:
            head += ["---", ""]

        # Drop the duplicated H1 markdownify may emit
        body = re.sub(r"^#\s+" + re.escape(p["title"]) + r"\s*\n", "", body)
        out = "\n".join(head) + body
        with open(os.path.join(OUT, fname), "w", encoding="utf-8") as f:
            f.write(out)
        index.append((title, fname, cat, len(out), len(changes)))
        print(f"OK {fname:34s} {len(out):7d} B  post-1.20.1={len(changes):3d}  inline={sum(len(v) for v in inline.values())}")

    with open(os.path.join(OUT, "_index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, indent=1)


if __name__ == "__main__":
    main()
