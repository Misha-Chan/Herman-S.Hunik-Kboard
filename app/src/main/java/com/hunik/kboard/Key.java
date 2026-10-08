package com.hunik.kboard;

/** One key (or spacer) of the keyboard. Geometry is filled in by KbView. */
final class Key {
    static final int CHAR = 0, SHIFT = -1, DEL = -2, SYM = -3, SYM2 = -4, LANG = -5,
            ENTER = -6, SPACE = -7, HIDE = -8, SPACER = -9;

    final int code;
    final String text;     // what gets typed
    final String label;    // what is drawn
    final float w;         // layout weight inside its row
    final String[] alts;   // long-press alternatives (may be null)

    float x, y, wd, ht;    // computed geometry in pixels
    boolean pressed;

    Key(int code, String text, String label, float w, String[] alts) {
        this.code = code;
        this.text = text;
        this.label = label;
        this.w = w;
        this.alts = alts;
    }

    boolean isSpacer() {
        return code == SPACER;
    }
}
