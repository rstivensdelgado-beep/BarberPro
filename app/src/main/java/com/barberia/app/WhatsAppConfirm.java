package com.barberia.app;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Lee las notificaciones de WhatsApp (solo en este teléfono, nada sale de él).
 * Si un cliente con cita pendiente responde "sí", "ok" o "vale", la cita pasa a confirmada.
 * Si hay más de una cita posible para ese cliente, no hace nada: lo confirmas tú en la Agenda.
 */
public class WhatsAppConfirm extends NotificationListenerService {

    private static final String[] APPS = {"com.whatsapp", "com.whatsapp.w4b"};
    private static final String[] YES = {
            "si", "sí", "sii", "confirmo", "confirmado", "ok", "okey", "vale", "listo", "dale", "perfecto", "de acuerdo", "👍"
    };
    /** Un "sí" corto es confirmación; un mensaje largo puede decir "sí, pero no puedo". */
    private static final int MAX_LEN = 25;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (!isWhatsApp(sbn.getPackageName())) return;
            Bundle extras = sbn.getNotification().extras;
            if (extras == null) return;
            if (extras.getBoolean("android.isGroupConversation", false)) return;
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (title == null || text == null) return;
            if (!isYes(text.toString())) return;
            confirmFor(title.toString());
        } catch (Exception ignored) {
            // Una notificación rara no debe detener el servicio.
        }
    }

    private static boolean isWhatsApp(String pkg) {
        for (String a : APPS) if (a.equals(pkg)) return true;
        return false;
    }

    private static boolean isYes(String raw) {
        String s = norm(raw).replaceAll("[.!¡?¿,]", "").trim();
        if (s.isEmpty() || s.length() > MAX_LEN) return false;
        for (String y : YES) if (y.equals(s)) return true;
        return false;
    }

    private void confirmFor(String sender) {
        Store store = Store.get(getApplicationContext());
        String name = norm(sender);
        String digits = sender.replaceAll("\\D", "");
        String today = Store.today();
        JSONArray appts = store.arr("appts");
        JSONObject reminded = store.sent();

        JSONObject match = null;
        int count = 0;
        for (int i = 0; i < appts.length(); i++) {
            JSONObject a = appts.optJSONObject(i);
            if (a == null) continue;
            if (!"cita".equals(a.optString("kind", "cita"))) continue;
            if (!"pendiente".equals(a.optString("status"))) continue;
            if (a.optString("date").compareTo(today) < 0) continue;
            // Solo cuenta si antes se envió el recordatorio desde la app.
            if (!reminded.has(a.optString("id"))) continue;
            if (!sameClient(a, name, digits)) continue;
            match = a;
            count++;
        }
        if (count != 1 || match == null) return;

        Store.put(match, "status", "confirmada");
        CalendarSync.onChange(getApplicationContext(), store, match);
        store.save();
        Toast.makeText(getApplicationContext(),
                "Confirmada por WhatsApp: " + match.optString("client") + " " + Store.hhmm(match.optInt("start")),
                Toast.LENGTH_LONG).show();
    }

    /** Coincide por nombre (exacto o uno contenido en otro) o por los últimos 8 dígitos del teléfono. */
    private static boolean sameClient(JSONObject a, String name, String digits) {
        String client = norm(a.optString("client"));
        if (!name.isEmpty() && !client.isEmpty()) {
            if (client.equals(name)) return true;
            if (name.length() >= 4 && client.contains(name)) return true;
            if (client.length() >= 4 && name.contains(client)) return true;
        }
        String phone = a.optString("phone").replaceAll("\\D", "");
        String p8 = last(phone, 8);
        String d8 = last(digits, 8);
        return p8.length() >= 6 && p8.equals(d8);
    }

    private static String last(String s, int n) {
        return s.length() > n ? s.substring(s.length() - n) : s;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
