package com.barberia.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * Almacenamiento único de la app: un documento JSON guardado en SharedPreferences.
 * Todas las pantallas leen y escriben aquí, así que los datos quedan unidos.
 */
public final class Store {

    private static final String PREFS = "barberia";
    private static final String KEY = "db";
    private static Store instance;

    private final SharedPreferences prefs;
    private JSONObject root;
    private final Random random = new Random();

    private static final String[] DAY_KEYS = {"dom", "lun", "mar", "mie", "jue", "vie", "sab"};

    private Store(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            root = new JSONObject(prefs.getString(KEY, "{}"));
        } catch (JSONException e) {
            root = new JSONObject();
        }
        if (!root.has("services")) seed();
    }

    public static synchronized Store get(Context ctx) {
        if (instance == null) instance = new Store(ctx);
        return instance;
    }

    public void save() {
        prefs.edit().putString(KEY, root.toString()).apply();
    }

    // ---------- Acceso a colecciones ----------

    public JSONArray arr(String name) {
        JSONArray a = root.optJSONArray(name);
        if (a == null) {
            a = new JSONArray();
            try { root.put(name, a); } catch (JSONException ignored) { }
        }
        return a;
    }

    public JSONObject obj(String name) {
        JSONObject o = root.optJSONObject(name);
        if (o == null) {
            o = new JSONObject();
            try { root.put(name, o); } catch (JSONException ignored) { }
        }
        return o;
    }

    public JSONObject profile() { return obj("profile"); }
    public JSONObject loyalty() { return obj("loyalty"); }
    public JSONObject settings() { return obj("settings"); }
    public JSONObject closes() { return obj("closes"); }
    public JSONObject sent() { return obj("sent"); }

    // ---------- Utilidades de datos ----------

    public String newId(String prefix) {
        return prefix + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(random.nextInt(46656), 36);
    }

    public static String today() {
        return dateStr(Calendar.getInstance());
    }

    public static String dateStr(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static Calendar parseDate(String iso) {
        Calendar c = Calendar.getInstance();
        String[] p = iso.split("-");
        c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    public static String addDays(String iso, int days) {
        Calendar c = parseDate(iso);
        c.add(Calendar.DAY_OF_MONTH, days);
        return dateStr(c);
    }

    public static long daysBetween(String from, String to) {
        long a = parseDate(from).getTimeInMillis();
        long b = parseDate(to).getTimeInMillis();
        return Math.round((b - a) / 86400000d);
    }

    public static String displayDate(String iso) {
        String[] p = iso.split("-");
        return p[2] + "/" + p[1] + "/" + p[0];
    }

    public static String hhmm(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    public static String dayKeyOf(String iso) {
        return DAY_KEYS[parseDate(iso).get(Calendar.DAY_OF_WEEK) - 1];
    }

    public static String money(double n) {
        return "CUP " + String.format(Locale.US, "%,.0f", (double) Math.round(n));
    }

    public static String clientKey(String name, String phone) {
        String d = phone == null ? "" : phone.replaceAll("\\D", "");
        if (d.length() >= 6) return "tel:" + d;
        return "nom:" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
    }

    // ---------- Consultas frecuentes ----------

    public JSONObject serviceById(String id) {
        JSONArray s = arr("services");
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && id != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    /** Horario del día (ej. "lun"), o null si el día no se trabaja. */
    public int[] workWindow(String iso) {
        JSONObject cfg = profile().optJSONObject("schedule");
        if (cfg == null) return null;
        JSONObject d = cfg.optJSONObject(dayKeyOf(iso));
        if (d == null || !d.optBoolean("on")) return null;
        int start = toMin(d.optString("start", "09:00"));
        int end = toMin(d.optString("end", "18:00"));
        return end > start ? new int[]{start, end} : null;
    }

    public static int toMin(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** Escribe un campo sin obligar a manejar JSONException en cada pantalla. */
    public static void put(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (JSONException ignored) { }
    }

    /** Agrega un elemento a una colección. */
    public static void add(JSONArray a, JSONObject o) {
        a.put(o);
    }

    /** Quita el elemento de índice i de una colección. */
    public static void removeAt(JSONArray a, int i) {
        a.remove(i);
    }

    // ---------- Datos iniciales ----------

    private void seed() {
        try {
            JSONArray services = arr("services");
            addService(services, "Corte clásico", 300, 30);
            addService(services, "Corte + barba", 450, 45);
            addService(services, "Afeitado tradicional con navaja", 250, 30);
            addService(services, "Arreglo de barba", 200, 20);
            addService(services, "Corte infantil (menores de 12)", 200, 25);
            addService(services, "Tratamiento capilar", 400, 40);

            JSONObject p = profile();
            p.put("name", "");
            p.put("nickname", "");
            p.put("photo", "");
            p.put("scheme", "mixto");
            p.put("salary", 0);
            p.put("commission", 40);
            JSONObject schedule = new JSONObject();
            for (String k : DAY_KEYS) {
                JSONObject d = new JSONObject();
                d.put("on", !k.equals("dom"));
                d.put("start", "09:00");
                d.put("end", "18:00");
                schedule.put(k, d);
            }
            p.put("schedule", schedule);

            JSONObject l = loyalty();
            l.put("threshold", 10);
            l.put("reward", "Corte clásico gratis");

            JSONObject s = settings();
            s.put("dark", true);
            s.put("lead", 1440);
            s.put("shopName", "BarberPro");

            save();
        } catch (JSONException ignored) { }
    }

    private void addService(JSONArray list, String name, int price, int minutes) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", newId("s"));
        o.put("name", name);
        o.put("price", price);
        o.put("minutes", minutes);
        o.put("active", true);
        list.put(o);
    }

    /** Borra todo y vuelve a los datos de ejemplo. */
    public void reset() {
        root = new JSONObject();
        seed();
    }

    public String exportJson() {
        return root.toString();
    }

    public boolean importJson(String text) {
        try {
            JSONObject o = new JSONObject(text);
            if (!o.has("services")) return false;
            root = o;
            save();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }
}
