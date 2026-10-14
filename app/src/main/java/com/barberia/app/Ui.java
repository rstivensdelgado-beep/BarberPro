package com.barberia.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Paleta neutra y constructores de vistas. Los colores cambian con el tema
 * (claro u oscuro) desde Ajustes; llama a applyTheme() antes de construir pantallas.
 */
public final class Ui {

    // Paleta: fondos cálidos neutros, tinta casi negra y un único acento dorado para cifras clave.
    // Por defecto: paleta A (oscura con acento ámbar), la elegida en los croquis.
    public static int BG = 0xFF141412;
    public static int SURFACE = 0xFF1F1E1B;
    public static int INK = 0xFFEDEBE5;
    public static int MUTED = 0xFF8C897F;
    public static int LINE = 0xFF2C2B27;
    public static int ACCENT = 0xFFD2AE6A;
    public static int DANGER = 0xFFD98B80;
    public static int ON_INK = 0xFF141412;
    // Verde azulado de las tarjetas de citas (igual en ambos temas) y su texto.
    public static final int TEAL = 0xFF1F6F6B;
    public static final int ON_TEAL = 0xFFFFFFFF;
    public static final int OK = 0xFF4CAF7A;
    /** true en modo oscuro; GLASS true cuando la paleta es Cristal. */
    public static boolean DARK = true;
    public static boolean GLASS = false;
    /** Multiplicador del tamaño de letra (Ajustes > Accesibilidad). */
    public static float FONT_SCALE = 1f;

    /** Fondo redondeado de un color sólido (tarjetas de citas, barra de fecha). */
    public static GradientDrawable solid(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    private Ui() { }

    public static void applyTheme(boolean dark) {
        DARK = dark;
        GLASS = false;
        if (dark) {
            BG = 0xFF141412;
            SURFACE = 0xFF1F1E1B;
            INK = 0xFFEDEBE5;
            MUTED = 0xFF8C897F;
            LINE = 0xFF2C2B27;
            ACCENT = 0xFFD2AE6A;
            DANGER = 0xFFD98B80;
            ON_INK = 0xFF141412;
        } else {
            BG = 0xFFF6F5F1;
            SURFACE = 0xFFFFFFFF;
            INK = 0xFF1B1A17;
            MUTED = 0xFF8A877F;
            LINE = 0xFFE6E4DD;
            ACCENT = 0xFFA8834A;
            DANGER = 0xFF9A4A42;
            ON_INK = 0xFFFFFFFF;
        }
    }

    /**
     * Material You: toma los colores del fondo de pantalla (Android 12 o superior).
     * Devuelve false si el sistema no los ofrece; en ese caso se queda la paleta dorada.
     * Los colores se leen por nombre de recurso para que compile con cualquier android.jar.
     */
    public static boolean applyWallpaper(Context c, boolean dark) {
        if (android.os.Build.VERSION.SDK_INT < 31) return false;
        android.content.res.Resources r = c.getResources();
        int accent = systemColor(r, dark ? "system_accent1_200" : "system_accent1_600");
        int bg = systemColor(r, dark ? "system_neutral1_900" : "system_neutral1_10");
        int surface = systemColor(r, dark ? "system_neutral1_800" : "system_neutral1_0");
        int ink = systemColor(r, dark ? "system_neutral1_50" : "system_neutral1_900");
        int muted = systemColor(r, dark ? "system_neutral2_300" : "system_neutral2_500");
        int line = systemColor(r, dark ? "system_neutral2_700" : "system_neutral2_100");
        int onInk = systemColor(r, dark ? "system_neutral1_900" : "system_neutral1_0");
        if (accent == 0 || bg == 0 || surface == 0 || ink == 0 || muted == 0 || line == 0 || onInk == 0) return false;
        BG = bg;
        SURFACE = surface;
        INK = ink;
        MUTED = muted;
        LINE = line;
        ACCENT = accent;
        ON_INK = onInk;
        return true;
    }

    /** Nombre visible de cada paleta. */
    public static String paletteName(String p) {
        if ("material".equals(p)) return "Material You";
        if ("medianoche".equals(p)) return "Medianoche";
        if ("cristal".equals(p)) return "Cristal";
        return "dorado";
    }

    /** Siguiente paleta del ciclo; Material You solo se ofrece en Android 12 o superior. */
    public static String nextPalette(String p) {
        boolean wall = android.os.Build.VERSION.SDK_INT >= 31;
        if ("dorado".equals(p)) return "medianoche";
        if ("medianoche".equals(p)) return "cristal";
        if ("cristal".equals(p)) return wall ? "material" : "dorado";
        return "dorado";
    }

    /** Medianoche y plata, con fondo sólido. */
    public static void applyMedianoche(boolean dark) {
        applyGlass(dark);
        GLASS = false;
    }

    /** Estilo cristal: colores de base y tarjetas translúcidas (sin desenfoque real). */
    public static void applyGlass(boolean dark) {
        DARK = dark;
        GLASS = true;
        // Medianoche y plata
        BG = dark ? 0xFF0A1020 : 0xFFEEF2F8;
        SURFACE = dark ? 0xFF111A2E : 0xFFFFFFFF;
        INK = dark ? 0xFFEEF2F8 : 0xFF141A2A;
        MUTED = dark ? 0xFF8E9AB2 : 0xFF6B7385;
        LINE = dark ? 0x33FFFFFF : 0x33000000;
        ACCENT = dark ? 0xFFC9D3E3 : 0xFF5E7396;
        ON_INK = dark ? 0xFF0A1020 : 0xFFFFFFFF;
    }

    /** Fondo degradado con dos brillos de color detrás de las tarjetas translúcidas. */
    public static android.graphics.drawable.Drawable glassBackground(Context c) {
        int top = DARK ? 0xFF0A1020 : 0xFFEEF2F8;
        int bottom = DARK ? 0xFF111A2E : 0xFFE2E8F2;
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        GradientDrawable glow = radialGlow(DARK ? 0x597896C8 : 0x667896C8, 0x007896C8, dp(c, 300), 0.85f, 0.10f);
        GradientDrawable silver = radialGlow(DARK ? 0x4DC9D3E3 : 0x44C9D3E3, 0x00C9D3E3, dp(c, 280), 0.12f, 0.90f);
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base, glow, silver});
    }

    private static GradientDrawable radialGlow(int center, int edge, int radiusPx, float cx, float cy) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{center, edge});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(radiusPx);
        return g;
    }

    /** Cristal: el fondo es sólido; solo la barra inferior se ve como vidrio. */
    public static boolean CRYSTAL = false;

    /**
     * Fondo de la barra inferior con aspecto de vidrio: relleno translúcido,
     * borde de luz y un brillo en la mitad superior.
     */
    public static android.graphics.drawable.Drawable crystalPill(Context c) {
        float r = dp(c, 32);
        android.graphics.drawable.GradientDrawable body = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                DARK ? new int[]{0x662A3650, 0x8C0E1628} : new int[]{0xD9FFFFFF, 0xA6FFFFFF});
        body.setCornerRadius(r);
        body.setStroke(dp(c, 1), DARK ? 0x59FFFFFF : 0xCCFFFFFF);

        android.graphics.drawable.GradientDrawable shine = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DARK ? 0x40FFFFFF : 0x99FFFFFF, 0x00FFFFFF});
        shine.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});

        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{body, shine});
        ld.setLayerInset(1, dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 30));
        return ld;
    }

    /** Color de la barra inferior: translúcida en cristal, sólida en los demás estilos. */
    public static int navColor() {
        if (!GLASS) return SURFACE;
        return DARK ? 0xD1111A2E : 0xCCFFFFFF;
    }

    /** Color del sistema por nombre; 0 si el dispositivo no lo tiene. */
    private static int systemColor(android.content.res.Resources r, String name) {
        int id = r.getIdentifier(name, "color", "android");
        return id == 0 ? 0 : r.getColor(id);
    }

    /** Color del punto de estado de una cita. */
    public static int statusColor(String status) {
        if ("confirmada".equals(status)) return OK;
        if ("completada".equals(status)) return MUTED;
        if ("cancelada".equals(status)) return DANGER;
        if ("bloqueado".equals(status)) return MUTED;
        return 0xFFE0B354; // pendiente: ámbar
    }

    /** Punto redondo de color, para indicar estado. */
    public static View dot(Context c, int color, float sizeDp) {
        View v = new View(c);
        v.setBackground(solid(c, color, sizeDp / 2));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** Bloque de estado vacío: título y explicación centrados. El botón lo añade cada pantalla. */
    public static LinearLayout emptyState(Context c, String title, String sub) {
        LinearLayout l = column(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(c, 8), dp(c, 18), dp(c, 8), dp(c, 14));
        TextView t = text(c, title, 16, INK, true);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        TextView s = text(c, sub, 13, MUTED, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(c, 4), 0, 0);
        l.addView(s);
        return l;
    }

    /** Animación de entrada suave para diálogos: crece desde un poco más pequeño y aparece. */
    public static void popIn(View v) {
        if (v == null) return;
        v.setScaleX(0.92f);
        v.setScaleY(0.92f);
        v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /**
     * Quita una vista de su padre actual. Hace falta para los campos que una pantalla
     * conserva entre refrescos: Android no permite añadir una vista que ya tiene padre.
     */
    public static void detach(View v) {
        if (v == null) return;
        android.view.ViewParent p = v.getParent();
        if (p instanceof ViewGroup) ((ViewGroup) p).removeView(v);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * FONT_SCALE);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                : Typeface.create("sans-serif", Typeface.NORMAL));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 26, INK, true);
        t.setPadding(0, 0, 0, dp(c, 4));
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 11, MUTED, false);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(c, 22), 0, dp(c, 8));
        return t;
    }

    public static TextView muted(Context c, String s) {
        return text(c, s, 13, MUTED, false);
    }

    /** Tarjeta blanca con esquinas suaves y padding estándar. */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        GradientDrawable g = new GradientDrawable();
        if (GLASS) {
            g.setColor(DARK ? 0x1FFFFFFF : 0x99FFFFFF);
            g.setCornerRadius(dp(c, 18));
            g.setStroke(dp(c, 1), DARK ? 0x40FFFFFF : 0x66FFFFFF);
        } else {
            g.setColor(SURFACE);
            g.setCornerRadius(dp(c, 14));
            g.setStroke(dp(c, 1), LINE);
        }
        l.setBackground(g);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    public static View spacer(Context c, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    /** Botón: relleno oscuro (principal) o solo texto (secundario). */
    public static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c, null, android.R.attr.borderlessButtonStyle);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        if (primary) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(INK);
            g.setCornerRadius(dp(c, 22));
            b.setBackground(g);
            b.setTextColor(ON_INK);
        } else {
            b.setTextColor(INK);
        }
        return b;
    }

    /** Botón de texto pequeño (acciones dentro de una fila). */
    public static TextView link(Context c, String label, int color) {
        TextView t = text(c, label, 14, color, true);
        t.setPadding(dp(c, 6), dp(c, 8), dp(c, 6), dp(c, 8));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static EditText input(Context c, String hint, boolean numeric) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(INK);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setSingleLine(true);
        e.setPadding(0, dp(c, 10), 0, dp(c, 10));
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BG);
        g.setCornerRadius(dp(c, 10));
        g.setStroke(dp(c, 1), LINE);
        e.setBackground(g);
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        return e;
    }

    /** Contenedor desplazable con márgenes laterales de 16 dp. */
    public static ScrollView page(Context c, LinearLayout content) {
        ScrollView sv = new ScrollView(c);
        if (GLASS) sv.setBackground(glassBackground(c));
        else sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        content.setPadding(dp(c, 16), dp(c, 20), dp(c, 16), dp(c, 112));
        sv.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    /** Cifra grande y fina con etiqueta debajo, para tarjetas de resumen. */
    public static LinearLayout stat(Context c, String label, String value, boolean warn) {
        LinearLayout l = column(c);
        l.addView(text(c, label, 12, MUTED, false));
        TextView v = text(c, value, 20, warn ? DANGER : INK, false);
        v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        l.addView(v);
        return l;
    }
}
