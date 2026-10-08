package com.hunik.kboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All layouts are 5 rows: numbers (Western digits 0-9), three letter/symbol rows, bottom row.
 * Row weights are chosen so the rows line up (10 units for English/symbols, 13 for Arabic).
 */
final class Layouts {
    static final int LETTERS = 0, SYM1 = 1, SYM2 = 2;
    static final int EN = 0, AR = 1;
    static final int NORMAL = 0, EMAIL = 1, URI = 2;   // field kind (changes the comma key)

    private static final Map<String, String[]> ALTS = new HashMap<String, String[]>();

    private static void alt(String key, String... a) {
        ALTS.put(key, a);
    }

    static {
        // English accents
        alt("a", "à", "á", "â", "ä", "ã", "å", "æ");
        alt("e", "è", "é", "ê", "ë", "ē");
        alt("i", "ì", "í", "î", "ï");
        alt("o", "ò", "ó", "ô", "ö", "õ", "œ");
        alt("u", "ù", "ú", "û", "ü");
        alt("c", "ç");
        alt("n", "ñ");
        alt("s", "ß");
        alt("y", "ÿ");
        alt(".", ",", "?", "!", "'", "\"", ":", ";");
        alt(",", ";", ":", "'", "\"");
        // Arabic
        alt("ا", "أ", "إ", "آ", "ٱ");
        alt("و", "ؤ");
        alt("ي", "ى", "ئ");
        alt("ه", "ة");
        alt("ء", "ئ", "ؤ");
        alt("ب", "پ");
        alt("ف", "ڤ");
        alt("ك", "گ");
        alt("ج", "چ");
        alt("ز", "ژ");
        alt("ل", "لا", "لأ", "لإ", "لآ");
        alt("،", "؛", "؟", "!", ":");
        // Western digits -> Arabic-Indic digits on long press
        String[] indic = {"٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"};
        for (int i = 0; i < 10; i++) alt(String.valueOf(i), indic[i]);
    }

    private static Key c(String t, float w) {
        return new Key(Key.CHAR, t, t, w, ALTS.get(t));
    }

    private static Key s(int code, float w) {
        return new Key(code, "", "", w, null);
    }

    private static Key sp(float w) {
        return new Key(Key.SPACER, "", "", w, null);
    }

    private static Key[] chars(String spaced, float w) {
        String[] t = spaced.split(" ");
        Key[] k = new Key[t.length];
        for (int i = 0; i < t.length; i++) k[i] = c(t[i], w);
        return k;
    }

    private static Key[] cat(Object... parts) {
        List<Key> out = new ArrayList<Key>();
        for (Object p : parts) {
            if (p instanceof Key) out.add((Key) p);
            else for (Key k : (Key[]) p) out.add(k);
        }
        return out.toArray(new Key[out.size()]);
    }

    /** Arabic diacritics row (shown when the Arabic shift key is on). */
    private static Key[] tashkeel(float w) {
        String tat = "\u0640";
        String[] marks = {"\u064B", "\u064C", "\u064D", "\u064E", "\u064F", "\u0650",
                "\u0651", "\u0652", "\u0640", "\u0670", "\u0653", "\u0654"};
        Key[] k = new Key[marks.length];
        for (int i = 0; i < marks.length; i++) {
            String label = marks[i].equals(tat) ? tat : tat + marks[i];
            k[i] = new Key(Key.CHAR, marks[i], label, w, null);
        }
        return k;
    }

    private static Key[] bottom(int layer, int lang, int kind) {
        boolean wide = layer == LETTERS && lang == AR;     // 13-unit grid
        String comma = lang == AR ? "،" : ",";
        Key commaKey;
        if (kind == EMAIL) commaKey = c("@", wide ? 1.2f : 1f);
        else if (kind == URI) commaKey = c("/", wide ? 1.2f : 1f);
        else commaKey = c(comma, wide ? 1.2f : 1f);
        return cat(
                s(Key.SYM, wide ? 1.8f : 1.3f),
                s(Key.LANG, wide ? 1.4f : 1f),
                commaKey,
                new Key(Key.SPACE, " ", " ", wide ? 5.4f : 4.2f, null),
                c(".", wide ? 1.2f : 1f),
                s(Key.ENTER, wide ? 2f : 1.5f));
    }

    static Key[][] build(int layer, int lang, boolean arAlt, int kind) {
        Key[][] r = new Key[5][];
        boolean arLetters = layer == LETTERS && lang == AR;
        r[0] = chars("1 2 3 4 5 6 7 8 9 0", arLetters ? 1.3f : 1f);

        if (layer == LETTERS && lang == EN) {
            r[1] = chars("q w e r t y u i o p", 1f);
            r[2] = cat(sp(.5f), chars("a s d f g h j k l", 1f), sp(.5f));
            r[3] = cat(s(Key.SHIFT, 1.5f), chars("z x c v b n m", 1f), s(Key.DEL, 1.5f));
        } else if (arLetters) {
            r[1] = cat(sp(.5f), arAlt ? tashkeel(1f) : chars("ض ص ث ق ف غ ع ه خ ح ج د", 1f), sp(.5f));
            r[2] = cat(sp(.5f), chars("ش س ي ب ل ا ت ن م ك ط ذ", 1f), sp(.5f));
            r[3] = cat(s(Key.SHIFT, 1.5f), chars("ئ ء ؤ ر لا ى ة و ز ظ", 1f), s(Key.DEL, 1.5f));
        } else if (layer == SYM1) {
            r[1] = chars("@ # $ _ & - + ( ) /", 1f);
            r[2] = chars("* \" ' : ; ! ? ~ % =", 1f);
            r[3] = cat(s(Key.SYM2, 1.5f), chars("{ } [ ] < > \\", 1f), s(Key.DEL, 1.5f));
        } else {
            r[1] = chars("، ؛ ؟ « » ٪ ـ ٫ ٬ …", 1f);
            r[2] = chars("` | ^ ° • € £ ¥ © ®", 1f);
            r[3] = cat(s(Key.SYM2, 1.5f), chars("± × ÷ ¿ ¡ § ¶", 1f), s(Key.DEL, 1.5f));
        }
        r[4] = bottom(layer, lang, kind);
        return r;
    }
}
