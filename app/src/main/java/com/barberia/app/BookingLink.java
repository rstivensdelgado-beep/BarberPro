package com.barberia.app;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Enlace de reservas. Lleva en el fragmento (#...) el horario de los próximos 14 días,
 * los turnos ya ocupados y los servicios. La página de reservas lo lee sin necesidad de servidor.
 */
public final class BookingLink {

    private static final int DAYS = 14;

    private BookingLink() { }

    /** Enlace completo listo para copiar, o "" si aún no hay dirección de la página. */
    public static String build(Store s) {
        String base = s.settings().optString("bookUrl", "").trim();
        if (base.isEmpty()) return "";
        try {
            byte[] data = payload(s).getBytes("UTF-8");
            String frag = Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return base + "#" + frag;
        } catch (Exception e) {
            return "";
        }
    }

    static String payload(Store s) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("shop", s.settings().optString("shopName", "BarberPro"));
        o.put("tel", s.settings().optString("bookPhone", ""));

        JSONArray services = new JSONArray();
        JSONArray list = s.arr("services");
        for (int i = 0; i < list.length(); i++) {
            JSONObject sv = list.optJSONObject(i);
            if (sv == null || !sv.optBoolean("active", true)) continue;
            JSONObject x = new JSONObject();
            x.put("n", sv.optString("name"));
            x.put("p", sv.optInt("price"));
            x.put("m", sv.optInt("minutes"));
            services.put(x);
        }
        o.put("services", services);

        String today = Store.today();
        String end = Store.addDays(today, DAYS);
        JSONArray days = new JSONArray();
        for (int d = 0; d < DAYS; d++) {
            String iso = Store.addDays(today, d);
            int[] w = s.workWindow(iso);
            if (w == null) continue;
            JSONObject x = new JSONObject();
            x.put("d", iso);
            x.put("s", w[0]);
            x.put("e", w[1]);
            days.put(x);
        }
        o.put("days", days);

        JSONArray busy = new JSONArray();
        JSONArray appts = s.arr("appts");
        for (int i = 0; i < appts.length(); i++) {
            JSONObject a = appts.optJSONObject(i);
            if (a == null || "cancelada".equals(a.optString("status"))) continue;
            String date = a.optString("date");
            if (date.compareTo(today) < 0 || date.compareTo(end) >= 0) continue;
            int start = a.optInt("start");
            JSONObject x = new JSONObject();
            x.put("d", date);
            x.put("s", start);
            x.put("e", start + a.optInt("minutes"));
            busy.put(x);
        }
        o.put("busy", busy);
        return o.toString();
    }
}
