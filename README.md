# JFOREXDATA — مشروع TTFM Fractal Model

كل شيء: سجل النقاش الكامل، الكود (النسخة الكاملة + النسخة الجوهرية)، الاختبارات،
الصور الرسمية وصور المستخدم، الأدلة، المرجع PDF، والفيديو الرسمي.
آخر تحديث: 2026-09-08

## الفهرس

| المسار | المحتوى |
|---|---|
| `archive/site/index.html` | **الكتاب الكامل** — ملف HTML واحد ذاتي الاكتفاء: سجل النقاش (11 فصلاً) + الكود مضمناً + كل الصور مع ملاحظات الفحص البكسلي + الأدلة + PDF والفيديو مضمّنان |
| `archive/ttfm-archive.zip` | الحزمة الكاملة مضغوطة (كتاب + أصول + manifest) |
| `archive/discussion-log.md` | سجل النقاش كنص Markdown مستقل |
| `archive/PUBLISH-AR.md` | دليل نشر الأرشيف على الإنترنت (4 طرق) |
| `archive/MANIFEST inside site/` | بصمات SHA-256 لكل أصل — للتحقق من عدم الفقدان |
| `jforex/TTFMCore.java` | المؤشر الكامل (55 خياراً) — الخطوات 1-3 مقفلة |
| `jforex/TTFMEssence.java` | **النسخة الجوهرية** — إعداد واحد يتيم `[CISD] Min Wave Length`، تسعة عناصر مقفلة |
| `jforex/tests/` | 133 اختباراً للـ Core + 70 للـ Essence |
| `jforex/docs/` | دليل الإعدادات، رسوم SVG، ملاحظات الفيديو، التحقق البكسلي، فريمات الفيديو |
| `jforex/tools/` | أدوات التحقق البكسلي (verify_tspot.py وغيرها) |
| `jforex/*_backup.java` | نسخ مقفلة لكل خطوة (1، 2، 2b، 3، v2، v3full) |
| `pdf/TTFM-Reference.pdf` | المرجع ثلاثي الطبقات (مفهوم/ميكانيكا/إعدادات) |
| `uploads/` | الصور الرسمية + صور المستخدم + الفيديو الرسمي (mp4) + مرجع Java الأصلي |
| `ttrades-fractal-model-report.md` | التقرير البحثي الأصلي |

## الترجمة والاختبار (محلياً)

```bash
cd jforex
javac -nowarn -encoding UTF-8 -d _compilecheck/out -sourcepath _compilecheck/stubs \
      TTFMCore.java TTFMEssence.java tests/TTFMCoreCoreTest.java tests/TTFMEssenceTest.java
java -cp _compilecheck/out com.dukascopy.indicators.TTFMCoreCoreTest   # 133/133
java -cp _compilecheck/out com.dukascopy.indicators.TTFMEssenceTest    # 70/70
```

## حالة المشروع
- مقفول: خطوة 1 (4H+D+EQ+T-Spot)، خطوة 2 (الإغلاقات+التظليل)، دفعة المطابقة الرسمية، خطوة 3 (محرك CISD).
- Essence: تسعة عناصر مقفلة بالقائمة («اعتمد القائمة») + تصويحات 2026-09-08b/c (وسوم المنصة مشطوبة، الحالات وTP مطفأة رسماً).
- Bias في Essence: Next-Day Model الرسمي على الشمعة اليومية (مع حالة Neutral).
- SMT: مؤجل لما بعد نجاح التجربة.
- بانتظار: التحقق البصري للمستخدم ثم قفل «اعتمد Essence».

## المصادر النظرية
ttrades.com — Next Day Model، Full Guide، Projections Guide، Equilibrium in Continuations.
