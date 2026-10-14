package com.barberia.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Copia las citas de BarberPro al calendario del móvil (una sola vía: app -> calendario).
 * Cada cita guarda el id de su evento en "calId", así se actualiza o se borra sin duplicarse.
 */
public final class CalendarSync {

    public static final int REQ_PERMS = 403;
    private static final String[] PERMS = {
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    };

    private CalendarSync() { }

    /** true si la app ya tiene permiso de calendario (en Android 5-5.1 se concede al instalar). */
    public static boolean hasPermission(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Muestra la ventana de permisos del sistema. El resultado llega a MainActivity.onRequestPermissionsResult. */
    public static void request(Activity a) {
        if (Build.VERSION.SDK_INT >= 23) a.requestPermissions(PERMS, REQ_PERMS);
    }

    public static boolean enabled(Store store) {
        return store.settings().optBoolean("calSync", false);
    }

    /** Llamar al crear o cambiar una cita. Solo actúa si el calendario está activado en Ajustes. */
    public static void onChange(Context c, Store store, JSONObject appt) {
        if (!enabled(store)) return;
        boolean gone = "cancelada".equals(appt.optString("status")) || "bloqueo".equals(appt.optString("kind"));
        if (gone) {
            remove(c, appt);
        } else {
            upsert(c, appt, serviceName(store, appt));
        }
        store.save();
    }

    /** Envía todas las citas activas que existen. Devuelve cuántas quedaron sincronizadas. */
    public static int syncAll(Context c, Store store) {
        JSONArray arr = store.arr("appts");
        int ok = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a == null || !"cita".equals(a.optString("kind", "cita"))) continue;
            if ("cancelada".equals(a.optString("status"))) {
                remove(c, a);
                continue;
            }
            if (upsert(c, a, serviceName(store, a))) ok++;
        }
        store.save();
        return ok;
    }

    /** Crea o actualiza el evento de una cita. Devuelve true si quedó en el calendario. */
    public static boolean upsert(Context c, JSONObject appt, String serviceName) {
        if (!hasPermission(c)) return false;
        try {
            long calId = writableCalendarId(c);
            if (calId < 0) return false;

            String[] d = appt.optString("date").split("-");
            int start = appt.optInt("start");
            int minutes = appt.optInt("minutes", 30);
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]),
                    start / 60, start % 60, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startMs = cal.getTimeInMillis();
            long endMs = startMs + minutes * 60000L;

            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, calId);
            v.put(CalendarContract.Events.TITLE, "Cita: " + appt.optString("client", "Cliente") + " · " + serviceName);
            String phone = appt.optString("phone");
            v.put(CalendarContract.Events.DESCRIPTION, phone.isEmpty() ? "BarberPro" : "Tel: " + phone + " · BarberPro");
            v.put(CalendarContract.Events.DTSTART, startMs);
            v.put(CalendarContract.Events.DTEND, endMs);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

            ContentResolver cr = c.getContentResolver();
            long existing = appt.optLong("calId", -1);
            if (existing > 0) {
                Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing);
                if (cr.update(u, v, null, null) > 0) return true;
            }
            Uri created = cr.insert(CalendarContract.Events.CONTENT_URI, v);
            if (created == null) return false;
            appt.put("calId", ContentUris.parseId(created));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Borra el evento de una cita del calendario. */
    public static void remove(Context c, JSONObject appt) {
        long id = appt.optLong("calId", -1);
        if (id <= 0) return;
        if (hasPermission(c)) {
            try {
                c.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null);
            } catch (Exception e) {
                // Si el evento ya no existe, no pasa nada.
            }
        }
        appt.remove("calId");
    }

    /** Primer calendario del móvil donde la app puede escribir (cuenta de Google, Samsung, etc.). */
    private static long writableCalendarId(Context c) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                    new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL},
                    null, null, null);
            while (cur != null && cur.moveToNext()) {
                if (cur.getInt(1) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) return cur.getLong(0);
            }
        } catch (Exception e) {
            return -1;
        } finally {
            if (cur != null) cur.close();
        }
        return -1;
    }

    private static String serviceName(Store store, JSONObject appt) {
        JSONObject s = store.serviceById(appt.optString("serviceId"));
        return s == null ? "Servicio" : s.optString("name", "Servicio");
    }
}
