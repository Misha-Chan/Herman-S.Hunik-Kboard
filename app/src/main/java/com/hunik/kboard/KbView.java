package com.hunik.kboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Handler;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import java.util.Locale;

/** The whole keyboard is drawn here (no XML, no libraries). */
final class KbView extends View {

    interface Listener {
        void onKey(Key k);
        void onText(String t);
        void onPicker();
    }

    private static final int MAXP = 10;
    private static final long LONG_MS = 380, REPEAT_FIRST = 400, REPEAT_MS = 55;

    // ---- design tokens (light theme, maroon accent like the launcher) ----
    private static final int BG = 0xFFE9E7E4;
    private static final int KEY = 0xFFFFFFFF;
    private static final int KEY_SPECIAL = 0xFFD3D0CC;
    private static final int KEY_PRESSED = 0xFFC4C0BB;
    private static final int SHADOW = 0x26000000;
    private static final int TEXT = 0xFF1E1E1E;
    private static final int TEXT_DIM = 0xFF8A8A8A;
    private static final int ACCENT = 0xFF7A1F2B;
    private static final int ACCENT_DARK = 0xFF5A141E;
    private static final int ON_ACCENT = 0xFFFFFFFF;

    private final Listener listener;
    private final Handler h = new Handler();
    private final float d;
    private final float stripH, rowH, vgap, padX, gap, radius;

    private Key[][] rows = new Key[0][];
    private final Key hideKey = new Key(Key.HIDE, "", "", 1f, null);

    // state pushed by the service
    private int layer, lang, shift, enterAction;
    private boolean arAlt;
    private boolean haptic = true, sound, preview = true;

    // touch state
    private final Key[] down = new Key[MAXP];
    private final boolean[] consumed = new boolean[MAXP];
    private final Runnable[] longPress = new Runnable[MAXP];
    private Key repeatKey;
    private final Runnable repeat = new Runnable() {
        @Override
        public void run() {
            if (repeatKey != null && repeatKey.pressed) {
                listener.onKey(repeatKey);
                h.postDelayed(this, REPEAT_MS);
            }
        }
    };

    // alternates popup
    private int popupPtr = -1, popupSel;
    private String[] popupCells;
    private float popupX, popupY, popupCellW, popupH;

    // paints
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    KbView(Context c, Listener l) {
        super(c);
        listener = l;
        d = c.getResources().getDisplayMetrics().density;
        stripH = 30 * d;
        rowH = 46 * d;
        vgap = 6 * d;
        padX = 4 * d;
        gap = 5 * d;
        radius = 6 * d;
        textPaint.setTextAlign(Paint.Align.CENTER);
        icon.setStrokeWidth(2f * d);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < MAXP; i++) {
            final int id = i;
            longPress[i] = new Runnable() {
                @Override
                public void run() {
                    onLongPress(id);
                }
            };
        }
    }

    // ------------------------------------------------------------------
    // State from the service
    // ------------------------------------------------------------------
    void setPrefs(boolean haptic, boolean sound, boolean preview) {
        this.haptic = haptic;
        this.sound = sound;
        this.preview = preview;
    }

    void setState(int layer, int lang, int shift, boolean arAlt, int enterAction) {
        this.layer = layer;
        this.lang = lang;
        this.shift = shift;
        this.arAlt = arAlt;
        this.enterAction = enterAction;
        invalidate();
    }

    void setLayout(Key[][] r) {
        cancelAll();
        rows = r;
        layoutKeys(getWidth());
        invalidate();
    }

    void cancelAll() {
        h.removeCallbacks(repeat);
        for (int i = 0; i < MAXP; i++) {
            h.removeCallbacks(longPress[i]);
            down[i] = null;
            consumed[i] = false;
        }
        for (Key[] row : rows) for (Key k : row) k.pressed = false;
        hideKey.pressed = false;
        repeatKey = null;
        popupPtr = -1;
        popupCells = null;
        invalidate();
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------
    @Override
    protected void onMeasure(int wms, int hms) {
        int w = MeasureSpec.getSize(wms);
        setMeasuredDimension(w, (int) (stripH + 5 * rowH + 4 * vgap + 10 * d));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys(w);
    }

    private void layoutKeys(int w) {
        if (w <= 0) return;
        float avail = w - 2 * padX;
        for (int r = 0; r < rows.length; r++) {
            float total = 0;
            for (Key k : rows[r]) total += k.w;
            float x = padX;
            float y = stripH + 4 * d + r * (rowH + vgap);
            for (Key k : rows[r]) {
                k.x = x;
                k.y = y;
                k.wd = k.w / total * avail;
                k.ht = rowH;
                x += k.wd;
            }
        }
        hideKey.x = w - 64 * d;
        hideKey.y = 0;
        hideKey.wd = 64 * d;
        hideKey.ht = stripH;
    }

    private Key keyAt(float x, float y) {
        if (y < stripH) return x >= hideKey.x ? hideKey : null;
        if (rows.length == 0) return null;
        int best = -1;
        for (int r = 0; r < rows.length; r++) {
            Key[] row = rows[r];
            if (row.length == 0) continue;
            float top = row[0].y - vgap / 2f, bottom = row[0].y + rowH + vgap / 2f;
            if (y >= top && y < bottom) {
                best = r;
                break;
            }
        }
        if (best < 0) best = y < rows[0][0].y ? 0 : rows.length - 1;
        Key found = null;
        float bestD = Float.MAX_VALUE;
        for (Key k : rows[best]) {
            if (k.isSpacer()) continue;
            if (x >= k.x && x < k.x + k.wd) return k;
            float dist = Math.abs(x - (k.x + k.wd / 2f));
            if (dist < bestD) {
                bestD = dist;
                found = k;
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // Touch
    // ------------------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (rows.length == 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex(), id = e.getPointerId(i);
                if (id < MAXP) pointerDown(id, e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    if (id < MAXP) pointerMove(id, e.getX(i), e.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex(), id = e.getPointerId(i);
                if (id < MAXP) pointerUp(id);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelAll();
                break;
            default:
                break;
        }
        return true;
    }

    private void press(int id, Key k) {
        down[id] = k;
        consumed[id] = false;
        k.pressed = true;
        if (k.code == Key.DEL) {
            listener.onKey(k);
            consumed[id] = true;
            repeatKey = k;
            h.removeCallbacks(repeat);
            h.postDelayed(repeat, REPEAT_FIRST);
        } else if (k.code == Key.CHAR || k.code == Key.SPACE || k.code == Key.LANG) {
            h.postDelayed(longPress[id], LONG_MS);
        }
    }

    private void pointerDown(int id, float x, float y) {
        Key k = keyAt(x, y);
        if (k == null) return;
        feedback(k);
        press(id, k);
        invalidate();
    }

    private void pointerMove(int id, float x, float y) {
        if (popupPtr == id) {
            int n = popupCells.length;
            int s = (int) ((x - popupX) / popupCellW);
            s = Math.max(0, Math.min(n - 1, s));
            if (s != popupSel) {
                popupSel = s;
                invalidate();
            }
            return;
        }
        Key cur = down[id];
        if (cur == null || consumed[id]) return;
        Key nk = keyAt(x, y);
        if (nk != null && nk != cur) {
            cur.pressed = false;
            h.removeCallbacks(longPress[id]);
            press(id, nk);
            invalidate();
        }
    }

    private void pointerUp(int id) {
        h.removeCallbacks(longPress[id]);
        Key k = down[id];
        down[id] = null;
        if (k == null) return;
        k.pressed = false;
        if (popupPtr == id) {
            String t = popupCells[popupSel];
            popupPtr = -1;
            popupCells = null;
            listener.onText(t);
        } else if (k.code == Key.DEL) {
            repeatKey = null;
            h.removeCallbacks(repeat);
        } else if (!consumed[id]) {
            listener.onKey(k);
        }
        consumed[id] = false;
        invalidate();
    }

    private void onLongPress(int id) {
        Key k = down[id];
        if (k == null || consumed[id]) return;
        if (k.code == Key.SPACE || k.code == Key.LANG) {
            consumed[id] = true;
            k.pressed = false;
            listener.onPicker();
            invalidate();
        } else if (k.code == Key.CHAR && k.alts != null && popupPtr < 0) {
            consumed[id] = true;
            openPopup(id, k);
        }
    }

    private void openPopup(int id, Key k) {
        popupCells = new String[k.alts.length + 1];
        popupCells[0] = caseFix(k.text);
        for (int i = 0; i < k.alts.length; i++) popupCells[i + 1] = caseFix(k.alts[i]);
        int n = popupCells.length;
        float cw = Math.max(40 * d, k.wd * 0.9f);
        if (n * cw > getWidth() - 8 * d) cw = (getWidth() - 8 * d) / n;
        popupCellW = cw;
        popupH = 52 * d;
        float total = n * cw;
        popupX = Math.max(4 * d, Math.min(getWidth() - total - 4 * d, k.x + k.wd / 2f - total / 2f));
        popupY = k.y - popupH - 6 * d;
        if (popupY < 0) popupY = k.y + k.ht + 4 * d;
        popupSel = 0;
        popupPtr = id;
        feedback(k);
        invalidate();
    }

    private void feedback(Key k) {
        if (haptic) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
        if (sound) {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int fx = k.code == Key.SPACE ? AudioManager.FX_KEYPRESS_SPACEBAR
                        : k.code == Key.DEL ? AudioManager.FX_KEYPRESS_DELETE
                        : k.code == Key.ENTER ? AudioManager.FX_KEYPRESS_RETURN
                        : AudioManager.FX_KEYPRESS_STANDARD;
                am.playSoundEffect(fx);
            }
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------
    private boolean upper() {
        return lang == Layouts.EN && layer == Layouts.LETTERS && shift > 0;
    }

    private String caseFix(String s) {
        return upper() ? s.toUpperCase(Locale.ENGLISH) : s;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(BG);
        drawChevron(c, hideKey.x + hideKey.wd / 2f, stripH / 2f, 7 * d,
                hideKey.pressed ? ACCENT : TEXT_DIM);
        for (Key[] row : rows) for (Key k : row) if (!k.isSpacer()) drawKey(c, k);
        if (preview) {
            for (int i = 0; i < MAXP; i++) {
                Key k = down[i];
                if (k != null && k.code == Key.CHAR && popupPtr != i) drawPreview(c, k);
            }
        }
        if (popupCells != null) drawPopup(c);
    }

    private void drawKey(Canvas c, Key k) {
        float l = k.x + gap / 2f, r = k.x + k.wd - gap / 2f, t = k.y, b = k.y + k.ht;
        rect.set(l, t + 1.2f * d, r, b + 1.2f * d);
        fill.setColor(SHADOW);
        c.drawRoundRect(rect, radius, radius, fill);

        boolean capsOn = layer == Layouts.LETTERS && ((lang == Layouts.EN && shift == 2) || (lang == Layouts.AR && arAlt));
        boolean onceOn = layer == Layouts.LETTERS && lang == Layouts.EN && shift == 1;
        int bg;
        switch (k.code) {
            case Key.CHAR:
            case Key.SPACE:
                bg = k.pressed ? KEY_PRESSED : KEY;
                break;
            case Key.ENTER:
                bg = k.pressed ? ACCENT_DARK : ACCENT;
                break;
            case Key.SHIFT:
                bg = capsOn ? ACCENT : (k.pressed ? KEY_PRESSED : (onceOn ? KEY : KEY_SPECIAL));
                break;
            default:
                bg = k.pressed ? KEY_PRESSED : KEY_SPECIAL;
                break;
        }
        rect.set(l, t, r, b);
        fill.setColor(bg);
        c.drawRoundRect(rect, radius, radius, fill);

        float cx = (l + r) / 2f, cy = (t + b) / 2f, kw = r - l;
        switch (k.code) {
            case Key.CHAR:
                drawLabel(c, caseFix(k.label), cx, cy, rowH * 0.46f, TEXT, kw);
                break;
            case Key.SPACE:
                drawLabel(c, lang == Layouts.EN ? "English" : "العربية", cx, cy, rowH * 0.30f, TEXT_DIM, kw);
                break;
            case Key.SHIFT:
                drawShift(c, cx, cy, rowH * 0.2f, capsOn || onceOn, capsOn ? ON_ACCENT : (onceOn ? ACCENT : TEXT));
                break;
            case Key.DEL:
                drawBackspace(c, cx, cy, rowH * 0.2f, k.pressed ? ACCENT : TEXT, bg);
                break;
            case Key.SYM:
                drawLabel(c, layer == Layouts.LETTERS ? "?123" : (lang == Layouts.AR ? "أبج" : "ABC"),
                        cx, cy, rowH * 0.32f, TEXT, kw);
                break;
            case Key.SYM2:
                drawLabel(c, layer == Layouts.SYM1 ? "=\\<" : "?123", cx, cy, rowH * 0.32f, TEXT, kw);
                break;
            case Key.LANG:
                drawLabel(c, lang == Layouts.EN ? "EN" : "ع", cx, cy, rowH * 0.34f, TEXT, kw);
                break;
            case Key.ENTER: {
                String s = enterText();
                if (s != null) drawLabel(c, s, cx, cy, rowH * 0.30f, ON_ACCENT, kw);
                else drawEnter(c, cx, cy, rowH * 0.2f, ON_ACCENT);
                break;
            }
            default:
                break;
        }
    }

    private String enterText() {
        boolean ar = lang == Layouts.AR;
        switch (enterAction) {
            case EditorInfo.IME_ACTION_GO: return ar ? "انتقال" : "Go";
            case EditorInfo.IME_ACTION_SEARCH: return ar ? "بحث" : "Search";
            case EditorInfo.IME_ACTION_SEND: return ar ? "إرسال" : "Send";
            case EditorInfo.IME_ACTION_NEXT: return ar ? "التالي" : "Next";
            case EditorInfo.IME_ACTION_DONE: return ar ? "تم" : "Done";
            default: return null;
        }
    }

    private void drawLabel(Canvas c, String s, float cx, float cy, float size, int color, float maxW) {
        textPaint.setTextSize(size);
        float w = textPaint.measureText(s);
        float avail = maxW - 6 * d;
        if (w > avail && w > 0) textPaint.setTextSize(size * avail / w);
        textPaint.setColor(color);
        c.drawText(s, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
    }

    private void drawShift(Canvas c, float cx, float cy, float s, boolean filled, int color) {
        path.reset();
        path.moveTo(cx, cy - s);
        path.lineTo(cx + s, cy);
        path.lineTo(cx + s * 0.45f, cy);
        path.lineTo(cx + s * 0.45f, cy + s * 0.8f);
        path.lineTo(cx - s * 0.45f, cy + s * 0.8f);
        path.lineTo(cx - s * 0.45f, cy);
        path.lineTo(cx - s, cy);
        path.close();
        icon.setColor(color);
        icon.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        c.drawPath(path, icon);
    }

    private void drawBackspace(Canvas c, float cx, float cy, float s, int color, int keyBg) {
        path.reset();
        path.moveTo(cx - s, cy);
        path.lineTo(cx - s * 0.4f, cy - s * 0.7f);
        path.lineTo(cx + s, cy - s * 0.7f);
        path.lineTo(cx + s, cy + s * 0.7f);
        path.lineTo(cx - s * 0.4f, cy + s * 0.7f);
        path.close();
        icon.setColor(color);
        icon.setStyle(Paint.Style.STROKE);
        c.drawPath(path, icon);
        c.drawLine(cx + s * 0.05f, cy - s * 0.3f, cx + s * 0.6f, cy + s * 0.3f, icon);
        c.drawLine(cx + s * 0.6f, cy - s * 0.3f, cx + s * 0.05f, cy + s * 0.3f, icon);
    }

    private void drawEnter(Canvas c, float cx, float cy, float s, int color) {
        float m = lang == Layouts.AR ? -1f : 1f;      // arrow points the reading direction
        icon.setColor(color);
        icon.setStyle(Paint.Style.STROKE);
        path.reset();
        path.moveTo(cx + m * s * 0.9f, cy - s * 0.7f);
        path.lineTo(cx + m * s * 0.9f, cy + s * 0.2f);
        path.lineTo(cx - m * s * 0.4f, cy + s * 0.2f);
        c.drawPath(path, icon);
        path.reset();
        path.moveTo(cx - m * s * 0.95f, cy + s * 0.2f);
        path.lineTo(cx - m * s * 0.35f, cy - s * 0.3f);
        path.lineTo(cx - m * s * 0.35f, cy + s * 0.7f);
        path.close();
        icon.setStyle(Paint.Style.FILL);
        c.drawPath(path, icon);
    }

    private void drawChevron(Canvas c, float cx, float cy, float s, int color) {
        icon.setColor(color);
        icon.setStyle(Paint.Style.STROKE);
        path.reset();
        path.moveTo(cx - s, cy - s * 0.4f);
        path.lineTo(cx, cy + s * 0.4f);
        path.lineTo(cx + s, cy - s * 0.4f);
        c.drawPath(path, icon);
    }

    private void drawPreview(Canvas c, Key k) {
        float w = Math.max(k.wd * 1.3f, 52 * d);
        float hgt = Math.min(52 * d, k.y - 2 * d);
        if (hgt < 20 * d) return;
        float cx = k.x + k.wd / 2f;
        float l = Math.max(2 * d, Math.min(getWidth() - w - 2 * d, cx - w / 2f));
        float t = k.y - hgt - 2 * d;
        rect.set(l, t + 1.5f * d, l + w, t + hgt + 1.5f * d);
        fill.setColor(SHADOW);
        c.drawRoundRect(rect, radius * 1.5f, radius * 1.5f, fill);
        rect.set(l, t, l + w, t + hgt);
        fill.setColor(KEY);
        c.drawRoundRect(rect, radius * 1.5f, radius * 1.5f, fill);
        drawLabel(c, caseFix(k.label), l + w / 2f, t + hgt / 2f, Math.min(hgt * 0.6f, 30 * d), TEXT, w);
    }

    private void drawPopup(Canvas c) {
        int n = popupCells.length;
        float total = n * popupCellW;
        rect.set(popupX, popupY + 1.5f * d, popupX + total, popupY + popupH + 1.5f * d);
        fill.setColor(SHADOW);
        c.drawRoundRect(rect, radius * 2, radius * 2, fill);
        rect.set(popupX, popupY, popupX + total, popupY + popupH);
        fill.setColor(KEY);
        c.drawRoundRect(rect, radius * 2, radius * 2, fill);
        for (int i = 0; i < n; i++) {
            float l = popupX + i * popupCellW;
            if (i == popupSel) {
                rect.set(l + 3 * d, popupY + 3 * d, l + popupCellW - 3 * d, popupY + popupH - 3 * d);
                fill.setColor(ACCENT);
                c.drawRoundRect(rect, radius * 1.5f, radius * 1.5f, fill);
            }
            drawLabel(c, popupCells[i], l + popupCellW / 2f, popupY + popupH / 2f, 24 * d,
                    i == popupSel ? ON_ACCENT : TEXT, popupCellW);
        }
    }
}
