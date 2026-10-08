package com.hunik.kboard;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.Locale;

public class KboardService extends InputMethodService implements KbView.Listener {

    private KbView view;
    private SharedPreferences prefs;

    private int lang = Layouts.EN;
    private int layer = Layouts.LETTERS;
    private int kind = Layouts.NORMAL;
    private int shift;               // 0 off, 1 one letter, 2 caps lock
    private boolean arAlt;           // Arabic diacritics row
    private int enterAction;
    private long lastShiftTap;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("kboard", MODE_PRIVATE);
        lang = prefs.getInt("lang", Layouts.EN);
    }

    @Override
    public View onCreateInputView() {
        view = new KbView(this, this);
        rebuild();
        return view;
    }

    /** Never take over the whole screen in landscape. */
    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInputView(EditorInfo ei, boolean restarting) {
        super.onStartInputView(ei, restarting);
        lang = prefs.getInt("lang", Layouts.EN);
        layer = Layouts.LETTERS;
        kind = Layouts.NORMAL;
        arAlt = false;
        shift = 0;
        enterAction = enterActionOf(ei);

        int cls = ei.inputType & InputType.TYPE_MASK_CLASS;
        int var = ei.inputType & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME) {
            layer = Layouts.SYM1;
        } else if (cls == InputType.TYPE_CLASS_TEXT) {
            if (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) {
                kind = Layouts.EMAIL;
                lang = Layouts.EN;
            } else if (var == InputType.TYPE_TEXT_VARIATION_URI) {
                kind = Layouts.URI;
                lang = Layouts.EN;
            } else if (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                lang = Layouts.EN;
            }
        }
        if (view != null) {
            view.setPrefs(prefs.getBoolean("haptic", true), prefs.getBoolean("sound", false),
                    prefs.getBoolean("preview", true));
            rebuild();
            updateAutoCaps();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        if (view != null) view.cancelAll();
    }

    // ------------------------------------------------------------------
    // State -> view
    // ------------------------------------------------------------------
    private void rebuild() {
        if (view == null) return;
        view.setLayout(Layouts.build(layer, lang, arAlt, kind));
        pushState();
    }

    private void pushState() {
        if (view != null) view.setState(layer, lang, shift, arAlt, enterAction);
    }

    private int enterActionOf(EditorInfo ei) {
        if (ei == null) return 0;
        if ((ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return 0;
        int a = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (a == EditorInfo.IME_ACTION_NONE || a == EditorInfo.IME_ACTION_UNSPECIFIED) return 0;
        return a;
    }

    // ------------------------------------------------------------------
    // Key events from the view
    // ------------------------------------------------------------------
    @Override
    public void onKey(Key k) {
        switch (k.code) {
            case Key.SHIFT:
                onShift();
                return;
            case Key.SYM:
                layer = layer == Layouts.LETTERS ? Layouts.SYM1 : Layouts.LETTERS;
                arAlt = false;
                rebuild();
                return;
            case Key.SYM2:
                layer = layer == Layouts.SYM1 ? Layouts.SYM2 : Layouts.SYM1;
                rebuild();
                return;
            case Key.LANG:
                lang = lang == Layouts.EN ? Layouts.AR : Layouts.EN;
                prefs.edit().putInt("lang", lang).apply();
                layer = Layouts.LETTERS;
                shift = 0;
                arAlt = false;
                rebuild();
                updateAutoCaps();
                return;
            case Key.HIDE:
                requestHideSelf(0);
                return;
            default:
                break;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (k.code == Key.DEL) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            updateAutoCaps();
        } else if (k.code == Key.ENTER) {
            if (enterAction != 0) ic.performEditorAction(enterAction);
            else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        } else {
            String t = k.text;
            if (lang == Layouts.EN && layer == Layouts.LETTERS && shift > 0) t = t.toUpperCase(Locale.ENGLISH);
            commit(ic, t);
        }
    }

    /** Text chosen from the long-press popup (already cased by the view). */
    @Override
    public void onText(String t) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) commit(ic, t);
    }

    @Override
    public void onPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private void commit(InputConnection ic, String t) {
        ic.commitText(t, 1);
        if (shift == 1) {
            shift = 0;
            pushState();
        }
        updateAutoCaps();
    }

    private void onShift() {
        if (layer != Layouts.LETTERS) return;
        if (lang == Layouts.AR) {          // Arabic: shift toggles the diacritics row
            arAlt = !arAlt;
            rebuild();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (shift == 0) shift = 1;
        else if (shift == 1) shift = (now - lastShiftTap < 400) ? 2 : 0;   // double tap = caps lock
        else shift = 0;
        lastShiftTap = now;
        pushState();
    }

    /** Capitalise the first letter of a sentence when the field asks for it. */
    private void updateAutoCaps() {
        if (lang != Layouts.EN || layer != Layouts.LETTERS || shift == 2 || view == null) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ei == null || ic == null || ei.inputType == InputType.TYPE_NULL) return;
        int n = ic.getCursorCapsMode(ei.inputType) != 0 ? 1 : 0;
        if (n != shift) {
            shift = n;
            pushState();
        }
    }
}
