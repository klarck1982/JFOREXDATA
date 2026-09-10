# JFOREXDATA — مشروع TTFM Fractal Model

كل شيء: سجل النقاش الكامل، الكود (النسخة الكاملة + النسخة الجوهرية)، الاختبارات،
الصور الرسمية وصور المستخدم، الأدلة، المرجع PDF، والفيديو الرسمي.
آخر تحديث: 2026-09-10

## الفهرس

| المسار | المحتوى |
|---|---|
| `archive/site/index.html` | **الكتاب الكامل** — ملف HTML واحد ذاتي الاكتفاء: سجل النقاش (11 فصلاً) + الكود مضمناً + كل الصور مع ملاحظات الفحص البكسلي + الأدلة + PDF والفيديو مضمّنان |
| `archive/ttfm-archive.zip` | الحزمة الكاملة مضغوطة (كتاب + أصول + manifest) |
| `archive/discussion-log.md` | سجل النقاش كنص Markdown مستقل |
| `archive/PUBLISH-AR.md` | دليل نشر الأرشيف على الإنترنت (4 طرق) |
| `archive/MANIFEST inside site/` | بصمات SHA-256 لكل أصل — للتحقق من عدم الفقدان |
| `jforex/TTFMCore.java` | المؤشر الكامل (55 خياراً) — الخطوات 1-3 مقفلة |
| `jforex/TTFMEssence.java` | **النسخة الجوهرية** — 20 خياراً (CISD Desk 15 + Sessions 2 + Bucketing 1 + SMT 1 + Timer 1)، تسعة عناصر + session overlay + شبكات TV لكل رمز + SMT panel-only + عدّاد تنازلي |
| `jforex/tests/` | 134 اختباراً للـ Core + 216 للـ Essence |
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
java -cp _compilecheck/out com.dukascopy.indicators.TTFMCoreCoreTest   # 134/134
java -cp _compilecheck/out com.dukascopy.indicators.TTFMEssenceTest    # 216/216
```

## حالة المشروع
- مقفول: خطوة 1 (4H+D+EQ+T-Spot)، خطوة 2 (الإغلاقات+التظليل)، دفعة المطابقة الرسمية، خطوة 3 (محرك CISD).
- Essence: تسعة عناصر مقفلة بالقائمة («اعتمد القائمة») + تصويحات 2026-09-08b/c (وسوم المنصة مشطوبة، الحالات وTP مطفأة رسماً).
- Bias في Essence: Next-Day Model الرسمي على الشمعة اليومية (مع حالة Neutral).
- SMT: محرك اختياري «لوحة فقط» (اتفاق 2026-09-10) — تباعد بين الأسواق عبر SharedSMT.csv (بواعث K=2 مكتملة، لا إعادة رسم)، مطفأ افتراضياً = صفر أثر، قابل للإزالة ككتلة واحدة؛ زوج الذهب تجريبي (`?`).
- العدّاد التنازلي (اتفاق 2026-09-10، قرار D6 = ساعة حائط): `HH:MM:SS` فوق تسمية TF على أعلى شمعة بكل عنقود (4H + D) حتى إغلاق شمعة الطبقة الحالية، افتراضي ON — **قابل للحذف ككتلة واحدة إن لم يلْبِ المطلوب** (REMOVAL note في الكود).
- **«اعتمد» 2026-09-09 (Essence):** session overlay (خط NY 08:00 بشارة + سطر الجلسة، opt-in) + إعادة تثبيت التجميع على EET+DST (أثينا، مطابقة TV وإصلاح انزياح الشتاء) + شبكات TV لكل رمز (EET للمؤشرات/العملات، Brussels للذهب، خيار `[Bucketing] Grid` + سطر `Grid:` دائم).
- نقطة التشغيل: JForex على `Day start = EET` دائماً (على الذهب المنصة تختلف والمؤشر هو المرجع المطابق لـ TV).

## المصادر النظرية
ttrades.com — Next Day Model، Full Guide، Projections Guide، Equilibrium in Continuations.
