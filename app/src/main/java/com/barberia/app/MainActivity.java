package com.barberia.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.barberia.app.screens.AgendaScreen;
import com.barberia.app.screens.ClientsScreen;
import com.barberia.app.screens.GalleryScreen;
import com.barberia.app.screens.HomeScreen;
import com.barberia.app.screens.SettingsScreen;

public class MainActivity extends Activity {

    public static final int REQ_PHOTO = 401;
    public static final int REQ_GALLERY = 402;

    /** Índices de las pantallas (orden en screens[]). */
    private static final int HOME = 0;
    public static final int AGENDA = 1;
    public static final int CLIENTS = 2;
    /** Galería: no está en la barra; se abre desde Inicio. */
    public static final int GALLERY = 3;
    /** Ajustes: siempre disponible, incluso sin activar. */
    public static final int SETTINGS = 4;

    private static final long DUR_OUT = 160;
    private static final long DUR_IN = 260;

    private Screen[] screens;
    /** Pestañas de la barra por índice de pantalla; null para las que no están en la barra. */
    private LinearLayout[] tabs;
    private TextView[] labels;
    private NavIcon[] icons;
    private FrameLayout container;
    private TextView fab;
    private int current = 0;
    private Screen settingsScreen;
    private FrameLayout rootLayer;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Primer mensaje: si aparece, la app arranca y el problema está al construir la pantalla.
        TextView opening = new TextView(this);
        opening.setText("Abriendo BarberPro...");
        opening.setGravity(Gravity.CENTER);
        opening.setTextSize(18);
        opening.setTextColor(0xFFFFFFFF);
        opening.setBackgroundColor(0xFF0A1020);
        setContentView(opening);
        try {
            buildUi(state);
        } catch (Throwable t) {
            showCrash(t);
        }
    }

    /** Si la app falla al abrir, muestra el error en pantalla en vez de cerrarse. */
    private void showCrash(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        TextView tv = new TextView(this);
        tv.setText("Error al abrir BarberPro. Copia este texto y mándaselo al programador:\n\n" + sw.toString());
        tv.setTextSize(11);
        tv.setTextColor(0xFFFFFFFF);
        tv.setBackgroundColor(0xFF000000);
        tv.setPadding(24, 48, 24, 24);
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(tv);
        setContentView(sc);
    }

    private void buildUi(Bundle state) {
        Store store = Store.get(this);
        Ui.FONT_SCALE = fontScaleOf(store);
        boolean darkMode = store.settings().optBoolean("dark", true);
        Ui.applyTheme(darkMode);
        String palette = store.settings().optString("palette", "dorado");
        if ("material".equals(palette)) Ui.applyWallpaper(this, darkMode);
        if ("medianoche".equals(palette)) Ui.applyMedianoche(darkMode);
        if ("cristal".equals(palette)) {
            Ui.applyMedianoche(darkMode);
            Ui.CRYSTAL = true;
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);

        screens = new Screen[]{
                new HomeScreen(this),
                new AgendaScreen(this),
                new ClientsScreen(this),
                new GalleryScreen(this),
                new SettingsScreen(this)
        };
        settingsScreen = screens[SETTINGS];

        // Capas: columna principal (cabecera y contenido) y barra en píldora encima, con el "+" al centro.
        FrameLayout root = new FrameLayout(this);
        rootLayer = root;
        if (Ui.GLASS) root.setBackground(Ui.glassBackground(this));
        else root.setBackgroundColor(Ui.BG);

        LinearLayout main = Ui.column(this);
        main.addView(buildTopBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container = new FrameLayout(this);
        main.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(main, matchParent());

        root.addView(buildPill(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // Pestaña inicial sin animación. Sin activar, la app abre en Ajustes.
        current = License.isUnlocked(this) ? HOME : SETTINGS;
        View first = screens[current].view();
        container.addView(first, matchParent());
        // Pantalla de inicio con el nombre de la app; solo en un arranque nuevo, no al recrear por cambio de tema.
        if (state == null) addSplash(root);
        setContentView(root);
        root.setFitsSystemWindows(true);
        refreshTabs();
        first.setVisibility(View.VISIBLE);
        updateFab();
    }

    /** Escala de letra elegida en Ajustes: normal, grande o muy grande. */
    private static float fontScaleOf(Store store) {
        String f = store.settings().optString("fontSize", "normal");
        if ("grande".equals(f)) return 1.15f;
        if ("muy_grande".equals(f)) return 1.3f;
        return 1f;
    }

    /** Pantalla de inicio: el nombre "BarberPro" en el color del tema, con fundido al terminar. */
    private void addSplash(final FrameLayout root) {
        final FrameLayout splash = new FrameLayout(this);
        if (Ui.GLASS) splash.setBackground(Ui.glassBackground(this));
        else splash.setBackgroundColor(Ui.BG);
        TextView name = Ui.text(this, "BarberPro", 34, Ui.ACCENT, true);
        name.setGravity(Gravity.CENTER);
        splash.addView(name, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(splash, matchParent());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                splash.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        root.removeView(splash);
                    }
                }).start();
            }
        }, 1100);
    }

    /** Aviso breve sobre la barra con botón "Deshacer" (dura unos 4 segundos). */
    public void showUndo(String msg, final Runnable undo) {
        if (rootLayer == null) return;
        final LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        bar.setBackground(Ui.solid(this, Ui.SURFACE, 16));

        TextView t = Ui.text(this, msg, 14, Ui.INK, false);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView act = Ui.link(this, "Deshacer", Ui.ACCENT);
        act.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        bar.addView(act);

        final Runnable close = new Runnable() {
            @Override
            public void run() {
                if (bar.getParent() != null) rootLayer.removeView(bar);
            }
        };
        act.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handler.removeCallbacks(close);
                close.run();
                undo.run();
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.leftMargin = Ui.dp(this, 14);
        lp.rightMargin = Ui.dp(this, 14);
        lp.bottomMargin = Ui.dp(this, 112);
        rootLayer.addView(bar, lp);
        Ui.popIn(bar);
        handler.postDelayed(close, 4000);
    }

    /** Cabecera con el nombre de la app. */
    private View buildTopBar() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 4));
        TextView brand = Ui.text(this, "BarberPro", 20, Ui.INK, true);
        bar.addView(brand);
        return bar;
    }

    /**
     * Barra en píldora: Inicio, Agenda, [hueco del botón central], Clientes, Ajustes.
     * El botón "+" va en el centro y sobresale un poco por encima de la píldora.
     */
    private View buildPill() {
        FrameLayout holder = new FrameLayout(this);

        LinearLayout pill = Ui.row(this);
        pill.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        if (Ui.CRYSTAL) {
            pill.setBackground(Ui.crystalPill(this));
            pill.setElevation(Ui.dp(this, 8));
        } else {
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(Ui.navColor());
            pillBg.setCornerRadius(Ui.dp(this, 32));
            pillBg.setStroke(Ui.dp(this, 1), Ui.LINE);
            pill.setBackground(pillBg);
        }

        tabs = new LinearLayout[5];
        labels = new TextView[5];
        icons = new NavIcon[5];
        addItem(pill, HOME, "Inicio", NavIcon.HOME);
        addItem(pill, AGENDA, "Agenda", NavIcon.CALENDAR);
        pill.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 76), 1));
        addItem(pill, CLIENTS, "Clientes", NavIcon.USERS);
        addItem(pill, SETTINGS, "Ajustes", NavIcon.GEAR);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64), Gravity.BOTTOM);
        plp.leftMargin = Ui.dp(this, 14);
        plp.rightMargin = Ui.dp(this, 14);
        plp.bottomMargin = Ui.dp(this, 14);
        holder.addView(pill, plp);

        fab = buildFab();
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56),
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        flp.bottomMargin = Ui.dp(this, 44);
        holder.addView(fab, flp);
        return holder;
    }

    private void addItem(LinearLayout pill, final int idx, String text, int iconType) {
        LinearLayout tab = Ui.column(this);
        tab.setGravity(Gravity.CENTER_HORIZONTAL);
        tab.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));

        NavIcon icon = new NavIcon(this, iconType);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        tab.addView(icon, ilp);

        TextView t = Ui.text(this, text, 11, Ui.MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 3);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        tab.addView(t, tlp);

        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(idx);
            }
        });
        pill.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        tabs[idx] = tab;
        labels[idx] = t;
        icons[idx] = icon;
    }

    /** Botón central dorado (+). Se ve solo en las pestañas que tienen una acción rápida. */
    private TextView buildFab() {
        TextView f = Ui.text(this, "+", 30, Ui.ON_INK, false);
        f.setGravity(Gravity.CENTER);
        f.setPadding(0, 0, 0, Ui.dp(this, 4));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Ui.ACCENT);
        f.setBackground(g);
        f.setElevation(Ui.dp(this, 6));
        f.setClickable(true);
        f.setFocusable(true);
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (screens[current].hasFab()) {
                    screens[current].onFab();
                } else {
                    // En Inicio y Ajustes el "+" abre una cita nueva en la agenda.
                    if (!License.isUnlocked(MainActivity.this)) {
                        show(SETTINGS);
                        return;
                    }
                    show(AGENDA);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            screens[AGENDA].onFab();
                        }
                    });
                }
            }
        });
        return f;
    }

    /** El "+" se ve en todas las pestañas; en las que no tienen acción propia abre una cita nueva. */
    private void updateFab() {
        fab.setVisibility(View.VISIBLE);
    }

    /** Cambia de pantalla con deslizamiento y fundido. */
    public void show(int index) {
        // Sin activación solo está abierta la pestaña Ajustes.
        if (index != SETTINGS && !License.isUnlocked(this)) {
            android.widget.Toast.makeText(this, "Tu prueba terminó. Activa BarberPro en Ajustes.", android.widget.Toast.LENGTH_SHORT).show();
            index = SETTINGS;
        }
        if (index == current) return;
        final int dir = index > current ? 1 : -1;
        final Screen incoming = screens[index];
        final View inView = incoming.view();
        final View outView = screens[current].view();
        final int offset = Ui.dp(this, 24);

        // Cancela animaciones pendientes para que un cambio rápido no deje una pantalla oculta.
        inView.animate().cancel();
        outView.animate().cancel();

        incoming.refresh();
        if (inView.getParent() == null) {
            container.addView(inView, matchParent());
        }

        inView.setVisibility(View.VISIBLE);
        inView.setTranslationX(dir * offset);
        inView.setAlpha(0f);
        inView.animate().translationX(0).alpha(1f).setDuration(DUR_IN)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withLayer()
                .start();

        outView.animate().translationX(-dir * offset).alpha(0f).setDuration(DUR_OUT)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .withLayer()
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        outView.setVisibility(View.GONE);
                        outView.setTranslationX(0);
                        outView.setAlpha(1f);
                    }
                })
                .start();

        current = index;
        refreshTabs();
        updateFab();
    }

    /** Abre la galería de trabajos desde Inicio. */
    public void openGallery() {
        show(GALLERY);
    }

    /** Marca la pestaña activa con una cápsula suave del color de acento. */
    private void refreshTabs() {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == null) continue;
            boolean on = i == current;
            labels[i].setTextColor(on ? Ui.ACCENT : Ui.MUTED);
            icons[i].setColor(on ? Ui.ACCENT : Ui.MUTED);
            tabs[i].setBackground(on ? activeCapsule() : null);
        }
    }

    private Drawable activeCapsule() {
        GradientDrawable g = new GradientDrawable();
        g.setColor((0x33 << 24) | (Ui.ACCENT & 0x00FFFFFF));
        g.setCornerRadius(Ui.dp(this, 22));
        return g;
    }

    /** Elige una foto para la galería de trabajos. */
    public void pickGalleryPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Elegir foto"), REQ_GALLERY);
    }

    /** Pide una foto de perfil; el resultado llega a onActivityResult. */
    public void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Elegir foto"), REQ_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) ((GalleryScreen) screens[GALLERY]).onPicked(uri);
        }
        if (requestCode == ContactsHelper.REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            ContactsHelper.Contact ct = ContactsHelper.fromPicked(this, data.getData());
            if (ct != null) ((AgendaScreen) screens[AGENDA]).onContactPicked(ct);
        }
        if (requestCode == REQ_PHOTO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) ((SettingsScreen) settingsScreen).onPhotoPicked(uri);
        }
    }

    /** Resultado de la ventana de permisos de calendario. */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ContactsHelper.REQ_READ) {
            boolean granted = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            android.widget.Toast.makeText(this, granted
                    ? "Contactos permitidos. Vuelve a tocar Importar."
                    : "Sin permiso de contactos no se pueden importar.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode != CalendarSync.REQ_PERMS) return;
        boolean ok = grantResults.length == 2
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                && grantResults[1] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        Store s = Store.get(this);
        if (ok) {
            Store.put(s.settings(), "calSync", true);
            s.save();
            int n = CalendarSync.syncAll(this, s);
            android.widget.Toast.makeText(this, "Calendario activado. Citas enviadas: " + n, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            Store.put(s.settings(), "calSync", false);
            s.save();
            android.widget.Toast.makeText(this, "Sin permiso de calendario no se puede sincronizar.", android.widget.Toast.LENGTH_LONG).show();
        }
        if (settingsScreen != null) settingsScreen.refresh();
    }

    /** Aplica el tema elegido y reconstruye la interfaz. */
    public void applyThemeAndRestart() {
        recreate();
    }

    /** Refresca las pantallas ya creadas (por ejemplo, tras guardar datos en otra). */
    public void refreshAll() {
        for (Screen s : screens) {
            if (s.view != null) s.refresh();
        }
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
