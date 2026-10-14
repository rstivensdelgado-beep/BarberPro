package com.barberia.app.screens;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.barberia.app.Calc;
import com.barberia.app.MainActivity;
import com.barberia.app.Screen;
import com.barberia.app.Store;
import com.barberia.app.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Inicio sencillo: saludo, resumen del día, galería y citas de hoy. Solo lectura. */
public class HomeScreen extends Screen {

    private LinearLayout content;

    public HomeScreen(MainActivity activity) {
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
        String today = Store.today();

        content.addView(Ui.title(activity, "Hola" + greetingName()));
        content.addView(Ui.muted(activity, dateLine(today)));
        content.addView(Ui.spacer(activity, 18));

        // Resumen del día en texto simple.
        List<JSONObject> list = todayAppts(today);
        double inToday = Calc.sum(Calc.movs(store, today, today), "ingreso");
        int pending = 0;
        JSONObject next = null;
        int nowMin = nowMinutes();
        for (JSONObject a : list) {
            if ("bloqueo".equals(a.optString("kind"))) continue;
            String st = a.optString("status");
            if ("completada".equals(st) || "cancelada".equals(st)) continue;
            pending++;
            if (next == null && a.optInt("start") >= nowMin) next = a;
        }
        content.addView(Ui.text(activity, "Cobrado hoy: " + Store.money(inToday), 16, Ui.INK, true));
        content.addView(Ui.spacer(activity, 4));
        content.addView(Ui.muted(activity, pendingLine(pending)));
        if (next != null) {
            content.addView(Ui.muted(activity, "Próxima: " + Store.hhmm(next.optInt("start"))
                    + " · " + next.optString("client", "Cliente")));
        }

        // Galería de trabajos: enlace simple.
        content.addView(Ui.spacer(activity, 20));
        TextView galLink = Ui.link(activity, "Galería de trabajos  →", Ui.ACCENT);
        galLink.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { activity.openGallery(); }
        });
        content.addView(galLink);

        // Citas de hoy, en lista sin tarjetas.
        content.addView(Ui.spacer(activity, 24));
        content.addView(Ui.label(activity, "Citas de hoy"));
        if (list.isEmpty()) content.addView(Ui.muted(activity, "No hay citas ni bloqueos para hoy."));
        for (int i = 0; i < list.size() && i < 8; i++) {
            JSONObject a = list.get(i);
            boolean block = "bloqueo".equals(a.optString("kind"));
            String who = block ? a.optString("reason", "Bloqueo") : a.optString("client", "Cliente");
            String sub = block ? "Horario bloqueado" : serviceName(a.optString("serviceId"));
            content.addView(Ui.text(activity, Store.hhmm(a.optInt("start")) + "   " + who, 15, Ui.INK, true));
            content.addView(Ui.muted(activity, sub));
            content.addView(Ui.spacer(activity, 10));
        }
    }

    private String pendingLine(int pending) {
        if (pending == 0) return "No quedan citas pendientes";
        if (pending == 1) return "1 cita pendiente";
        return pending + " citas pendientes";
    }

    private int nowMinutes() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private String greetingName() {
        String nick = store.profile().optString("nickname", "");
        String name = store.profile().optString("name", "");
        String who = !nick.isEmpty() ? nick : name.split(" ")[0];
        return who.isEmpty() ? "" : ", " + who;
    }

    private String dateLine(String today) {
        String[] days = {"domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"};
        String[] months = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        Calendar c = Store.parseDate(today);
        return days[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH)
                + " de " + months[c.get(Calendar.MONTH)];
    }

    private List<JSONObject> todayAppts(String date) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = store.arr("appts");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && date.equals(o.optString("date")) && !"cancelada".equals(o.optString("status"))) out.add(o);
        }
        java.util.Collections.sort(out, new java.util.Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject x, JSONObject y) {
                return x.optInt("start") - y.optInt("start");
            }
        });
        return out;
    }

    private String serviceName(String id) {
        JSONObject s = store.serviceById(id);
        return s == null ? "Servicio" : s.optString("name");
    }
}
