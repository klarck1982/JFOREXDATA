#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Builds the self-contained TTFM archive book (site/index.html) + assets + manifest + zip."""
import base64, hashlib, html, os, re, shutil, subprocess, sys

ROOT = os.path.dirname(os.path.abspath(__file__))          # .../ttfm-project/archive
PROJ = os.path.dirname(ROOT)                                # .../ttfm-project
SITE = os.path.join(ROOT, "site")

# ---------------------------------------------------------------- mini markdown
INLINE = [
    (re.compile(r"`([^`]+)`"), lambda m: "<code>%s</code>" % m.group(1)),
    (re.compile(r"\*\*([^*]+)\*\*"), lambda m: "<strong>%s</strong>" % m.group(1)),
    (re.compile(r"(?<!\*)\*([^*]+)\*(?!\*)"), lambda m: "<em>%s</em>" % m.group(1)),
    (re.compile(r"\[([^\]]+)\]\((https?://[^)]+)\)"), lambda m: '<a href="%s">%s</a>' % (m.group(2), m.group(1))),
]

def inline(s):
    s = html.escape(s, quote=False)
    for rx, fn in INLINE:
        s = rx.sub(fn, s)
    return s

def md2html(text):
    out, lines, i, n = [], text.split("\n"), 0, len(text.split("\n"))
    while i < n:
        ln = lines[i]
        if ln.startswith("```"):
            buf = []
            i += 1
            while i < n and not lines[i].startswith("```"):
                buf.append(lines[i]); i += 1
            i += 1
            out.append("<pre class='mdcode'>%s</pre>" % html.escape("\n".join(buf)))
            continue
        if ln.startswith("#"):
            lv = len(ln) - len(ln.lstrip("#"))
            out.append("<h%d>%s</h%d>" % (lv, inline(ln[lv:].strip()), lv))
            i += 1; continue
        if ln.strip() in ("---", "***"):
            out.append("<hr>"); i += 1; continue
        if ln.startswith("|"):
            rows = []
            while i < n and lines[i].startswith("|"):
                rows.append(lines[i]); i += 1
            cells = [r.strip().strip("|").split("|") for r in rows]
            cells = [c for c in cells if not all(set(x.strip()) <= set("-: ") for x in c)]
            t = ["<table>"]
            for k, c in enumerate(cells):
                tag = "th" if k == 0 else "td"
                t.append("<tr>" + "".join("<%s>%s</%s>" % (tag, inline(x.strip()), tag) for x in c) + "</tr>")
            t.append("</table>")
            out.append("\n".join(t)); continue
        if ln.startswith("> "):
            buf = []
            while i < n and lines[i].startswith("> "):
                buf.append(lines[i][2:]); i += 1
            out.append("<blockquote>%s</blockquote>" % inline(" ".join(buf)))
            continue
        if re.match(r"^\s*[-*] ", ln):
            buf = []
            while i < n and re.match(r"^\s*[-*] ", lines[i]):
                buf.append("<li>%s</li>" % inline(re.sub(r"^\s*[-*] ", "", lines[i]))); i += 1
            out.append("<ul>%s</ul>" % "".join(buf)); continue
        if re.match(r"^\s*\d+\. ", ln):
            buf = []
            while i < n and re.match(r"^\s*\d+\. ", lines[i]):
                buf.append("<li>%s</li>" % inline(re.sub(r"^\s*\d+\. ", "", lines[i]))); i += 1
            out.append("<ol>%s</ol>" % "".join(buf)); continue
        if ln.strip():
            buf = [ln]
            while i + 1 < n and lines[i + 1].strip() and not lines[i + 1].startswith(("#", "|", ">", "-", "```")) and not re.match(r"^\s*\d+\. ", lines[i + 1]):
                i += 1; buf.append(lines[i])
            out.append("<p>%s</p>" % inline(" ".join(buf)))
        i += 1
    return "\n".join(out)

# ---------------------------------------------------------------- helpers
def read(p):
    with open(p, "rb") as f:
        return f.read()

def data_uri(p, mime):
    return "data:%s;base64,%s" % (mime, base64.b64encode(read(p)).decode())

def sha(p):
    return hashlib.sha256(read(p)).hexdigest()

CSS = """
body{font-family:'Segoe UI',Tahoma,Arial,sans-serif;direction:rtl;margin:0;background:#f6f7fb;color:#1c2333;line-height:1.75}
header{background:#101828;color:#fff;padding:34px 8%;direction:rtl}
header h1{margin:0 0 6px;font-size:26px}
header p{margin:2px 0;color:#9fb3d9;font-size:14px}
main{max-width:1050px;margin:0 auto;padding:26px 4% 80px}
nav.toc{background:#fff;border:1px solid #dde3f0;border-radius:10px;padding:14px 22px;margin:22px 0}
nav.toc a{display:block;color:#1d4ed8;text-decoration:none;padding:2px 0}
h2{border-bottom:3px solid #1d4ed8;padding-bottom:6px;margin-top:44px;font-size:22px}
h3{color:#17356b;margin-top:26px}
h4{color:#3b4a6b}
table{border-collapse:collapse;width:100%;margin:12px 0;background:#fff;font-size:14px}
th,td{border:1px solid #d4dcec;padding:7px 10px;text-align:right;vertical-align:top}
th{background:#eef2fb}
pre,pre.mdcode{background:#0f172a;color:#e2e8f0;direction:ltr;text-align:left;padding:14px;border-radius:8px;overflow:auto;font-size:12.5px;line-height:1.55;white-space:pre}
code{background:#e8edf8;color:#12306b;padding:1px 5px;border-radius:4px;font-size:.92em}
pre code{background:none;color:inherit;padding:0}
blockquote{border-right:4px solid #f59e0b;background:#fff8ea;margin:10px 0;padding:8px 14px}
figure{margin:18px 0;background:#fff;border:1px solid #dde3f0;border-radius:10px;padding:12px}
figure img{max-width:100%;border-radius:6px;border:1px solid #c9d3e8}
figcaption{font-size:13px;color:#44507a;margin-top:8px}
hr{border:none;border-top:1px dashed #b9c4dd;margin:26px 0}
a{color:#1d4ed8}
.badge{display:inline-block;background:#16a34a;color:#fff;border-radius:6px;padding:2px 10px;font-size:12px;margin-left:6px}
.badge.warn{background:#d97706}
.dl{display:inline-block;margin:4px 0 4px 8px;background:#1d4ed8;color:#fff;padding:6px 14px;border-radius:8px;text-decoration:none;font-size:13px}
footer{background:#101828;color:#9fb3d9;padding:20px 8%;font-size:13px}
"""

CAPTIONS = {
    "image_2026-09-06_224921764.png": "صورة رسمية: قاعدة EQ الشرطية (منتصف الفتيل الأطول) — أساس قفل خطوة 1.",
    "indicator-example-smt.png": "_chart رسمي: سلّم الإسقاطات، ساق C2 برتقالية، EQ أزرق، مناطق خضراء، حدود T-Spot منقطة، وخط SMT.",
    "image_2026-09-07_011806120.png": "شارت المستخدم الحي مع مؤشرنا بعد خطوة 3: حالات CISD رمادي/برتقالي/أحمر + أسهم المنصة البرتقالية (لا تُحتسب).",
    "image_2026-09-07_111350637.png": "تحقق المستخدم: TradingView يسار / JForex يمين — تطابق شموع 4H والعدّاد.",
    "image_2026-09-07_114022381.png": "صورة رسمية EURAUD 5m (شتاءً): مرجع إعدادات T-Spot + هدف تدقيق الحسابات بالقياس البكسلي.",
    "image_2026-09-07_145108499.png": "شارت المستخدم USATECH 30m: مصدر شكوى وقت اللوحة (سُويت بخيار [Panel] Time Line).",
    "image_2026-09-08_114206258.png": "صورة التجربة الحالية: شارت مزدوج USATECH/USA500 15m — تباعد Bias، إزاحة ترقيم C، مثلث تحذير ES.",
}

def main():
    if os.path.isdir(SITE):
        shutil.rmtree(SITE)
    for d in ("assets/code", "assets/docs", "assets/img", "assets/video"):
        os.makedirs(os.path.join(SITE, d))

    # ---- copy raw assets
    copies = [
        (os.path.join(PROJ, "jforex/TTFMCore.java"), "assets/code/TTFMCore.java"),
        (os.path.join(PROJ, "jforex/tests/TTFMCoreCoreTest.java"), "assets/code/TTFMCoreCoreTest.java"),
        (os.path.join(PROJ, "jforex/docs/settings-guide.md"), "assets/docs/settings-guide.md"),
        (os.path.join(PROJ, "jforex/docs/video-match-notes.md"), "assets/docs/video-match-notes.md"),
        (os.path.join(PROJ, "jforex/docs/tspot-pixel-verification.md"), "assets/docs/tspot-pixel-verification.md"),
        (os.path.join(PROJ, "jforex/docs/eq-tspot-visual.svg"), "assets/docs/eq-tspot-visual.svg"),
        (os.path.join(PROJ, "jforex/docs/c-columns-timing.svg"), "assets/docs/c-columns-timing.svg"),
        (os.path.join(PROJ, "jforex/tools/verify_tspot.py"), "assets/docs/verify_tspot.py"),
        (os.path.join(PROJ, "pdf/TTFM-Reference.pdf"), "assets/docs/TTFM-Reference.pdf"),
        (os.path.join(ROOT, "discussion-log.md"), "discussion-log.md"),
        (os.path.join(ROOT, "PUBLISH-AR.md"), "PUBLISH-AR.md"),
        (os.path.join(ROOT, "publish_gist.sh"), "publish_gist.sh"),
    ]
    up = "/home/user/uploads"
    for f in sorted(os.listdir(up)):
        p = os.path.join(up, f)
        if f.endswith(".png"):
            copies.append((p, "assets/img/" + f))
        elif f.endswith(".txt") and f.startswith("TTFM-Fractal"):
            copies.append((p, "assets/video/TTFM-Fractal-Example.mp4"))
        elif f == "HigherTFCandles.txt":
            copies.append((p, "assets/code/HigherTFCandles-reference.java"))
    for src, dst in copies:
        shutil.copyfile(src, os.path.join(SITE, dst))

    # ---- manifest of everything inside site/assets
    man = []
    for base, _dirs, fs in os.walk(os.path.join(SITE, "assets")):
        for f in sorted(fs):
            p = os.path.join(base, f)
            man.append("%s  %s" % (sha(p), os.path.relpath(p, SITE).replace(os.sep, "/")))
    for f in ("discussion-log.md", "PUBLISH-AR.md", "publish_gist.sh"):
        man.append("%s  %s" % (sha(os.path.join(SITE, f)), f))
    with open(os.path.join(SITE, "MANIFEST.txt"), "w") as fh:
        fh.write("\n".join(man) + "\n")

    # ---- html pieces
    disc = md2html(read(os.path.join(SITE, "discussion-log.md")).decode("utf-8"))
    pub = md2html(read(os.path.join(SITE, "PUBLISH-AR.md")).decode("utf-8"))
    guide = md2html(read(os.path.join(SITE, "assets/docs/settings-guide.md")).decode("utf-8"))
    vnotes = md2html(read(os.path.join(SITE, "assets/docs/video-match-notes.md")).decode("utf-8"))
    pver = md2html(read(os.path.join(SITE, "assets/docs/tspot-pixel-verification.md")).decode("utf-8"))

    def code_block(rel):
        txt = read(os.path.join(SITE, rel)).decode("utf-8", "replace")
        return "<pre><code>%s</code></pre>" % html.escape(txt)

    figs = []
    for f in sorted(os.listdir(os.path.join(SITE, "assets/img"))):
        p = os.path.join(SITE, "assets/img", f)
        figs.append("<figure><img src='%s' alt='%s'><figcaption><strong>%s</strong> — %s</figcaption></figure>"
                    % (data_uri(p, "image/png"), f, f, CAPTIONS.get(f, "")))
    figs = "\n".join(figs)

    man_rows = "\n".join("<tr><td style='direction:ltr;text-align:left'><code>%s</code></td><td style='direction:ltr;text-align:left'>%s</td></tr>"
                         % (h, fn) for h, fn in (l.split("  ") for l in man))

    java_uri = data_uri(os.path.join(SITE, "assets/code/TTFMCore.java"), "text/plain")
    test_uri = data_uri(os.path.join(SITE, "assets/code/TTFMCoreCoreTest.java"), "text/plain")
    pdf_uri = data_uri(os.path.join(SITE, "assets/docs/TTFM-Reference.pdf"), "application/pdf")
    vid_uri = data_uri(os.path.join(SITE, "assets/video/TTFM-Fractal-Example.mp4"), "video/mp4")
    svg1 = data_uri(os.path.join(SITE, "assets/docs/eq-tspot-visual.svg"), "image/svg+xml")
    svg2 = data_uri(os.path.join(SITE, "assets/docs/c-columns-timing.svg"), "image/svg+xml")

    doc = """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>TTFM Fractal Model — الكتاب الكامل للمشروع (نقاش + كود)</title>
<style>%s</style>
</head>
<body>
<header>
<h1>TTFM Fractal Model — الأرشيف الكامل</h1>
<p>سجل النقاش كاملاً + الكود الكامل + الصور الرسمية وصور المستخدم + الأدلة والاختبارات — ملف واحد ذاتي الاكتفاء.</p>
<p>تاريخ التجميع: 2026-09-08 &nbsp;|&nbsp; حالة المؤشر: الخطوات 1-3 مقفلة، 133/133 اختباراً ناجحاً، 55 خياراً</p>
</header>
<main>
<nav class="toc"><strong>المحتويات</strong>
<a href="#c1">1) سجل النقاش الكامل (قرارات، أقفال، تصويبات)</a>
<a href="#c2">2) الكود الكامل: TTFMCore.java + الاختبارات 133/133</a>
<a href="#c3">3) معرض الصور مع ملاحظات الفحص</a>
<a href="#c4">4) الأدلة البصرية SVG + دليل الإعدادات + ملاحظات الفيديو والتحقق البكسلي</a>
<a href="#c5">5) تنزيلات: PDF المرجعي، الفيديو الرسمي، الكود الخام</a>
<a href="#c6">6) manifest السلامة SHA-256</a>
<a href="#c7">7) دليل النشر على الإنترنت</a>
</nav>

<h2 id="c1">1) سجل النقاش الكامل</h2>
%s

<h2 id="c2">2) الكود الكامل</h2>
<p>النص الحرفي الحالي للمؤشر وملف الاختبارات (يُترجم ويجري 133/133):</p>
<a class="dl" download="TTFMCore.java" href="%s">تنزيل TTFMCore.java</a>
<a class="dl" download="TTFMCoreCoreTest.java" href="%s">تنزيل الاختبارات</a>
<h3>TTFMCore.java</h3>
%s
<h3>TTFMCoreCoreTest.java</h3>
%s

<h2 id="c3">3) معرض الصور مع ملاحظات الفحص</h2>
%s

<h2 id="c4">4) الأدلة البصرية والإعدادات</h2>
<h3>4.1 رسم EQ وT-Spot (SVG)</h3>
<figure><img src="%s" alt="eq-tspot"></figure>
<h3>4.2 توقيت أعمدة C (SVG)</h3>
<figure><img src="%s" alt="c-columns"></figure>
<h3>4.3 دليل الإعدادات الكامل</h3>
%s
<h3>4.4 ملاحظات مطابقة الفيديو الرسمي</h3>
%s
<h3>4.5 التحقق البكسلي لـ T-Spot</h3>
%s

<h2 id="c5">5) تنزيلات</h2>
<a class="dl" download="TTFM-Reference.pdf" href="%s">تنزيل المرجع PDF</a>
<a class="dl" download="TTFMCore.java" href="%s">TTFMCore.java</a>
<a class="dl" download="TTFMCoreCoreTest.java" href="%s">الاختبارات</a>
<h3>الفيديو الرسمي (مضمّن)</h3>
<video controls style="max-width:100%%;border-radius:8px" src="%s"></video>

<h2 id="c6">6) manifest السلامة SHA-256</h2>
<p>كل أصل داخل الأرشيف له بصمة؛ بعد أي تنزيل أو نشر شغّل <code>sha256sum -c MANIFEST.txt</code> من جذر موقع site:</p>
<table><tr><th>SHA-256</th><th>الملف</th></tr>
%s
</table>

<h2 id="c7">7) دليل النشر على الإنترنت</h2>
%s

</main>
<footer>
TTFM Fractal Model Archive — جُمع آلياً من Workspace المشروع وسجل الجلسة. المصادر النظرية: ttrades.com (Next Day Model، Full Guide، Projections Guide).
</footer>
</body></html>
""" % (CSS, disc, java_uri, test_uri,
       code_block("assets/code/TTFMCore.java"), code_block("assets/code/TTFMCoreCoreTest.java"),
       figs, svg1, svg2, guide, vnotes, pver,
       pdf_uri, java_uri, test_uri, vid_uri, man_rows, pub)

    with open(os.path.join(SITE, "index.html"), "w", encoding="utf-8") as fh:
        fh.write(doc)

    # ---- zip
    zip_path = os.path.join(ROOT, "ttfm-archive.zip")
    if os.path.exists(zip_path):
        os.remove(zip_path)
    subprocess.run(["zip", "-qr", zip_path, "site", "discussion-log.md", "PUBLISH-AR.md", "publish_gist.sh"],
                   cwd=ROOT, check=True)
    print("index.html bytes:", os.path.getsize(os.path.join(SITE, "index.html")))
    print("zip bytes:", os.path.getsize(zip_path))
    print("assets:", sum(len(f) for _, _, f in os.walk(os.path.join(SITE, "assets"))))

if __name__ == "__main__":
    main()
