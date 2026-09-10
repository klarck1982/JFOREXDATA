# تقرير المراجعة الكاملة — TTFMCore.java / TTFMEssence.java
**التاريخ:** 2026-09-10 · **الفرع:** main @ 6830285 · **الحالة:** الكود يعمل — هذه مراجعة حرفية قبل أي تعديل
**منهجية:** حفظ 23 وثيقة رسمية (انظر `docs/jforex-api/README.md`) + تحقق مباشر من Javadoc الرسمي (انظر `docs/jforex-api/javadoc-verified-facts.md`) + قراءة سطرية للكود + إعادة تجميع وتشغيل الاختبارات.

**الأساس الحالي (أخضر):**
- التجميع: نظيف (stubs + الملفان + اختبارا الوحدات).
- `TTFMCoreCoreTest`: **134/134 PASS** · `TTFMEssenceTest`: **216/216 PASS**.

---

## أولاً — الخلاصة

الكود **مطابق لدورة حياة IIndicator الرسمية** (onStart / calculate / IndicatorResult / NaN-gap / drawnByIndicator)، ولا يوجد خلل وظيفي في المحرك الأساسي (الطبقات، الأعمدة، CISD، SMT-Engine، الجورنال). ما وجدته:

| # | الخطورة | الملف | الملخص |
|---|---------|-------|--------|
| 1 | **HIGH** | Core | `fetchSmt()` يسحب التاريخ (حتى 7× `getBars`) **في كل tick** |
| 2 | **HIGH→مؤكّد** | Core | خريطة أسماء SMT تعمل، لكنها هشة وصامتة (بدون logging) — الحل الرسمي `Instrument.fromString` |
| 3 | MEDIUM | Essence | تسريب `Clip` صوتي في كل تنبيه (لا `close()`) |
| 4 | MEDIUM | Essence | `playSound` يبحث في `user.dir` وليس في المجلد الرسمي `getFilesDir()` |
| 5 | MEDIUM | كلاهما | `displayList()` يُستدعى **داخل حلقة كل شموع** عند ملء المخرجات (O(N×W)) |
| 6 | MEDIUM | Core | حسابات ميتة في كل calculate: `revBullMask/revBearMask` (دعوتا `reversalStages` O(n)) + `profileName/profileStartMs` — تُكتب ولا تُقرأ |
| 7 | LOW | كلاهما | عناوين C-column تستخدم `openTime+periodMs` ثابت → انحراف ساعة تجميلية في أيام DST |
| 8 | LOW | Core | مسار الجورنال بدمج سلاسل (`File + separator`) بدل `new File(dir, name)` |
| 9 | LOW | Essence | خطوط CISD: عندما يخرج الحرف الأيسر عن الشاشة يُهمل الخط كله بدل قصّه |
| 10 | LOW | Essence | كود ميت: import `Instrument`، `Signal`+`MAX_SIGNALS`، `c2WickMid`، `lastAlertStartTime/lastAlertLevel`، `cisdStoredDeactivationTime` |
| 11 | LOW | كلاهما | `sparceIndicator=true` موصى به رسمياً للمخرجات المتفرقة (اختياري — لا أثر وظيفي حالي) |
| 12 | LOW | — | `IStopListener` غير مسجّل — **ليس خطأ** (لا موارد دائمة) لكن المستند يوثّق كيفية تفعيله |
| 13 | LOW | كلاهما | سباق مقبول على CSVs المشتركة (تلتئم ذاتياً) — التصميم صحيح بالوثائق |
| 14 | INFO | كلاهما | فرع XAU/EUR ميّت (لا يوجد `XAUEUR` في enum الرسمي) — ضارّ بصمت |
| 15 | INFO | كلاهما | نسخ `RB[]` كامل في كل calculate (حتى incremental) — تحسين أداء اختياري |

---

## ثانياً — التفاصيل

### 1) HIGH — Core: `fetchSmt` في كل tick (السطر 894)

```java
fetchSmt(periodMs);   // داخل calculate() — تُستدعى عند كل تحديث للشمعة المتكوّنة
```
- داخل `fetchSmt` (1497–1517): `fetchRB()` = `h.getBars(inst,p,OfferSide.BID,start,now)` لـ **120 شمعة** لكل من: الأداة نفسها + حتى 6 أدوات من `SMT_INSTRUMENTS` → **حتى 7 استعلامات تاريخ لكل tick** (الافتراضي `smtEnabled=true`، سطر 251).
- وثائقياً: `IHistory.getBars` داخل المؤشر مرخّص (access-historical-data.md)، لكن الاستعلامات المتكررة كل ثانية-عدّة لأدوات **غير مرسومة على المخطط** تُحمّل خدمة التاريخ بلا داعٍ؛ قيم SMT لا تتغير إلا عند إقفال شمعة جديدة على الأداة الأُخرى.
- **مقارنة مع Essence:** محرك SMT هناك **خالي من IHistory تماماً** (ملف SharedSMT.csv مشترك) → لا مشكلة.
- **الحلول المقترحة (بترتيب الأفضلية):**
  - أ) إعادة الجلب فقط عند تغيّر **إقفال** آخر شمعة على أداة الأقران (تخزين آخر مفتاح: `inst|period|lastClosedTime`) — أبسط وأدق.
  - ب) TTL مؤقت (مثلاً 30–60 ثانية) على نتائج `fetchRB`.
  - ج) جعل الافتراضي `smtEnabled=false` كما في Essence (قرار منتج — لا أنفّذه إلا بتعمّدك).

### 2) HIGH (تمّ التحقق — يعمل لكن هشّ) — Core: خريطة أسماء SMT

تمّ التحقق **من Javadoc الرسمي** (انظر `javadoc-verified-facts.md`):
- `Instrument.toString()` يعيد **صيغة العرض** `"EUR/USD"` / `"USATECH.IDX/USD"` → دوال `correlated()` و`shortSym()` وأعمدة الجورنال **متسقة معها ✓**.
- الثوابت الحقيقية: `USATECHIDXUSD` / `USA500IDXUSD` / `USA30IDXUSD` (بدون underscores) و`XAUUSD` **ولا يوجد `XAUEUR`**.
- سلسلتا `resolveInstrument` (سطر 1529): المحاولة الأولى `USATECH_IDX_USD` تفشل → السقطة `USATECHIDXUSD` تنجح ✓؛ `"EUR/USD"`→`"EURUSD"` ✓؛ `"XAU/EUR"`→ null → يُتخطى بأمان ✓.

**النتيجة:** SMT يعمل فعلاً (لا يوجد "صمت" في المسار الحالي)، **لكن**:
- الاعتماد على صيغة `toString()` بدون أي تسجيل؛ أي تغيّر مستقبلي في الصيغة يقتل SMT بصمت (كل شيء داخل try/catch فارغ).
- الطريقة الموثقة رسمياً للتحويل هي `Instrument.fromString("CUR1/CUR2")` (ترجع null إن لم يوجد).
- **المقترح:** استبدال `resolveInstrument` بـ `Instrument.fromString` + سطر تحذيري **مرة واحدة** في `console.getWarn()` عند فشل حلّ الأداة.

### 3) MEDIUM — Essence: تسريب `Clip` في `playSound` (سطر 1470)

```java
Clip clip = AudioSystem.getClip();
clip.open(audioIn); clip.start();      // لا close() ولا LineListener
```
- كل تنبيه يولّد `Clip` جديداً **لا يُغلق** → تسريب خط صوتي أصلي مع كل استدعاء → بعد تنبيهات كثيرة قد **يفشل الصوت كلياً** (نفاذ خطوط الصوت).
- **الحل الرسمي:** إعادة استخدام `Clip` واحد (حقل كلاس) مع `clip.stop()` قبل `open/start`، أو إغلاقه بعد انتهاء التشغيل عبر `LineListener` (حدث `STOP`) أو `Timer` بعد `clip.getMicrosecondLength()`.

### 4) MEDIUM — Essence: مسار الأصوات `user.dir` (سطر 1473)

```java
String userDir = System.getProperty("user.dir");
File soundFile = new File(userDir, filename);
if (!soundFile.exists()) return;   // صمت كامل إن غاب الملف
```
- Javadoc الرسمي لـ `IIndicatorContext.getFilesDir()`: *"Returns directory where reading and writing is allowed"* — هذا هو المجلد المأذون رسمياً.
- ملفات `alert.wav` / `retest.wav` موجودة في المستودع (`jforex/sounds/`) لكن الكود لا يبحث فيها ولا في `getFilesDir()`؛ المستخدم مطالب بنسخها يدوياً إلى مجلد تشغيل JForex.
- **المقترح:** البحث بالترتيب: `getFilesDir()/name` → `user.dir/name` (توافقاً)؛ وإن لم يُعثر → `console.getWarn()` مرة واحدة.

### 5) MEDIUM — كلاهما: `displayList()` داخل حلقة ملء المخرجات

Core سطر 902 / Essence المعادل (نهاية `calculate`):
```java
for (int idx = startIndex, a = 0; idx <= endIndex; idx++, a++) {
    List<CandleData> disp = displayList(primary);   // ← نسخة كاملة من historical في كل شمعة!
    ...
}
```
- `displayList` = `new ArrayList<>(layer.historical)` + trim → في إعادة الحساب الكاملة (N شمعة × حجم الكلستر W) = **N×W نسخة/تخصيصات** (مثلاً 20,000 شمعة × 20 = 400 ألف عنصر).
- القيمة **ثابتة خلال الـ calculate** (لا شيء يتغير داخل الحلقة) → **رفعها فوق الحلقة** (استدعاء واحد) = إصلاح بأمان تام.
- أيضاً في `drawOutput` (Essence سطر ~1870): `displayList(layer)` يُستدعى لكل طبقة في كل تمريرة `candleIdx` (حتى 4×/طبقة/repaint) → يُستحسن تخزين النتائج في حقل يُحدَّث من `calculate()`.

### 6) MEDIUM — Core: حسابات ميتة في كل calculate (سطور 1443–1447)

```java
revBullMask  = reversalStages(bars, bars.length-1, true,  chartAvgRange, lastSignalBull());
revBearMask  = reversalStages(bars, bars.length-1, false, chartAvgRange, lastSignalBear());
profileName  = profileOf(now, nyTZ); profileStartMs = profileStart(now, nyTZ);
```
- الحقول الأربعة **تُكتب ولا تُقرأ أبداً** (تأكدت بالgrep: لا قراءات).
- `reversalStages` مسح O(n) يُدفع **في كل tick** بلا فائدة؛ `smtLabel` (سطر 306) أيضاً يُكتب ولا يُقرأ (المستخدم هو `smtCracks`).
- **المقترح:** حذف الأربعة + الدعوّتين (zero-residue) — أو ربطها لوالية/لوحة إن أردتَ استخدامها مستقبلاً.

### 7) LOW — عناوين C-column في أيام DST

- التجميع (bucketing) عبر `periodStart` بـ Calendar في التوقيت الصحيح → **DST-safe ✓**.
- لكن العرض: `cd.openTime + periodMs` (24h/168h ثابتة) في عنوان العمود وعرض الحيز → في يوم انتقال DST يظهر زمن النهاية منحصراً بساعة (جمالي فقط؛ لا أثر على القيم أو الإشارة).
- **المقترح (اختياري):** حساب النهاية بـ `periodStart(openTime+periodMs)` أو عرض "نهاية اليوم" بدون +periodMs.

### 8) LOW — Core: بناء مسار الجورنال (سطر 1054)

```java
String path = context.getFilesDir() + File.separator + "TTFMCore_Signals.csv";
```
- يعمل (عبر `File.toString()`) لكن غير متناسق مع إصلاح Essence الرسمي (commit 8148b31): `new File(filesDir(), "TTFMCore_Signals.csv")`.

### 9) LOW — Essence: قصّ خطوط CISD (سطر ~1942)

```java
if (xStart < 0 || xBreakout < 0) continue;   // يهمل الخط كله إذا خرج حرفه الأيسر
```
- الحالة: إشارات محفوظة قديمة، طرفها الأيسر خارج الشاشة يساراً وطرفها الأيمن **ما زال مرئياً** → الخط يختفي جزئياً ظلمةً.
- **المقترح:** `xStart = Math.max(xStart, 0)` (قصّ) مع الإبقاء على `continue` فقط عندما `xBreakout < 0`.

### 10) LOW — Essence: كود ميت (فلسفة zero-residue)

| الرمز | المكان | الحالة |
|-------|--------|--------|
| `import com.dukascopy.api.Instrument` | سطر 144 | غير مستخدم |
| `class Signal` + `MAX_SIGNALS` | 215 / 160 | نسخة ميتة من Core (لا `new Signal` ولا قراءات) |
| `c2WickMid(CandleData)` | 860 | معرّفة ولم تُستدعَ أبداً |
| `lastAlertStartTime` / `lastAlertLevel` | 329–330 | تُكتب (1416) وتُصفَّر (1019) ولا تُقرأ |
| `cisdStoredDeactivationTime[]` | 346 | تُكتب/تُزاح (1369/1397/1585) ولا تُقرأ |

### 11) LOW — `sparceIndicator` (ملاحظة مطابقة وثائقية)

- parameter-configuration.md: *"For ZIGZAG … has calculated values only on candles where it changes direction … set sparceIndicator=true, telling system that it has values only for some of the candles. The same goes to indicators with LEVEL type outputs."*
- مخرجاتنا متفرقة فعلاً (OHLC الكلاستر + NaN) لكن **لا أثر وظيفي** لأن رسمنا لا يعتمد على `valuesArr` (نستخدم `getXForTime/getYForValue` مباشرة) → **اختياري**؛ إن أضيف لا يغيّر شيئاً الآن لكنه يوثّق نية التصميم.

### 12) LOW — `IStopListener` غير مسجّل

- execute-code-on-method-stop.md: استلام `onStop` يتطلب `IStopListener` + `context.getIndicatorsProvider().addIndicatorStopListener(this, this)`.
- **ليس خطأ لدينا:** لا نملك موارد دائمة (كل IO عبر try-with-resources، لا Threads، لا Handles) → لا حاجة. ملاحظة: stub المحلي لـ `IIndicatorContext` ناقص `getIndicatorsProvider` (موجود في API الحقيقي — موثّق في `javadoc-verified-facts.md`)، يُضاف إن أردتَ تفعيل onStop مستقبلاً.

### 13) LOW — سباق على CSVs المشتركة (تصميم مقبول)

- `SharedSMT.csv` / `SharedCISD.csv`: قراءة-تعديل-كتابة بقفل **لكل instance**؛ مخطآن في JVM واحد = قفلان مختلفان → قد يضيع أحد الكتابات المتزامنتين.
- **تلتئم ذاتياً:** كل instance يشارك أ Pivot/إشارته الجديدة في كل calculate، والكتابة تحدث عند تغيّر فقط → أثر السباق لحظي وبسيط.
- **وثائقياً:** لا يوجد أي قناة تواصل رسمية بين instances المؤشرات (لا broadcast؛ threading.md مخصص للاستراتيجيات، وinstances.md يؤكد تعدد JVMs) → **الملفات هي الآلية الوحيدة الممكنة** → التصميم صحيح؛ السباق قيد مقبول موثّق.

### 14) INFO — فرع XAU/EUR ميت

- لا يوجد `XAUEUR` في enum الرسمي (تأكدت: 0 مرات في Javadoc) → Core: `resolveInstrument`→null→تخطي صامت؛ Essence: المفتاح `"XAUEUR"` لا يمكن تحقيقه (لا مخطط JForex لذلك) → فرع تجريبي (`?`) لا يعمل عملياً. **بدون أثر ضار** — يُترك أو يُحذف مع الكود الميت (قرارك).

### 15) INFO — نسخ `RB[]` في كل calculate (حجم الأداء)

- `bars = new RB[ibars.length]` + `toRB` لكل عنصر في **كل** استدعاء (حتى incremental) + `drawTimes = new long[bars.length]` → تخصيصات O(N) كل tick.
- تحسين اختياري: إعادة استخدام المصفوفة المحفوظة عند **نفس مرجع** `inputs[0]` ونفس الطول (تحويل الشمعة الجديدة فقط). أثر: تقليل ضغط GC على المخططات الطويلة. **لا أثر على الصحة.**

---

## ثالثاً — ما توثّق أنه **صحيح مطابق** (لا تغيير)

1. **دورة الحياة:** `onStart` يبني الوصفية فقط (IndicatorInfo بالتوقيع التساعي الرسمي، `InputParameterInfo.Type.BAR` الرسمي، `IntegerListDescription` بقيمتها الافتراضية ضمن القيم ✓، `OutputParameterInfo` + `setDrawnByIndicator(true)` — الشرط الرسمي للرسم).
2. **عقد calculate:** `new IndicatorResult(startIndex, length)` = (firstValueIndex, numberOfElements) ✓؛ يتعامل مع `startIndex>0` (incremental) ✓؛ `Double.NaN` = فجوة رسم رسمية ✓.
3. **`getLookback/getLookforward = 0`** ✓ (لا مستقبل؛ Pivots مؤكدة فقط بكفّتي K شمعة مقفلة).
4. **`recalculateAll=false` (الافتراضي) صحيح:** الحساب تراكمي تسلسلي — incremental ≡ إعادة كاملة بنفس ترتيب الشموع؛ الشمعة المتكوّنة **لا تشارك أبداً** في pivot مؤكد (يتطلب i+2 مقفلة) → لا repaint.
5. **`unstablePeriod=false`** ✓ (الحتمي، لا فترة استقرار).
6. **`getYForValue` مستخدم بشكل صحيح:** الوثائق: يحوّل القيمة وفق min/max **لمخرجات المؤشر** — مخرجاتنا تحمل **أسعاراً حقيقية** (OHLC الكلاستر) → مقياس المخرجات = مقياس السعر ✓. ولا نستخدمها لغير أسعار.
7. **`getXForTime(long, boolean)`** موجود في API الرسمي ✓ (المستنسخ يطابقه).
8. **كل IO ملفات** عبر try-with-resources ✓؛ الجورنال/القرارات في `getFilesDir()` (المجلد المأذون رسمياً) ✓.
9. **Threading:** لا Threads من المؤشر؛ المؤقت = عرض حائط فقط؛ `Clip.start()` غير محجب ✓ (threading.md).
10. **الأنماط متعددة الأدوات:** الوثائق تنصح `InputParameterInfo.setInstrument` — لكنه **غير قابل للتعبير** في تصميمنا (الوصفية تثبت في onStart قبل خيارات المخططات، والأقران ديناميكيون) → `IHistory.getBars` هو البديل الموثق ✓ (ثقله معالجة في البند 1).
11. **تصميم 4×MAX_CANDLES مخرجات:** كل استدعاء `drawOutput` يرسم **شمعة كلاستر واحدة بالضبط** (بلا overdraw)؛ الألواح كلها في `outputIdx==0` فقط → كفاءة ✓.
12. **`updateJournalDecisionsFromFile` في مسار الرسم:** مخزّن بـ `lastModified` → إحصاء واحد لكل repaint وليس تحليلاً؛ وهو **ميزة حية** (تحدّث وسم القرار فوراً عند تعديل الـ CSV خارجياً) → يبقى ✓.
13. **SMT Essence:** `if (showSMT) updateSmt();` → OFF = صفر IO/حالة/رسم ✓ (مصمم صراحةً zero-residue).
14. **حارس الشمعة المتكوّنة** (`detectionIndex` يُرجع شمعة إن كان الآن < نهاية الشمعة) في الملفين ✓.
15. **حدود الجلسات** 18/1/8/17 NY، حصر الحلقات (rings)، حواجز التكرار ✓.

---

## رابعاً — خطة التنفيذ المقترحة (بانتظار اعتمادك بنداً بنداً)

1. **Core-SMT:** كاش `fetchRB` بمفتاح `inst|period|آخر إقفال` + `Instrument.fromString` + تحذير واحد عند الفشل. (بنود 1، 2)
2. **Essence-Sound:** `Clip` واحد معاد استخدامه (أو close بعد STOP) + بحث `getFilesDir` ثم `user.dir` + تحذير واحد. (بنود 3، 4)
3. **كلاهما:** رفع `displayList()` خارج حلقة ملء المخرجات + كاش الرسم. (بند 5)
4. **Core:** حذف الحقول الميتة والحسابات الميتة (4 حقول + دعوّتا `reversalStages`). (بند 6)
5. **تنظيف Essence:** import + `Signal/MAX_SIGNALS` + `c2WickMid` + `lastAlert*` + `cisdStoredDeactivationTime`. (بند 10)
6. **تجميلي (اختياري):** مسار الجورنال `new File(dir,name)` (8) + قصّ خطوط CISD (9) + عناوين DST (7).
7. كل بند: تعديل ← إعادة تجميع ← اختبارا الوحدات أخضران ← commit مستقل.

**قاعدة العمل سارية:** لا تعديل قبل "اعتمد/نفّذ" صريح منك على كل بند.
