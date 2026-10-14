package com.barberia.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cálculos compartidos por el panel, la caja, las fichas y la fidelidad. */
public final class Calc {

    private Calc() { }

    /** Movimientos de caja entre dos fechas (inclusive, formato yyyy-MM-dd). */
    public static List<JSONObject> movs(Store s, String from, String to) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = s.arr("movs");
        for (int i = 0; i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            if (m == null) continue;
            String d = m.optString("date");
            if (d.compareTo(from) >= 0 && d.compareTo(to) <= 0) out.add(m);
        }
        return out;
    }

    public static double sum(List<JSONObject> list, String type) {
        double t = 0;
        for (JSONObject m : list) {
            if (type.equals(m.optString("type"))) t += m.optDouble("amount", 0);
        }
        return t;
    }

    public static double sumCash(List<JSONObject> list, String type) {
        double t = 0;
        for (JSONObject m : list) {
            if (type.equals(m.optString("type")) && "efectivo".equals(m.optString("method"))) {
                t += m.optDouble("amount", 0);
            }
        }
        return t;
    }

    /** Citas completadas del día que aún no tienen cobro registrado. */
    public static List<JSONObject> pendingCharges(Store s, String date) {
        Map<String, Boolean> cobradas = new HashMap<>();
        JSONArray movs = s.arr("movs");
        for (int i = 0; i < movs.length(); i++) {
            JSONObject m = movs.optJSONObject(i);
            if (m != null && m.has("apptId")) cobradas.put(m.optString("apptId"), true);
        }
        List<JSONObject> out = new ArrayList<>();
        JSONArray citas = s.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject a = citas.optJSONObject(i);
            if (a == null) continue;
            if (!date.equals(a.optString("date"))) continue;
            if (!"cita".equals(a.optString("kind"))) continue;
            if (!"completada".equals(a.optString("status"))) continue;
            if (cobradas.containsKey(a.optString("id"))) continue;
            out.add(a);
        }
        return out;
    }

    /** Minutos ocupados y minutos de trabajo de un día. */
    public static int[] occupancy(Store s, String date) {
        int[] work = s.workWindow(date);
        int workMin = work == null ? 0 : work[1] - work[0];
        int busy = 0;
        JSONArray citas = s.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject a = citas.optJSONObject(i);
            if (a == null || !date.equals(a.optString("date"))) continue;
            if ("cancelada".equals(a.optString("status"))) continue;
            busy += a.optInt("minutes", 0);
        }
        return new int[]{busy, workMin};
    }

    /** Clave de cliente -> {nombre, última visita} con las visitas completadas. */
    public static Map<String, String[]> lastVisits(Store s) {
        Map<String, String[]> map = new HashMap<>();
        JSONArray citas = s.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject a = citas.optJSONObject(i);
            if (a == null || !"cita".equals(a.optString("kind"))) continue;
            if (!"completada".equals(a.optString("status"))) continue;
            String key = Store.clientKey(a.optString("client"), a.optString("phone"));
            String[] cur = map.get(key);
            String date = a.optString("date");
            if (cur == null || date.compareTo(cur[1]) > 0) {
                map.put(key, new String[]{a.optString("client", "Cliente"), date});
            }
        }
        // Si existe ficha, su nombre manda sobre el de la agenda.
        JSONArray fichas = s.arr("clients");
        for (int i = 0; i < fichas.length(); i++) {
            JSONObject c = fichas.optJSONObject(i);
            if (c == null) continue;
            String key = Store.clientKey(c.optString("name"), c.optString("phone"));
            String[] cur = map.get(key);
            if (cur != null) cur[0] = c.optString("name");
        }
        return map;
    }

    /**
     * Próxima cita sugerida según el ritmo del cliente: {días entre visitas, fecha ISO}.
     * Devuelve null si aún no hay al menos dos visitas completadas.
     */
    public static String[] suggestNext(Store s, String key) {
        String first = null;
        String last = null;
        int n = 0;
        JSONArray citas = s.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject a = citas.optJSONObject(i);
            if (a == null || !"cita".equals(a.optString("kind"))) continue;
            if (!"completada".equals(a.optString("status"))) continue;
            if (!key.equals(Store.clientKey(a.optString("client"), a.optString("phone")))) continue;
            String d = a.optString("date");
            n++;
            if (first == null || d.compareTo(first) < 0) first = d;
            if (last == null || d.compareTo(last) > 0) last = d;
        }
        if (n < 2) return null;
        int interval = (int) Math.round(Store.daysBetween(first, last) / (double) (n - 1));
        if (interval < 1) return null;
        return new String[]{String.valueOf(interval), Store.addDays(last, interval)};
    }

    /** Visitas completadas de un cliente (por clave). */
    public static int visitsOf(Store s, String key) {
        int n = 0;
        JSONArray citas = s.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject a = citas.optJSONObject(i);
            if (a == null || !"cita".equals(a.optString("kind"))) continue;
            if (!"completada".equals(a.optString("status"))) continue;
            if (key.equals(Store.clientKey(a.optString("client"), a.optString("phone")))) n++;
        }
        return n;
    }

    /** Sellos disponibles: visitas menos los sellos ya canjeados. */
    public static int stampsAvailable(Store s, String key) {
        int used = 0;
        int threshold = s.loyalty().optInt("threshold", 10);
        JSONArray r = s.arr("redemptions");
        for (int i = 0; i < r.length(); i++) {
            JSONObject o = r.optJSONObject(i);
            if (o != null && key.equals(o.optString("key"))) used += threshold;
        }
        return visitsOf(s, key) - used;
    }

    public static List<JSONObject> lowStock(Store s) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray inv = s.arr("inventory");
        for (int i = 0; i < inv.length(); i++) {
            JSONObject it = inv.optJSONObject(i);
            if (it != null && it.optInt("stock") <= it.optInt("min")) out.add(it);
        }
        return out;
    }
}
