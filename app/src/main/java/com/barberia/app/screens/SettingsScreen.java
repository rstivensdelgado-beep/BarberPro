package com.barberia.app.screens;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import com.barberia.app.CalendarSync;
import com.barberia.app.License;
import com.barberia.app.MainActivity;
import com.barberia.app.Screen;
import com.barberia.app.Store;
import com.barberia.app.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Ajustes: negocio, barbero, horario, pago, fidelidad, recordatorios, servicios, tema y datos. */
public class SettingsScreen extends Screen {

    private static final String[] DAY_KEYS = {"lun", "mar", "mie", "jue", "vie", "sab", "dom"};
    private static final String[] DAY_NAMES = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final int[] LEAD_VALUES = {1440, 720, 120};
    private static final String[] LEAD_NAMES = {"24 horas antes", "12 horas antes", "2 horas antes"};

    private LinearLayout content;
    /** Clave de la sección abierta; solo una a la vez. null = todas cerradas. */
    private String openSection = null;
    private boolean opened = false;

    public SettingsScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View build() {
        content = Ui.column(activity);
        return Ui.page(activity, content);
    }

    /** Tarjeta de activación: muestra el código del dispositivo y permite activar la app. */
    private LinearLayout activationCard() {
        LinearLayout card = Ui.card(activity);
        final boolean on = License.isActivated(activity);
        long days = License.trialDaysLeft(activity);
        boolean expired = License.isExpired(activity);
        String title = on ? "App activada · " + License.planLabel(activity)
                : (expired ? "Licencia vencida"
                : (days > 0 ? "Prueba gratis: quedan " + days + " día(s)" : "Prueba terminada"));
        String help = on
                ? License.expiryText(activity)
                : (expired
                    ? "Tu licencia ya venció. Envía tu código de dispositivo al vendedor y escribe el nuevo código."
                    : "Envía tu código de dispositivo al vendedor y escribe el código de activación que te devuelva.");
        card.addView(Ui.text(activity, title, 16, on ? Ui.OK : Ui.ACCENT, true));
        card.addView(Ui.muted(activity, help));
        card.addView(Ui.spacer(activity, 12));
        card.addView(Ui.muted(activity, "Tu código de dispositivo"));
        final String code = License.deviceCode(activity);
        card.addView(Ui.text(activity, code, 17, Ui.INK, true));

        Button copy = Ui.button(activity, "Copiar código", false);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("codigo", code));
                toast("Código copiado");
            }
        });
        card.addView(copy);

        if (!on) {
            card.addView(Ui.spacer(activity, 12));
            final EditText input = Ui.input(activity, "Código de activación", false);
            card.addView(input);
            card.addView(Ui.spacer(activity, 8));
            Button act = Ui.button(activity, "Activar", true);
            act.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (License.activate(activity, input.getText().toString())) {
                        toast("App activada. Ya puedes usar todas las funciones.");
                        refresh();
                    } else {
                        toast("Código incorrecto para este dispositivo.");
                    }
                }
            });
            LinearLayout row = Ui.row(activity);
            row.addView(Ui.spacer(activity, 1), Ui.weight(1));
            row.addView(act);
            card.addView(row);
        }
        return card;
    }

    @Override
    public void refresh() {
        if (content == null) return;
        // Si la app aún no está activada, la sección de activación se abre sola la primera vez.
        if (!opened) {
            opened = true;
            if (!License.isActivated(activity)) openSection = "activacion";
        }
        content.removeAllViews();
        content.addView(Ui.title(activity, "Ajustes"));
        content.addView(Ui.muted(activity, "Toca una sección para abrirla."));

        section("activacion", "Activación", "Prueba o código de activación", activationCard());
        section("negocio", "Negocio y barbero", "Nombre, foto y datos del barbero", businessCard());
        section("horario", "Horario de trabajo", "Días y horas de atención", scheduleCard());
        section("fidelidad", "Fidelidad", "Sellos y premio", loyaltyCard());
        section("recordatorios", "Recordatorios", "Antelación de los avisos", reminderCard());
        section("servicios", "Catálogo de servicios", "Precios y duración", servicesCard());
        section("apariencia", "Apariencia", "Tema, colores y tamaño de letra", themeCard());
        section("calendario", "Calendario", "Sincronizar con el móvil", calendarCard());
        section("datos", "Datos", "Respaldo y borrado", dataCard());
    }

    /** Sección plegable: el título siempre se ve; el contenido solo si está abierta. */
    private void section(final String key, String title, String hint, View body) {
        final boolean open = key.equals(openSection);
        LinearLayout head = Ui.row(activity);
        head.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 14));
        LinearLayout texts = Ui.column(activity);
        texts.addView(Ui.text(activity, title, 16, Ui.INK, true));
        texts.addView(Ui.muted(activity, hint));
        head.addView(texts, Ui.weight(1));
        head.addView(Ui.text(activity, open ? "−" : "+", 22, Ui.ACCENT, true));
        head.setClickable(true);
        head.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSection = open ? null : key;
                refresh();
            }
        });
        content.addView(head, Ui.matchWrap());
        if (open) content.addView(body, Ui.matchWrap());
        content.addView(gap());
    }

    // ---------- Negocio y barbero ----------

    private LinearLayout businessCard() {
        LinearLayout card = Ui.card(activity);
        final JSONObject p = store.profile();
        final EditText shop = Ui.input(activity, "Nombre de la barbería", false);
        shop.setText(store.settings().optString("shopName", ""));
        final EditText name = Ui.input(activity, "Nombre del barbero", false);
        name.setText(p.optString("name", ""));
        final EditText nick = Ui.input(activity, "Apodo (opcional)", false);
        nick.setText(p.optString("nickname", ""));

        LinearLayout photoRow = Ui.row(activity);
        photoRow.addView(avatar(p.optString("photo", "")));
        LinearLayout photoActions = Ui.column(activity);
        photoActions.setPadding(Ui.dp(activity, 14), 0, 0, 0);
        photoActions.addView(linkAction("Cambiar foto", Ui.INK, new Runnable() {
            public void run() { activity.pickPhoto(); }
        }));
        if (!p.optString("photo", "").isEmpty()) {
            photoActions.addView(linkAction("Quitar foto", Ui.DANGER, new Runnable() {
                public void run() {
                    Store.put(p, "photo", "");
                    store.save();
                    refresh();
                }
            }));
        }
        photoRow.addView(photoActions, Ui.weight(1));

        card.addView(Ui.label(activity, "Foto"));
        card.addView(photoRow);
        card.addView(Ui.label(activity, "BarberPro"));
        card.addView(shop);
        card.addView(Ui.label(activity, "Barbero"));
        card.addView(name);
        card.addView(Ui.spacer(activity, 8));
        card.addView(nick);
        card.addView(Ui.spacer(activity, 14));
        Button save = Ui.button(activity, "Guardar datos", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) { toast("Escribe el nombre del barbero."); return; }
                Store.put(store.settings(), "shopName", shop.getText().toString().trim());
                Store.put(p, "name", n);
                Store.put(p, "nickname", nick.getText().toString().trim());
                store.save();
                toast("Datos guardados");
                activity.refreshAll();
            }
        });
        card.addView(alignEnd(save));
        return card;
    }

    /** Foto circular del barbero, o un círculo con sus iniciales si no hay foto. */
    private View avatar(String base64) {
        final int size = Ui.dp(activity, 72);
        ImageView img = new ImageView(activity);
        img.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        img.setClipToOutline(true);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Ui.LINE);
        img.setBackground(g);
        if (base64 != null && !base64.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                img.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            } catch (IllegalArgumentException ignored) { }
        }
        return img;
    }

    /** Llamado por MainActivity cuando el usuario elige una foto. */
    public void onPhotoPicked(Uri uri) {
        try {
            InputStream in = activity.getContentResolver().openInputStream(uri);
            Bitmap src = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            if (src == null) { toast("No se pudo leer la imagen."); return; }

            // Recorte cuadrado centrado y reducción a 240 px para no ocupar mucha memoria.
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Bitmap small = Bitmap.createScaledBitmap(square, 240, 240, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, 85, out);
            String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);

            Store.put(store.profile(), "photo", b64);
            store.save();
            toast("Foto guardada");
            refresh();
        } catch (Exception e) {
            toast("No se pudo guardar la foto.");
        }
    }

    // ---------- Horario ----------

    private LinearLayout scheduleCard() {
        LinearLayout card = Ui.card(activity);
        JSONObject schedule = store.profile().optJSONObject("schedule");
        if (schedule == null) schedule = new JSONObject();
        for (int i = 0; i < DAY_KEYS.length; i++) {
            if (i > 0) card.addView(gap());
            card.addView(dayRow(schedule, DAY_KEYS[i], DAY_NAMES[i]));
        }
        return card;
    }

    private LinearLayout dayRow(final JSONObject schedule, final String key, String label) {
        JSONObject d = schedule.optJSONObject(key);
        if (d == null) {
            d = new JSONObject();
            Store.put(d, "on", false);
            Store.put(d, "start", "09:00");
            Store.put(d, "end", "18:00");
            Store.put(schedule, key, d);
        }
        final JSONObject day = d;
        LinearLayout box = Ui.column(activity);
        LinearLayout top = Ui.row(activity);
        boolean on = day.optBoolean("on");
        top.addView(Ui.text(activity, label, 15, on ? Ui.INK : Ui.MUTED, true), Ui.weight(1));
        top.addView(linkAction(on ? "Abierto" : "Cerrado", on ? Ui.ACCENT : Ui.MUTED, new Runnable() {
            public void run() {
                Store.put(day, "on", !day.optBoolean("on"));
                store.save();
                refresh();
            }
        }));
        box.addView(top);

        if (on) {
            LinearLayout times = Ui.row(activity);
            times.setPadding(0, Ui.dp(activity, 6), 0, 0);
            times.addView(timeButton(day, "start", "Inicio"), Ui.weight(1));
            times.addView(Ui.text(activity, "a", 13, Ui.MUTED, false));
            times.addView(timeButton(day, "end", "Fin"), Ui.weight(1));
            box.addView(times);
        }
        return box;
    }

    private View timeButton(final JSONObject day, final String field, final String hint) {
        final Button b = Ui.button(activity, day.optString(field, "09:00"), false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int m = Store.toMin(day.optString(field, "09:00"));
                new TimePickerDialog(activity, new TimePickerDialog.OnTimeSetListener() {
                    @Override public void onTimeSet(TimePicker p, int h, int min) {
                        Store.put(day, field, Store.hhmm(h * 60 + min));
                        store.save();
                        refresh();
                    }
                }, m / 60, m % 60, true).show();
            }
        });
        return b;
    }

    // ---------- Fidelidad ----------

    private LinearLayout loyaltyCard() {
        LinearLayout card = Ui.card(activity);
        final JSONObject l = store.loyalty();
        final EditText threshold = Ui.input(activity, "Sellos para el premio", true);
        threshold.setText(String.valueOf(l.optInt("threshold", 10)));
        final EditText reward = Ui.input(activity, "Premio", false);
        reward.setText(l.optString("reward", ""));
        card.addView(Ui.label(activity, "Sellos para el premio"));
        card.addView(threshold);
        card.addView(Ui.label(activity, "Premio"));
        card.addView(reward);
        card.addView(Ui.spacer(activity, 14));
        Button save = Ui.button(activity, "Guardar fidelidad", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int t = (int) parseOr(threshold.getText().toString(), 0);
                String r = reward.getText().toString().trim();
                if (t < 2 || t > 50) { toast("Los sellos deben estar entre 2 y 50."); return; }
                if (r.isEmpty()) { toast("Escribe el premio."); return; }
                Store.put(l, "threshold", t);
                Store.put(l, "reward", r);
                store.save();
                toast("Fidelidad guardada");
                refresh();
            }
        });
        card.addView(alignEnd(save));
        return card;
    }

    // ---------- Recordatorios ----------

    private LinearLayout reminderCard() {
        LinearLayout card = Ui.card(activity);
        final JSONObject s = store.settings();
        int current = s.optInt("lead", 1440);
        int idx = 0;
        for (int i = 0; i < LEAD_VALUES.length; i++) if (LEAD_VALUES[i] == current) idx = i;
        final int[] lead = {idx};
        final Button b = Ui.button(activity, LEAD_NAMES[lead[0]], false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                lead[0] = (lead[0] + 1) % LEAD_VALUES.length;
                Store.put(s, "lead", LEAD_VALUES[lead[0]]);
                store.save();
                b.setText(LEAD_NAMES[lead[0]]);
            }
        });
        card.addView(Ui.label(activity, "Avisar con antelación"));
        card.addView(b);
        card.addView(Ui.spacer(activity, 8));
        card.addView(Ui.muted(activity, "El envío por WhatsApp se hace desde la Agenda, con el mensaje ya preparado."));
        card.addView(Ui.spacer(activity, 14));
        card.addView(Ui.muted(activity, "Confirmación automática: si un cliente con cita pendiente responde \"sí\", \"ok\" o \"vale\" por WhatsApp, la cita se confirma sola. Necesitas permitir el acceso a notificaciones para BarberPro."));
        card.addView(Ui.spacer(activity, 8));
        card.addView(linkAction("Permitir confirmación por WhatsApp", Ui.ACCENT, new Runnable() {
            public void run() {
                try {
                    activity.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } catch (Exception e) {
                    toast("No se pudo abrir los ajustes de notificaciones");
                }
            }
        }));
        return card;
    }

    // ---------- Servicios ----------

    private LinearLayout servicesCard() {
        LinearLayout card = Ui.card(activity);
        JSONArray list = store.arr("services");
        if (list.length() == 0) card.addView(Ui.muted(activity, "No hay servicios. Agrega el primero."));
        for (int i = 0; i < list.length(); i++) {
            final JSONObject s = list.optJSONObject(i);
            if (s == null) continue;
            if (i > 0) card.addView(gap());
            LinearLayout box = Ui.column(activity);
            boolean active = s.optBoolean("active", true);
            LinearLayout top = Ui.row(activity);
            LinearLayout info = Ui.column(activity);
            info.addView(Ui.text(activity, s.optString("name"), 15, active ? Ui.INK : Ui.MUTED, true));
            info.addView(Ui.muted(activity, s.optInt("minutes") + " min" + (active ? "" : " · inactivo")));
            top.addView(info, Ui.weight(1));
            top.addView(Ui.text(activity, Store.money(s.optDouble("price")), 15, active ? Ui.INK : Ui.MUTED, false));
            box.addView(top);
            LinearLayout actions = Ui.row(activity);
            actions.setPadding(0, Ui.dp(activity, 4), 0, 0);
            actions.addView(linkAction("Editar", Ui.INK, new Runnable() {
                public void run() { showServiceDialog(s); }
            }));
            actions.addView(linkAction(active ? "Pausar" : "Activar", Ui.MUTED, new Runnable() {
                public void run() {
                    Store.put(s, "active", !s.optBoolean("active", true));
                    store.save();
                    refresh();
                }
            }));
            actions.addView(linkAction("Eliminar", Ui.DANGER, new Runnable() {
                public void run() { deleteService(s.optString("id")); }
            }));
            box.addView(actions);
            card.addView(box);
        }
        card.addView(Ui.spacer(activity, 12));
        Button add = Ui.button(activity, "Nuevo servicio", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showServiceDialog(null); }
        });
        card.addView(alignEnd(add));
        return card;
    }

    private void showServiceDialog(final JSONObject editing) {
        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), 0);
        final EditText name = Ui.input(activity, "Nombre del servicio", false);
        final EditText price = Ui.input(activity, "Precio en CUP", true);
        final EditText minutes = Ui.input(activity, "Duración en minutos", true);
        if (editing != null) {
            name.setText(editing.optString("name"));
            price.setText(String.valueOf((long) editing.optDouble("price")));
            minutes.setText(String.valueOf(editing.optInt("minutes")));
        }
        form.addView(Ui.label(activity, "Nombre"));
        form.addView(name);
        form.addView(Ui.label(activity, "Precio"));
        form.addView(price);
        form.addView(Ui.label(activity, "Duración"));
        form.addView(minutes);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(editing == null ? "Nuevo servicio" : "Editar servicio")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String n = name.getText().toString().trim();
                double p = parseOr(price.getText().toString(), -1);
                int m = (int) parseOr(minutes.getText().toString(), 0);
                if (n.isEmpty()) { toast("Escribe el nombre del servicio."); return; }
                if (p < 0) { toast("Indica un precio válido."); return; }
                if (m < 5 || m > 240) { toast("La duración debe estar entre 5 y 240 minutos."); return; }
                JSONObject s = editing;
                if (s == null) {
                    s = new JSONObject();
                    Store.put(s, "id", store.newId("s"));
                    Store.put(s, "active", true);
                    Store.add(store.arr("services"), s);
                }
                Store.put(s, "name", n);
                Store.put(s, "price", p);
                Store.put(s, "minutes", m);
                store.save();
                dialog.dismiss();
                refresh();
            }
        });
    }

    private void deleteService(String id) {
        JSONArray list = store.arr("services");
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) { Store.removeAt(list, i); break; }
        }
        store.save();
        refresh();
    }

    // ---------- Calendario del móvil ----------

    private LinearLayout calendarCard() {
        LinearLayout card = Ui.card(activity);
        final boolean on = CalendarSync.enabled(store);
        LinearLayout row = Ui.row(activity);
        row.addView(Ui.text(activity, on ? "Sincronización activada" : "Sincronización apagada", 15, Ui.INK, true), Ui.weight(1));
        row.addView(linkAction(on ? "Apagar" : "Activar", Ui.ACCENT, new Runnable() {
            public void run() {
                if (on) {
                    Store.put(store.settings(), "calSync", false);
                    store.save();
                    refresh();
                    return;
                }
                // Primero pide el permiso; al aceptarlo, MainActivity activa la sincronización.
                if (!CalendarSync.hasPermission(activity)) {
                    CalendarSync.request(activity);
                    return;
                }
                Store.put(store.settings(), "calSync", true);
                store.save();
                int n = CalendarSync.syncAll(activity, store);
                toast("Calendario activado. Citas enviadas: " + n);
                refresh();
            }
        }));
        card.addView(row);
        card.addView(Ui.spacer(activity, 8));
        if (on) {
            card.addView(linkAction("Enviar otra vez todas las citas", Ui.INK, new Runnable() {
                public void run() {
                    int n = CalendarSync.syncAll(activity, store);
                    toast("Citas enviadas al calendario: " + n);
                }
            }));
            card.addView(Ui.spacer(activity, 6));
        }
        card.addView(Ui.muted(activity, "Las citas nuevas, sus cambios y sus cancelaciones se reflejan en el calendario de este móvil. Los bloqueos no se envían."));
        return card;
    }

    // ---------- Apariencia ----------

    private LinearLayout themeCard() {
        LinearLayout card = Ui.card(activity);
        final boolean dark = store.settings().optBoolean("dark", true);
        LinearLayout row = Ui.row(activity);
        row.addView(Ui.text(activity, dark ? "Tema oscuro" : "Tema claro", 15, Ui.INK, true), Ui.weight(1));
        row.addView(linkAction(dark ? "Usar claro" : "Usar oscuro", Ui.ACCENT, new Runnable() {
            public void run() {
                Store.put(store.settings(), "dark", !dark);
                store.save();
                activity.applyThemeAndRestart();
            }
        }));
        card.addView(row);

        // Paleta de colores: dorado, cristal o Material You (según el teléfono).
        card.addView(Ui.spacer(activity, 12));
        final String palette = store.settings().optString("palette", "dorado");
        final String next = Ui.nextPalette(palette);
        LinearLayout paletteRow = Ui.row(activity);
        paletteRow.addView(Ui.text(activity, "Colores: " + Ui.paletteName(palette), 15, Ui.INK, true), Ui.weight(1));
        paletteRow.addView(linkAction("Usar " + Ui.paletteName(next), Ui.ACCENT, new Runnable() {
            public void run() {
                Store.put(store.settings(), "palette", next);
                store.save();
                activity.applyThemeAndRestart();
            }
        }));
        card.addView(paletteRow);
        if (Build.VERSION.SDK_INT < 31) {
            card.addView(Ui.spacer(activity, 6));
            card.addView(Ui.muted(activity, "Material You necesita Android 12 o superior."));
        }

        // Tamaño de letra: normal, grande o muy grande. Se aplica al reconstruir la pantalla.
        card.addView(Ui.spacer(activity, 12));
        final String fs = store.settings().optString("fontSize", "normal");
        final String fsNext = "normal".equals(fs) ? "grande" : ("grande".equals(fs) ? "muy_grande" : "normal");
        LinearLayout fontRow = Ui.row(activity);
        fontRow.addView(Ui.text(activity, "Tamaño de letra: " + fontName(fs), 15, Ui.INK, true), Ui.weight(1));
        fontRow.addView(linkAction("Usar " + fontName(fsNext), Ui.ACCENT, new Runnable() {
            public void run() {
                Store.put(store.settings(), "fontSize", fsNext);
                store.save();
                activity.applyThemeAndRestart();
            }
        }));
        card.addView(fontRow);
        return card;
    }

    private String fontName(String key) {
        if ("grande".equals(key)) return "grande";
        if ("muy_grande".equals(key)) return "muy grande";
        return "normal";
    }

    // ---------- Datos ----------

    private LinearLayout dataCard() {
        LinearLayout card = Ui.card(activity);
        card.addView(linkAction("Exportar respaldo (copiar o enviar)", Ui.INK, new Runnable() {
            public void run() {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, store.exportJson());
                activity.startActivity(Intent.createChooser(i, "Respaldo"));
            }
        }));
        card.addView(Ui.spacer(activity, 10));
        card.addView(linkAction("Borrar todos los datos", Ui.DANGER, new Runnable() {
            public void run() { confirmReset(); }
        }));
        card.addView(Ui.spacer(activity, 6));
        card.addView(Ui.muted(activity, "Los datos se guardan en este equipo. Exporta un respaldo antes de borrarlos."));
        return card;
    }

    private void confirmReset() {
        new AlertDialog.Builder(activity)
                .setTitle("¿Borrar todos los datos?")
                .setMessage("Se eliminarán citas, caja, clientes, inventario y promociones. No se puede deshacer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        store.reset();
                        toast("Datos borrados");
                        activity.recreate();
                    }
                })
                .show();
    }

    // ---------- Utilidades ----------

    private LinearLayout alignEnd(View v) {
        LinearLayout row = Ui.row(activity);
        row.setGravity(android.view.Gravity.END);
        row.addView(v);
        return row;
    }

    private View linkAction(String label, int color, final Runnable r) {
        View l = Ui.link(activity, label, color);
        l.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        return l;
    }

    private View gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
        lp.topMargin = Ui.dp(activity, 12);
        lp.bottomMargin = Ui.dp(activity, 12);
        View v = new View(activity);
        v.setBackgroundColor(Ui.LINE);
        v.setLayoutParams(lp);
        return v;
    }

    private double parseOr(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }
}
