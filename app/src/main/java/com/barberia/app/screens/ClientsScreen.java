package com.barberia.app.screens;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.barberia.app.Calc;
import com.barberia.app.ContactsHelper;
import com.barberia.app.MainActivity;
import com.barberia.app.Screen;
import com.barberia.app.Store;
import com.barberia.app.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Clientes: fichas, fidelidad (sellos, canjes y reactivación) y promociones. */
public class ClientsScreen extends Screen {

    private static final String[] PROMO_TYPES = {"porcentaje", "monto"};
    private static final String[] PROMO_TYPE_NAMES = {"Descuento %", "Descuento fijo"};
    private static final String[] AUDIENCES = {"todos", "inactivos", "premio"};
    private static final String[] AUDIENCE_NAMES = {"Todos", "Inactivos (30 días)", "Con premio listo"};

    private LinearLayout content;
    private LinearLayout fichaList;
    private String section = "fichas";
    private EditText search;
    private String query = "";

    public ClientsScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View build() {
        content = Ui.column(activity);
        return Ui.page(activity, content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        content.addView(Ui.title(activity, "Clientes"));
        content.addView(Ui.muted(activity, "Fichas, fidelidad y promociones."));
        content.addView(segments());

        if ("fichas".equals(section)) {
            buildFichas();
        } else if ("fidelidad".equals(section)) {
            buildFidelidad();
        } else {
            buildPromos();
        }
    }

    private LinearLayout segments() {
        LinearLayout r = Ui.row(activity);
        r.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 6));
        String[] keys = {"fichas", "fidelidad", "promos"};
        String[] names = {"Fichas", "Fidelidad", "Promociones"};
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            View t = Ui.link(activity, names[i], k.equals(section) ? Ui.INK : Ui.MUTED);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { section = k; refresh(); }
            });
            r.addView(t, Ui.weight(1));
        }
        return r;
    }

    // ---------- Fichas ----------

    private void buildFichas() {
        Button add = Ui.button(activity, "Nueva ficha", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showFichaDialog(null); }
        });
        LinearLayout addRow = Ui.column(activity);
        addRow.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 8));
        addRow.addView(add);
        content.addView(addRow, Ui.matchWrap());

        Button imp = Ui.button(activity, "Importar de mis contactos", false);
        imp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { importContacts(); }
        });
        LinearLayout impRow = Ui.column(activity);
        impRow.setPadding(0, 0, 0, Ui.dp(activity, 8));
        impRow.addView(imp);
        content.addView(impRow, Ui.matchWrap());

        if (search == null) {
            search = Ui.input(activity, "Buscar por nombre, apodo o teléfono", false);
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { renderFichaList(); }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
        }
        content.addView(search, Ui.matchWrap());
        fichaList = Ui.card(activity);
        content.addView(fichaList, Ui.matchWrap());
        renderFichaList();
    }

    /** Solo re-dibuja la lista; así el buscador conserva el foco mientras se escribe. */
    private void renderFichaList() {
        if (fichaList == null) return;
        fichaList.removeAllViews();
        List<Map<String, Object>> list = allClients();
        int shown = 0;
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase();
        for (Map<String, Object> c : list) {
            if (!q.isEmpty() && !matches(c, q)) continue;
            if (shown > 0) fichaList.addView(gap());
            fichaList.addView(fichaRow(c));
            shown++;
        }
        if (shown == 0) {
            if (list.isEmpty()) {
                fichaList.addView(Ui.emptyState(activity, "Aún no hay clientes",
                        "Se crean solos al reservar, o agrégalos con Nueva ficha."));
            } else {
                fichaList.addView(Ui.muted(activity, "Ningún cliente coincide con la búsqueda."));
            }
        }
    }

    private boolean matches(Map<String, Object> c, String q) {
        String name = String.valueOf(c.get("name")).toLowerCase();
        String nick = String.valueOf(c.get("nick")).toLowerCase();
        String phone = String.valueOf(c.get("phone")).replaceAll("\\D", "");
        String qd = q.replaceAll("\\D", "");
        return name.contains(q) || nick.contains(q) || (!qd.isEmpty() && phone.contains(qd));
    }

    /** Fichas guardadas más clientes que solo existen en la agenda, con sus cifras. */
    private List<Map<String, Object>> allClients() {
        Map<String, Double> spent = spentByKey();
        Map<String, String[]> visits = Calc.lastVisits(store);
        List<Map<String, Object>> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        JSONArray fichas = store.arr("clients");
        for (int i = 0; i < fichas.length(); i++) {
            JSONObject f = fichas.optJSONObject(i);
            if (f == null) continue;
            String key = Store.clientKey(f.optString("name"), f.optString("phone"));
            seen.add(key);
            out.add(entry(f, true, key, spent, visits));
        }
        for (Map.Entry<String, String[]> e : visits.entrySet()) {
            if (seen.contains(e.getKey())) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("saved", false);
            m.put("key", e.getKey());
            m.put("name", e.getValue()[0]);
            m.put("nick", "");
            m.put("phone", phoneFromAppts(e.getKey()));
            m.put("note", "");
            m.put("birthday", "");
            m.put("visits", Calc.visitsOf(store, e.getKey()));
            m.put("spent", spent.containsKey(e.getKey()) ? spent.get(e.getKey()) : 0d);
            m.put("last", e.getValue()[1]);
            out.add(m);
        }
        Collections.sort(out, new Comparator<Map<String, Object>>() {
            @Override public int compare(Map<String, Object> a, Map<String, Object> b) {
                return String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name")));
            }
        });
        return out;
    }

    private Map<String, Object> entry(JSONObject f, boolean saved, String key, Map<String, Double> spent, Map<String, String[]> visits) {
        Map<String, Object> m = new HashMap<>();
        m.put("saved", saved);
        m.put("id", f.optString("id"));
        m.put("key", key);
        m.put("name", f.optString("name"));
        m.put("nick", f.optString("nick"));
        m.put("phone", f.optString("phone"));
        m.put("note", f.optString("note"));
        m.put("birthday", f.optString("birthday"));
        m.put("visits", Calc.visitsOf(store, key));
        m.put("spent", spent.containsKey(key) ? spent.get(key) : 0d);
        String[] v = visits.get(key);
        m.put("last", v == null ? "" : v[1]);
        return m;
    }

    private Map<String, Double> spentByKey() {
        Map<String, String> keyByAppt = new HashMap<>();
        JSONArray citas = store.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject a = citas.optJSONObject(i);
            if (a != null) keyByAppt.put(a.optString("id"), Store.clientKey(a.optString("client"), a.optString("phone")));
        }
        Map<String, Double> out = new HashMap<>();
        JSONArray movs = store.arr("movs");
        for (int i = 0; i < movs.length(); i++) {
            JSONObject m = movs.optJSONObject(i);
            if (m == null || !"ingreso".equals(m.optString("type")) || !m.has("apptId")) continue;
            String key = keyByAppt.get(m.optString("apptId"));
            if (key == null) continue;
            Double cur = out.get(key);
            out.put(key, (cur == null ? 0 : cur) + m.optDouble("amount"));
        }
        return out;
    }

    private String phoneFromAppts(String key) {
        JSONArray citas = store.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject a = citas.optJSONObject(i);
            if (a != null && key.equals(Store.clientKey(a.optString("client"), a.optString("phone")))) {
                return a.optString("phone");
            }
        }
        return "";
    }

    private LinearLayout fichaRow(final Map<String, Object> c) {
        LinearLayout box = Ui.column(activity);
        String nick = String.valueOf(c.get("nick"));
        String name = String.valueOf(c.get("name")) + (nick.isEmpty() ? "" : " “" + nick + "”");
        LinearLayout top = Ui.row(activity);
        LinearLayout info = Ui.column(activity);
        info.addView(Ui.text(activity, name, 15, Ui.INK, true));
        String phone = String.valueOf(c.get("phone"));
        info.addView(Ui.muted(activity, (phone.isEmpty() ? "Sin teléfono" : phone)
                + " · " + c.get("visits") + " visitas"));
        top.addView(info, Ui.weight(1));
        top.addView(Ui.text(activity, Store.money((Double) c.get("spent")), 14, Ui.INK, false));
        box.addView(top);

        String last = String.valueOf(c.get("last"));
        if (!last.isEmpty()) box.addView(Ui.muted(activity, "Última visita " + Store.displayDate(last)));
        final String[] sug = Calc.suggestNext(store, String.valueOf(c.get("key")));
        if (sug != null) {
            box.addView(Ui.muted(activity, "Suele venir cada " + sug[0] + " días · próxima sugerida " + Store.displayDate(sug[1])));
        }
        String note = String.valueOf(c.get("note"));
        if (!note.isEmpty()) {
            TextViewNote(box, note);
        }

        LinearLayout actions = Ui.row(activity);
        actions.setPadding(0, Ui.dp(activity, 6), 0, 0);
        if (sug != null && !phone.isEmpty()) {
            actions.addView(linkAction("Sugerir cita", Ui.ACCENT, new Runnable() {
                public void run() { sendSuggestion(c, sug[1]); }
            }));
        }
        if ((Boolean) c.get("saved")) {
            actions.addView(linkAction("Editar", Ui.INK, new Runnable() {
                public void run() { showFichaDialog(c); }
            }));
            actions.addView(linkAction("Eliminar", Ui.DANGER, new Runnable() {
                public void run() { deleteFicha(String.valueOf(c.get("id"))); }
            }));
        } else {
            actions.addView(linkAction("Crear ficha", Ui.INK, new Runnable() {
                public void run() { showFichaDialog(c); }
            }));
        }
        box.addView(actions);
        return box;
    }

    /** Abre WhatsApp con un mensaje que sugiere la próxima cita. */
    private void sendSuggestion(Map<String, Object> c, String date) {
        String name = String.valueOf(c.get("name"));
        String shop = store.settings().optString("shopName", "BarberPro");
        String msg = "Hola " + name + ", ¿te reservo tu próximo turno? Te sugiero el "
                + Store.displayDate(date) + ". Respóndeme y lo dejamos listo. — " + shop;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, msg);
        i.setPackage("com.whatsapp");
        try {
            activity.startActivity(i);
        } catch (ActivityNotFoundException e) {
            i.setPackage(null);
            activity.startActivity(Intent.createChooser(i, "Enviar sugerencia"));
        }
    }

    private void TextViewNote(LinearLayout box, String note) {
        LinearLayout quote = Ui.column(activity);
        quote.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 6), 0, Ui.dp(activity, 2));
        quote.addView(Ui.text(activity, note, 14, Ui.INK, false));
        box.addView(quote);
    }

    @Override
    public boolean hasFab() {
        return true;
    }

    @Override
    public void onFab() {
        showFichaDialog(null);
    }

    private void showFichaDialog(final Map<String, Object> prefill) {
        final boolean editing = prefill != null && Boolean.TRUE.equals(prefill.get("saved"));
        final String editId = editing ? String.valueOf(prefill.get("id")) : null;

        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), 0);
        final EditText name = Ui.input(activity, "Nombre", false);
        final EditText nick = Ui.input(activity, "Apodo (opcional)", false);
        final EditText phone = Ui.input(activity, "Teléfono", false);
        final EditText birthday = Ui.input(activity, "Cumpleaños (aaaa-mm-dd)", false);
        final EditText note = Ui.input(activity, "Preferencias y notas", false);
        if (prefill != null) {
            name.setText(String.valueOf(prefill.get("name")));
            nick.setText(String.valueOf(prefill.get("nick")));
            phone.setText(String.valueOf(prefill.get("phone")));
            birthday.setText(String.valueOf(prefill.get("birthday")));
            note.setText(String.valueOf(prefill.get("note")));
        }
        form.addView(Ui.label(activity, "Nombre"));
        form.addView(name);
        form.addView(Ui.label(activity, "Apodo"));
        form.addView(nick);
        form.addView(Ui.label(activity, "Teléfono"));
        form.addView(phone);
        form.addView(Ui.label(activity, "Cumpleaños"));
        form.addView(birthday);
        form.addView(Ui.label(activity, "Notas"));
        form.addView(note);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(editing ? "Editar ficha" : "Nueva ficha")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.show();
        Ui.popIn(dialog.getWindow().getDecorView());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String n = name.getText().toString().trim();
                String p = phone.getText().toString().trim();
                if (n.isEmpty()) { toast("Escribe el nombre."); return; }
                if (!p.isEmpty() && p.replaceAll("\\D", "").length() < 6) { toast("El teléfono debe tener al menos 6 dígitos."); return; }
                String key = Store.clientKey(n, p);
                if (!editing && existsFicha(key)) { toast("Ya existe una ficha con ese nombre o teléfono."); return; }

                JSONObject f;
                if (editing) {
                    f = findFicha(editId);
                    if (f == null) { toast("No se encontró la ficha."); return; }
                } else {
                    f = new JSONObject();
                    Store.put(f, "id", store.newId("c"));
                    Store.add(store.arr("clients"), f);
                }
                Store.put(f, "name", n);
                Store.put(f, "nick", nick.getText().toString().trim());
                Store.put(f, "phone", p);
                Store.put(f, "birthday", birthday.getText().toString().trim());
                Store.put(f, "note", note.getText().toString().trim());
                store.save();
                dialog.dismiss();
                refresh();
            }
        });
    }

    private boolean existsFicha(String key) {
        JSONArray f = store.arr("clients");
        for (int i = 0; i < f.length(); i++) {
            JSONObject o = f.optJSONObject(i);
            if (o != null && key.equals(Store.clientKey(o.optString("name"), o.optString("phone")))) return true;
        }
        return false;
    }

    private JSONObject findFicha(String id) {
        JSONArray f = store.arr("clients");
        for (int i = 0; i < f.length(); i++) {
            JSONObject o = f.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    /** Trae a Clientes los contactos del móvil que aún no tienen ficha, elegidos uno por uno. */
    private void importContacts() {
        if (!ContactsHelper.hasPermission(activity)) {
            ContactsHelper.request(activity);
            toast("Acepta el permiso de contactos y vuelve a tocar Importar.");
            return;
        }
        final List<ContactsHelper.Contact> fresh = new ArrayList<>();
        for (ContactsHelper.Contact c : ContactsHelper.readAll(activity)) {
            if (!existsFicha(Store.clientKey(c.name, c.phone))) fresh.add(c);
        }
        if (fresh.isEmpty()) {
            toast("No hay contactos nuevos para importar.");
            return;
        }
        String[] labels = new String[fresh.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = fresh.get(i).name + " · " + fresh.get(i).phone;
        }
        final boolean[] checked = new boolean[fresh.size()];
        new AlertDialog.Builder(activity)
                .setTitle("Importar contactos")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Importar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        int n = 0;
                        for (int i = 0; i < checked.length; i++) {
                            if (!checked[i]) continue;
                            ContactsHelper.Contact c = fresh.get(i);
                            JSONObject f = new JSONObject();
                            Store.put(f, "id", store.newId("c"));
                            Store.put(f, "name", c.name);
                            Store.put(f, "nick", "");
                            Store.put(f, "phone", c.phone);
                            Store.put(f, "birthday", "");
                            Store.put(f, "note", "");
                            Store.add(store.arr("clients"), f);
                            n++;
                        }
                        store.save();
                        toast("Clientes importados: " + n);
                        refresh();
                    }
                })
                .show();
    }

    /** Pide confirmación antes de quitar la ficha. */
    private void deleteFicha(final String id) {
        new AlertDialog.Builder(activity)
                .setTitle("¿Eliminar esta ficha?")
                .setMessage("El historial de citas y cobros se conserva.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { removeFicha(id); }
                })
                .show();
    }

    /** Quita la ficha y ofrece deshacer durante unos segundos. */
    private void removeFicha(String id) {
        JSONArray f = store.arr("clients");
        for (int i = 0; i < f.length(); i++) {
            final JSONObject removed = f.optJSONObject(i);
            if (removed != null && id.equals(removed.optString("id"))) {
                Store.removeAt(f, i);
                store.save();
                refresh();
                activity.showUndo("Ficha eliminada", new Runnable() {
                    @Override public void run() { restoreFicha(removed); }
                });
                return;
            }
        }
    }

    /** Devuelve una ficha eliminada. Vuelve al final; la lista se ordena por nombre al mostrarse. */
    private void restoreFicha(JSONObject ficha) {
        Store.add(store.arr("clients"), ficha);
        store.save();
        refresh();
    }

    // ---------- Fidelidad ----------

    private void buildFidelidad() {
        int threshold = store.loyalty().optInt("threshold", 10);
        String reward = store.loyalty().optString("reward", "Premio");

        LinearLayout head = Ui.card(activity);
        head.addView(Ui.muted(activity, "Cada visita completada suma un sello. Al llegar a " + threshold + " se canjea:"));
        head.addView(Ui.spacer(activity, 4));
        head.addView(Ui.text(activity, reward, 16, Ui.INK, true));
        head.addView(Ui.spacer(activity, 6));
        head.addView(Ui.muted(activity, "Puedes cambiar el número y el premio en Ajustes."));
        content.addView(head, Ui.matchWrap());

        content.addView(Ui.label(activity, "Tarjetas"));
        LinearLayout card = Ui.card(activity);
        List<Map<String, Object>> list = allClients();
        boolean any = false;
        for (Map<String, Object> c : list) {
            if (((Integer) c.get("visits")) == 0) continue;
            if (any) card.addView(gap());
            card.addView(stampRow(c, threshold, reward));
            any = true;
        }
        if (!any) card.addView(Ui.muted(activity, "Aún no hay visitas completadas."));
        content.addView(card, Ui.matchWrap());

        content.addView(Ui.label(activity, "Para reactivar"));
        LinearLayout react = Ui.card(activity);
        boolean anyR = false;
        String today = Store.today();
        for (Map<String, Object> c : list) {
            String last = String.valueOf(c.get("last"));
            if (last.isEmpty() || Store.daysBetween(last, today) < 30) continue;
            if (anyR) react.addView(gap());
            react.addView(reactRow(c, threshold, reward));
            anyR = true;
        }
        if (!anyR) react.addView(Ui.muted(activity, "Todos han venido en el último mes."));
        content.addView(react, Ui.matchWrap());
    }

    private LinearLayout stampRow(final Map<String, Object> c, final int threshold, final String reward) {
        LinearLayout box = Ui.column(activity);
        int available = Calc.stampsAvailable(store, String.valueOf(c.get("key")));
        LinearLayout top = Ui.row(activity);
        top.addView(Ui.text(activity, String.valueOf(c.get("name")), 15, Ui.INK, true), Ui.weight(1));
        top.addView(Ui.muted(activity, Math.max(0, available) + " de " + threshold));
        box.addView(top);
        box.addView(Ui.spacer(activity, 8));

        LinearLayout dots = Ui.row(activity);
        int filled = Math.min(Math.max(0, available), threshold);
        for (int i = 0; i < threshold; i++) {
            View d = new View(activity);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (i < filled) {
                g.setColor(Ui.INK);
            } else {
                g.setColor(Ui.SURFACE);
                g.setStroke(Ui.dp(activity, 1), Ui.MUTED);
            }
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(activity, 12), Ui.dp(activity, 12));
            lp.setMargins(0, 0, Ui.dp(activity, 5), Ui.dp(activity, 5));
            dots.addView(d, lp);
        }
        box.addView(dots);

        if (available >= threshold) {
            box.addView(Ui.spacer(activity, 6));
            box.addView(linkAction("Canjear: " + reward, Ui.ACCENT, new Runnable() {
                public void run() { redeem(String.valueOf(c.get("key"))); }
            }));
        }
        return box;
    }

    private LinearLayout reactRow(final Map<String, Object> c, final int threshold, final String reward) {
        LinearLayout box = Ui.column(activity);
        LinearLayout top = Ui.row(activity);
        top.addView(Ui.text(activity, String.valueOf(c.get("name")), 15, Ui.INK, true), Ui.weight(1));
        top.addView(Ui.muted(activity, Store.daysBetween(String.valueOf(c.get("last")), Store.today()) + " días"));
        box.addView(top);
        LinearLayout actions = Ui.row(activity);
        actions.setPadding(0, Ui.dp(activity, 4), 0, 0);
        actions.addView(linkAction("Enviar mensaje", Ui.INK, new Runnable() {
            public void run() { shareText(reactMessage(c, threshold, reward)); }
        }));
        box.addView(actions);
        return box;
    }

    private String reactMessage(Map<String, Object> c, int threshold, String reward) {
        String first = String.valueOf(c.get("name")).split(" ")[0];
        int available = Calc.stampsAvailable(store, String.valueOf(c.get("key")));
        return "Hola " + first + ", hace tiempo que no te vemos en la barbería. Ya llevas "
                + Math.max(0, available) + " de " + threshold + " sellos para tu " + reward.toLowerCase() + ". Te esperamos.";
    }

    private void redeem(String key) {
        JSONObject r = new JSONObject();
        Store.put(r, "id", store.newId("r"));
        Store.put(r, "key", key);
        Store.put(r, "date", Store.today());
        Store.add(store.arr("redemptions"), r);
        store.save();
        toast("Premio canjeado");
        refresh();
    }

    // ---------- Promociones ----------

    private void buildPromos() {
        Button add = Ui.button(activity, "Nueva promoción", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPromoDialog(null); }
        });
        LinearLayout addRow = Ui.column(activity);
        addRow.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 8));
        addRow.addView(add);
        content.addView(addRow, Ui.matchWrap());

        content.addView(Ui.label(activity, "Promociones"));
        LinearLayout card = Ui.card(activity);
        JSONArray promos = store.arr("promos");
        if (promos.length() == 0) card.addView(Ui.muted(activity, "Aún no hay promociones."));
        for (int i = 0; i < promos.length(); i++) {
            JSONObject p = promos.optJSONObject(i);
            if (p == null) continue;
            if (i > 0) card.addView(gap());
            card.addView(promoRow(p));
        }
        content.addView(card, Ui.matchWrap());
    }

    private String promoStatus(JSONObject p) {
        if (!p.optBoolean("active", true)) return "Pausada";
        String today = Store.today();
        String from = p.optString("from", "");
        String to = p.optString("to", "");
        if (!from.isEmpty() && today.compareTo(from) < 0) return "Programada";
        if (!to.isEmpty() && today.compareTo(to) > 0) return "Vencida";
        return "Vigente";
    }

    private LinearLayout promoRow(final JSONObject p) {
        LinearLayout box = Ui.column(activity);
        LinearLayout top = Ui.row(activity);
        LinearLayout info = Ui.column(activity);
        info.addView(Ui.text(activity, p.optString("name"), 15, Ui.INK, true));
        String value = "porcentaje".equals(p.optString("type")) ? p.optInt("value") + "%" : Store.money(p.optDouble("value"));
        String scope = "todos".equals(p.optString("serviceId")) ? "todos los servicios" : serviceName(p.optString("serviceId"));
        info.addView(Ui.muted(activity, promoStatus(p) + " · " + value + " en " + scope));
        String from = p.optString("from", "");
        String to = p.optString("to", "");
        if (!from.isEmpty() || !to.isEmpty()) {
            info.addView(Ui.muted(activity, (from.isEmpty() ? "" : "Desde " + Store.displayDate(from) + " ")
                    + (to.isEmpty() ? "" : "hasta " + Store.displayDate(to))));
        }
        top.addView(info, Ui.weight(1));
        box.addView(top);

        LinearLayout actions = Ui.row(activity);
        actions.setPadding(0, Ui.dp(activity, 6), 0, 0);
        actions.addView(linkAction("Compartir anuncio", Ui.INK, new Runnable() {
            public void run() { shareText(announcement(p)); }
        }));
        actions.addView(linkAction(p.optBoolean("active", true) ? "Pausar" : "Activar", Ui.MUTED, new Runnable() {
            public void run() {
                Store.put(p, "active", !p.optBoolean("active", true));
                store.save();
                refresh();
            }
        }));
        actions.addView(linkAction("Editar", Ui.MUTED, new Runnable() {
            public void run() { showPromoDialog(p); }
        }));
        actions.addView(linkAction("Eliminar", Ui.DANGER, new Runnable() {
            public void run() { deletePromo(p.optString("id")); }
        }));
        box.addView(actions);
        return box;
    }

    private String announcement(JSONObject p) {
        String value = "porcentaje".equals(p.optString("type")) ? p.optInt("value") + "% de descuento" : Store.money(p.optDouble("value")) + " de descuento";
        String scope = "todos".equals(p.optString("serviceId")) ? "en todos los servicios" : "en " + serviceName(p.optString("serviceId")).toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("¡Nueva promoción en la barbería!\n");
        sb.append(p.optString("name")).append(": ").append(value).append(" ").append(scope).append(".");
        String from = p.optString("from", "");
        String to = p.optString("to", "");
        if (!from.isEmpty() || !to.isEmpty()) {
            sb.append("\nVigente ");
            if (!from.isEmpty()) sb.append("desde el ").append(Store.displayDate(from)).append(" ");
            if (!to.isEmpty()) sb.append("hasta el ").append(Store.displayDate(to));
            sb.append(".");
        }
        sb.append("\nReserva tu turno cuando quieras.");
        return sb.toString();
    }

    private void showPromoDialog(final JSONObject editing) {
        final String[] typeHolder = {editing != null ? editing.optString("type", "porcentaje") : "porcentaje"};
        final String[] audHolder = {editing != null ? editing.optString("audience", "todos") : "todos"};
        final List<JSONObject> services = activeServices();
        final String[] serviceIdHolder = {editing != null ? editing.optString("serviceId", "todos") : "todos"};
        // Nota: 'editing' es final para que las clases anónimas lo capturen en Java 7.

        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), 0);
        final EditText name = Ui.input(activity, "Nombre de la promoción", false);
        final EditText value = Ui.input(activity, "Valor (% o CUP)", true);
        final EditText from = Ui.input(activity, "Desde (aaaa-mm-dd, opcional)", false);
        final EditText to = Ui.input(activity, "Hasta (aaaa-mm-dd, opcional)", false);
        if (editing != null) {
            name.setText(editing.optString("name"));
            value.setText(String.valueOf(editing.optDouble("value")).replace(".0", ""));
            from.setText(editing.optString("from"));
            to.setText(editing.optString("to"));
        }
        final Button typeBtn = Ui.button(activity, "Tipo: " + typeName(typeHolder[0]), false);
        typeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                typeHolder[0] = "porcentaje".equals(typeHolder[0]) ? "monto" : "porcentaje";
                typeBtn.setText("Tipo: " + typeName(typeHolder[0]));
            }
        });
        final Button scopeBtn = Ui.button(activity, "Aplica a: " + scopeName(serviceIdHolder[0], services), false);
        scopeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // Recorre: todos, luego cada servicio activo.
                int idx = -1;
                for (int i = 0; i < services.size(); i++) {
                    if (services.get(i).optString("id").equals(serviceIdHolder[0])) idx = i;
                }
                if (idx + 1 < services.size()) serviceIdHolder[0] = services.get(idx + 1).optString("id");
                else serviceIdHolder[0] = "todos";
                scopeBtn.setText("Aplica a: " + scopeName(serviceIdHolder[0], services));
            }
        });
        final Button audBtn = Ui.button(activity, "Dirigida a: " + audienceName(audHolder[0]), false);
        audBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int idx = 0;
                for (int i = 0; i < AUDIENCES.length; i++) if (AUDIENCES[i].equals(audHolder[0])) idx = i;
                audHolder[0] = AUDIENCES[(idx + 1) % AUDIENCES.length];
                audBtn.setText("Dirigida a: " + audienceName(audHolder[0]));
            }
        });

        form.addView(Ui.label(activity, "Nombre"));
        form.addView(name);
        form.addView(Ui.label(activity, "Tipo y valor"));
        form.addView(typeBtn);
        form.addView(Ui.spacer(activity, 6));
        form.addView(value);
        form.addView(Ui.label(activity, "Servicio"));
        form.addView(scopeBtn);
        form.addView(Ui.label(activity, "Audiencia"));
        form.addView(audBtn);
        form.addView(Ui.label(activity, "Vigencia"));
        form.addView(from);
        form.addView(Ui.spacer(activity, 6));
        form.addView(to);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(editing == null ? "Nueva promoción" : "Editar promoción")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String n = name.getText().toString().trim();
                double val = parseOr(value.getText().toString(), 0);
                if (n.isEmpty()) { toast("Escribe el nombre de la promoción."); return; }
                if (val <= 0) { toast("Indica un valor mayor que cero."); return; }
                if ("porcentaje".equals(typeHolder[0]) && val > 100) { toast("El porcentaje no puede pasar de 100."); return; }
                String f = from.getText().toString().trim();
                String t = to.getText().toString().trim();
                if (!f.isEmpty() && !t.isEmpty() && t.compareTo(f) < 0) { toast("La fecha final debe ser posterior a la inicial."); return; }

                JSONObject p = editing;
                if (p == null) {
                    p = new JSONObject();
                    Store.put(p, "id", store.newId("p"));
                    Store.put(p, "active", true);
                    Store.add(store.arr("promos"), p);
                }
                Store.put(p, "name", n);
                Store.put(p, "type", typeHolder[0]);
                Store.put(p, "value", val);
                Store.put(p, "serviceId", serviceIdHolder[0]);
                Store.put(p, "audience", audHolder[0]);
                Store.put(p, "from", f);
                Store.put(p, "to", t);
                store.save();
                dialog.dismiss();
                refresh();
            }
        });
    }

    private void deletePromo(String id) {
        JSONArray promos = store.arr("promos");
        for (int i = 0; i < promos.length(); i++) {
            JSONObject p = promos.optJSONObject(i);
            if (p != null && id.equals(p.optString("id"))) { Store.removeAt(promos, i); break; }
        }
        store.save();
        refresh();
    }

    // ---------- Utilidades ----------

    private void shareText(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        activity.startActivity(Intent.createChooser(i, "Compartir"));
    }

    private LinearLayout.LayoutParams noop() { return null; }

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

    private List<JSONObject> activeServices() {
        List<JSONObject> out = new ArrayList<>();
        JSONArray s = store.arr("services");
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && o.optBoolean("active", true)) out.add(o);
        }
        return out;
    }

    private String serviceName(String id) {
        JSONObject s = store.serviceById(id);
        return s == null ? "servicio" : s.optString("name");
    }

    private String typeName(String t) { return "porcentaje".equals(t) ? "Descuento %" : "Descuento fijo"; }

    private String scopeName(String id, List<JSONObject> services) {
        if ("todos".equals(id)) return "Todos los servicios";
        for (JSONObject s : services) if (s.optString("id").equals(id)) return s.optString("name");
        return "Todos los servicios";
    }

    private String audienceName(String a) {
        for (int i = 0; i < AUDIENCES.length; i++) if (AUDIENCES[i].equals(a)) return AUDIENCE_NAMES[i];
        return AUDIENCE_NAMES[0];
    }

    private double parseOr(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }
}
