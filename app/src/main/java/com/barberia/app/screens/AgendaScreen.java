package com.barberia.app.screens;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;
import com.barberia.app.BookingLink;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.AdapterView;

import com.barberia.app.CalendarSync;
import com.barberia.app.ContactsHelper;
import com.barberia.app.MainActivity;
import com.barberia.app.Screen;
import com.barberia.app.Store;
import com.barberia.app.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Agenda del día, nuevas citas o bloqueos y recordatorios de las próximas horas. */
public class AgendaScreen extends Screen {

    private LinearLayout content;
    private String day;
    /** Campos del diálogo de cita que se llenan al elegir un contacto. */
    private EditText pickClient, pickPhone;
    /** Sección de recordatorios plegada (cerrada por defecto). */
    private boolean remindersOpen = false;

    public AgendaScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View build() {
        day = Store.today();
        content = Ui.column(activity);
        return Ui.page(activity, content);
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        content.addView(Ui.title(activity, "Agenda"));
        content.addView(Ui.muted(activity, "Citas, bloqueos y recordatorios."));

        // Navegación por día
        // Barra de fecha dorada, como en el croquis.
        LinearLayout nav = Ui.row(activity);
        nav.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 8), Ui.dp(activity, 6), Ui.dp(activity, 8));
        nav.setBackground(Ui.solid(activity, Ui.ACCENT, 12));
        nav.addView(Ui.link(activity, "‹", Ui.ON_INK), Ui.dp(activity, 40), ViewGroup.LayoutParams.WRAP_CONTENT);
        nav.getChildAt(0).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { day = Store.addDays(day, -1); refresh(); }
        });
        LinearLayout middle = Ui.column(activity);
        middle.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        middle.addView(Ui.text(activity, dayTitle(day), 17, Ui.ON_INK, true));
        middle.addView(Ui.link(activity, day.equals(Store.today()) ? "Hoy" : "Ir a hoy", Ui.ON_INK));
        middle.getChildAt(1).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { day = Store.today(); refresh(); }
        });
        nav.addView(middle, Ui.weight(1));
        addNext(nav);
        content.addView(nav, Ui.matchWrap());

        Button add = Ui.button(activity, "Nueva cita o bloqueo", true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showNewDialog(); }
        });
        LinearLayout addRow = Ui.column(activity);
        addRow.setPadding(0, Ui.dp(activity, 14), 0, 0);
        addRow.addView(add);
        content.addView(addRow, Ui.matchWrap());
        Button bookLink = Ui.button(activity, "Enlace de reservas", false);
        bookLink.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showBookingDialog(); }
        });
        content.addView(bookLink, Ui.matchWrap());

        // Citas del día
        content.addView(Ui.label(activity, "Citas del día"));
        LinearLayout card = Ui.column(activity);
        List<JSONObject> list = apptsOn(day);
        if (list.isEmpty()) {
            card.addView(Ui.emptyState(activity, "Día libre", "No hay citas ni bloqueos para este día."));
            card.addView(action("Nueva cita o bloqueo", Ui.ACCENT, new Runnable() {
                public void run() { showNewDialog(); }
            }));
        }
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) card.addView(Ui.spacer(activity, 10));
            card.addView(apptRow(list.get(i)));
        }
        content.addView(card, Ui.matchWrap());

        // Recordatorios: citas dentro de la antelación elegida
        List<JSONObject> due = remindersDue();
        LinearLayout remHead = Ui.row(activity);
        remHead.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 6));
        remHead.addView(Ui.label(activity, "Recordatorios próximos" + (due.isEmpty() ? "" : " · " + due.size())), Ui.weight(1));
        remHead.addView(Ui.text(activity, remindersOpen ? "−" : "+", 22, Ui.ACCENT, true));
        remHead.setClickable(true);
        remHead.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                remindersOpen = !remindersOpen;
                refresh();
            }
        });
        content.addView(remHead, Ui.matchWrap());
        if (remindersOpen) {
            LinearLayout rem = Ui.card(activity);
            if (due.isEmpty()) rem.addView(Ui.muted(activity, "No hay citas dentro de la antelación configurada."));
            for (int i = 0; i < due.size(); i++) {
                if (i > 0) rem.addView(divider());
                rem.addView(reminderRow(due.get(i)));
            }
            rem.addView(Ui.spacer(activity, 10));
            rem.addView(Ui.muted(activity, "Se envía desde WhatsApp si está instalado. Marca el recordatorio cuando lo mandes."));
            content.addView(rem, Ui.matchWrap());
        }
    }

    private void addNext(LinearLayout nav) {
        View next = Ui.link(activity, "›", Ui.ON_INK);
        nav.addView(next, Ui.dp(activity, 40), ViewGroup.LayoutParams.WRAP_CONTENT);
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { day = Store.addDays(day, 1); refresh(); }
        });
    }

    private View divider() {
        View d = Ui.divider(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
        lp.topMargin = Ui.dp(activity, 12);
        lp.bottomMargin = Ui.dp(activity, 12);
        d.setLayoutParams(lp);
        return d;
    }

    private LinearLayout apptRow(final JSONObject a) {
        boolean block = "bloqueo".equals(a.optString("kind"));
        String status = a.optString("status");
        // Citas en tarjeta verde azulada con texto claro; bloqueos en tarjeta neutra.
        int fg = block ? Ui.INK : Ui.ON_TEAL;
        int subColor = block ? Ui.MUTED : Ui.ON_TEAL;

        LinearLayout box = Ui.column(activity);
        box.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 10));
        box.setBackground(Ui.solid(activity, block ? Ui.SURFACE : Ui.TEAL, 14));
        LinearLayout top = Ui.row(activity);

        // Punto de color según el estado (pendiente, confirmada, completada, cancelada).
        if (!block) {
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(activity, 10), Ui.dp(activity, 10));
            dlp.rightMargin = Ui.dp(activity, 10);
            dlp.topMargin = Ui.dp(activity, 5);
            top.addView(Ui.dot(activity, Ui.statusColor(status), 10), dlp);
        }

        LinearLayout info = Ui.column(activity);
        info.addView(Ui.text(activity, block ? a.optString("reason", "Horario bloqueado") : a.optString("client", "Cliente"), 15, fg, true));
        String sub = block ? a.optInt("minutes") + " min" : serviceName(a.optString("serviceId")) + " · " + a.optInt("minutes") + " min";
        if (!block && !a.optString("phone").isEmpty()) sub += " · " + a.optString("phone");
        info.addView(Ui.text(activity, sub, 13, subColor, false));
        top.addView(info, Ui.weight(1));

        int start = a.optInt("start");
        top.addView(Ui.text(activity, Store.hhmm(start) + " – " + Store.hhmm(start + a.optInt("minutes")), 14, fg, false));
        box.addView(top);

        LinearLayout actions = Ui.row(activity);
        actions.setPadding(0, Ui.dp(activity, 6), 0, 0);
        if (!block && "pendiente".equals(status)) {
            actions.addView(action("Confirmar", fg, new Runnable() { public void run() { setStatus(a, "confirmada"); } }));
        }
        if (!block && !"completada".equals(status) && !"cancelada".equals(status)) {
            actions.addView(action("Completar", fg, new Runnable() { public void run() { setStatus(a, "completada"); } }));
        }
        if (!block && !a.optString("phone").isEmpty()) {
            actions.addView(action("WhatsApp", fg, new Runnable() { public void run() { sendReminder(a); } }));
        }
        if (!"cancelada".equals(status) && !"completada".equals(status)) {
            actions.addView(action(block ? "Quitar bloqueo" : "Cancelar", block ? Ui.DANGER : Ui.ON_TEAL, new Runnable() { public void run() { cancelAppt(a); } }));
        }
        if (!block) {
            String st = statusLabel(status);
            actions.addView(Ui.text(activity, st, 12, subColor, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        box.addView(actions);
        // Deslizar sobre la tarjeta: a la derecha confirma o completa; a la izquierda cancela.
        box.setOnTouchListener(swipe(a, block));
        return box;
    }

    /** Gesto de deslizar sobre la tarjeta. Los botones de texto siguen funcionando igual. */
    private View.OnTouchListener swipe(final JSONObject a, final boolean block) {
        return new View.OnTouchListener() {
            private float x0;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getX();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        v.setTranslationX((e.getX() - x0) * 0.6f);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float total = e.getX() - x0;
                        float limit = Ui.dp(activity, 90);
                        v.animate().translationX(0).setDuration(150).start();
                        if (total > limit) swipeRight(a, block);
                        else if (total < -limit) cancelAppt(a);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().translationX(0).setDuration(150).start();
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private void swipeRight(JSONObject a, boolean block) {
        if (block) return;
        String st = a.optString("status");
        if ("pendiente".equals(st)) setStatus(a, "confirmada");
        else if (!"completada".equals(st) && !"cancelada".equals(st)) setStatus(a, "completada");
    }

    /** Cancela la cita (o quita el bloqueo) después de confirmar, con opción de deshacer. */
    private void cancelAppt(final JSONObject a) {
        final boolean block = "bloqueo".equals(a.optString("kind"));
        final String prev = a.optString("status");
        new AlertDialog.Builder(activity)
                .setTitle(block ? "¿Quitar este bloqueo?" : "¿Cancelar esta cita?")
                .setMessage(block ? "El horario quedará libre." : "La cita quedará marcada como cancelada.")
                .setNegativeButton("No", null)
                .setPositiveButton(block ? "Quitar" : "Sí, cancelar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        setStatus(a, "cancelada");
                        activity.showUndo(block ? "Bloqueo quitado" : "Cita cancelada", new Runnable() {
                            @Override public void run() { setStatus(a, prev); }
                        });
                    }
                })
                .show();
    }

    private View action(String label, int color, final Runnable r) {
        View l = Ui.link(activity, label, color);
        l.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        return l;
    }

    private LinearLayout reminderRow(final JSONObject a) {
        LinearLayout box = Ui.column(activity);
        LinearLayout top = Ui.row(activity);
        boolean sent = store.sent().has(a.optString("id"));
        LinearLayout info = Ui.column(activity);
        info.addView(Ui.text(activity, a.optString("client", "Cliente"), 15, sent ? Ui.MUTED : Ui.INK, true));
        String phone = a.optString("phone");
        info.addView(Ui.muted(activity, Store.displayDate(a.optString("date")) + " · " + Store.hhmm(a.optInt("start"))
                + (phone.isEmpty() ? " · sin teléfono" : " · " + phone)));
        top.addView(info, Ui.weight(1));
        if (sent) top.addView(Ui.muted(activity, "Enviado"));
        box.addView(top);

        if (!phone.isEmpty()) {
            LinearLayout actions = Ui.row(activity);
            actions.setPadding(0, Ui.dp(activity, 6), 0, 0);
            actions.addView(action("Enviar recordatorio", Ui.INK, new Runnable() { public void run() { sendReminder(a); } }));
            if (!sent) {
                actions.addView(action("Marcar enviado", Ui.MUTED, new Runnable() {
                    public void run() {
                        Store.put(store.sent(), a.optString("id"), Store.today());
                        store.save();
                        refresh();
                    }
                }));
            }
            box.addView(actions);
        }
        return box;
    }

    /** Número listo para WhatsApp: solo dígitos y, si son 8, se agrega el código de Cuba (53). */
    private String waNumber(String phone) {
        String d = phone.replaceAll("\\D", "");
        if (d.startsWith("0")) d = d.substring(1);
        if (d.length() == 8) d = "53" + d;
        return d;
    }

    /**
     * Abre el chat directo con el cliente en WhatsApp, sin buscarlo en contactos.
     * El mensaje es un saludo cortés con solo el día y la hora de la cita.
     */
    private void sendReminder(JSONObject a) {
        String number = waNumber(a.optString("phone"));
        if (number.length() < 8) {
            toast("Esta cita no tiene un teléfono válido.");
            return;
        }
        String first = a.optString("client", "").trim().split(" ")[0];
        String text = "Hola" + (first.isEmpty() ? "" : " " + first) + ", buenas. Le recordamos su cita el "
                + Store.displayDate(a.optString("date")) + " a las " + Store.hhmm(a.optInt("start"))
                + ". ¡Hasta pronto!";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse("https://wa.me/" + number + "?text=" + Uri.encode(text)));
        try {
            i.setPackage("com.whatsapp");
            activity.startActivity(i);
        } catch (ActivityNotFoundException e) {
            // Sin WhatsApp instalado, el enlace se abre en el navegador.
            i.setPackage(null);
            activity.startActivity(i);
        }
        // Al enviar el recordatorio, la cita queda marcada: solo entonces se acepta la confirmación por WhatsApp.
        Store.put(store.sent(), a.optString("id"), Store.today());
        store.save();
        refresh();
    }

    /** Llamado por MainActivity al elegir un contacto: llena nombre y teléfono del diálogo abierto. */
    public void onContactPicked(ContactsHelper.Contact c) {
        if (pickClient == null || pickPhone == null) return;
        pickClient.setText(c.name);
        pickPhone.setText(c.phone);
    }

    private void setStatus(JSONObject a, String status) {
        Store.put(a, "status", status);
        CalendarSync.onChange(activity, store, a);
        store.save();
        refresh();
    }

    // ---------- Nueva cita o bloqueo ----------

    @Override
    public boolean hasFab() {
        return true;
    }

    @Override
    public void onFab() {
        showNewDialog();
    }

    /** Configura la dirección de la página de reservas y el WhatsApp, y copia el enlace. */
    private void showBookingDialog() {
        final JSONObject st = store.settings();
        LinearLayout box = Ui.column(activity);
        box.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), 0);
        box.addView(Ui.muted(activity, "Pega la dirección de tu página de reservas y tu número de WhatsApp. El cliente elige día y hora desde ese enlace."));
        box.addView(Ui.spacer(activity, 10));
        final EditText url = Ui.input(activity, "Dirección de la página de reservas", false);
        url.setText(st.optString("bookUrl", ""));
        box.addView(url);
        box.addView(Ui.spacer(activity, 8));
        final EditText tel = Ui.input(activity, "Tu WhatsApp, ej. 5355551234", true);
        tel.setText(st.optString("bookPhone", ""));
        box.addView(tel);

        new AlertDialog.Builder(activity)
                .setTitle("Reservas por enlace")
                .setView(box)
                .setPositiveButton("Copiar enlace", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Store.put(st, "bookUrl", url.getText().toString().trim());
                        Store.put(st, "bookPhone", tel.getText().toString().trim());
                        store.save();
                        String link = BookingLink.build(store);
                        if (link.isEmpty()) {
                            toast("Escribe la dirección de tu página de reservas.");
                            return;
                        }
                        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("reservas", link));
                        toast("Enlace copiado.");
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void showNewDialog() {
        final List<JSONObject> services = activeServices();
        if (services.isEmpty()) {
            toast("Agrega un servicio en Ajustes primero.");
            return;
        }

        LinearLayout form = Ui.column(activity);
        form.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), 0);

        final Spinner type = new Spinner(activity);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, new String[]{"Cita", "Bloqueo de horario"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        type.setAdapter(typeAdapter);

        final EditText client = Ui.input(activity, "Nombre del cliente", false);
        final EditText phone = Ui.input(activity, "Teléfono (opcional)", false);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        final Spinner service = new Spinner(activity);
        List<String> names = new ArrayList<>();
        for (JSONObject s : services) names.add(s.optString("name") + " · " + s.optInt("minutes") + " min");
        ArrayAdapter<String> svcAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, names);
        svcAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        service.setAdapter(svcAdapter);
        final EditText reason = Ui.input(activity, "Motivo (ej. almuerzo)", false);
        final EditText minutes = Ui.input(activity, "Duración en minutos", true);

        final String[] dateHolder = {day};
        final int[] startHolder = {9 * 60};
        final Button dateBtn = Ui.button(activity, Store.displayDate(day), false);
        final Button timeBtn = Ui.button(activity, Store.hhmm(startHolder[0]), false);
        dateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Calendar c = Store.parseDate(dateHolder[0]);
                new DatePickerDialog(activity, new DatePickerDialog.OnDateSetListener() {
                    @Override public void onDateSet(android.widget.DatePicker p, int y, int m, int d) {
                        dateHolder[0] = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                        dateBtn.setText(Store.displayDate(dateHolder[0]));
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        timeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new TimePickerDialog(activity, new TimePickerDialog.OnTimeSetListener() {
                    @Override public void onTimeSet(android.widget.TimePicker p, int h, int m) {
                        startHolder[0] = h * 60 + m;
                        timeBtn.setText(Store.hhmm(startHolder[0]));
                    }
                }, startHolder[0] / 60, startHolder[0] % 60, true).show();
            }
        });

        // Selector de clientes ya guardados: al elegir uno se copian nombre y teléfono,
        // así la cita queda asociada a su ficha (misma clave de cliente).
        final List<JSONObject> knownClients = new ArrayList<>();
        final List<String> clientNames = new ArrayList<>();
        clientNames.add("Nuevo cliente (escribir abajo)");
        JSONArray fichas = store.arr("clients");
        for (int i = 0; i < fichas.length(); i++) {
            JSONObject ficha = fichas.optJSONObject(i);
            if (ficha == null) continue;
            knownClients.add(ficha);
            String tel = ficha.optString("phone");
            clientNames.add(ficha.optString("name") + (tel.isEmpty() ? "" : " · " + tel));
        }
        final Spinner clientPick = new Spinner(activity);
        ArrayAdapter<String> pickAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, clientNames);
        pickAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        clientPick.setAdapter(pickAdapter);
        clientPick.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) return;
                JSONObject ficha = knownClients.get(pos - 1);
                client.setText(ficha.optString("name"));
                phone.setText(ficha.optString("phone"));
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        final LinearLayout clientBlock = Ui.column(activity);
        clientBlock.addView(Ui.label(activity, "Cliente de tu lista"));
        clientBlock.addView(clientPick);
        Button fromContacts = Ui.button(activity, "Elegir de mis contactos", false);
        fromContacts.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pickClient = client;
                pickPhone = phone;
                try {
                    activity.startActivityForResult(ContactsHelper.pickIntent(), ContactsHelper.REQ_PICK);
                } catch (ActivityNotFoundException e) {
                    toast("No se encontró la app de contactos.");
                }
            }
        });
        clientBlock.addView(fromContacts);
        clientBlock.addView(Ui.label(activity, "Cliente"));
        clientBlock.addView(client);
        clientBlock.addView(Ui.label(activity, "Teléfono"));
        clientBlock.addView(phone);
        clientBlock.addView(Ui.label(activity, "Servicio"));
        clientBlock.addView(service);

        final LinearLayout blockBlock = Ui.column(activity);
        blockBlock.addView(Ui.label(activity, "Motivo"));
        blockBlock.addView(reason);
        blockBlock.addView(Ui.label(activity, "Duración en minutos"));
        blockBlock.addView(minutes);
        blockBlock.setVisibility(View.GONE);

        form.addView(Ui.label(activity, "Tipo"));
        form.addView(type);
        form.addView(Ui.label(activity, "Día"));
        form.addView(dateBtn);
        form.addView(Ui.label(activity, "Hora de inicio"));
        form.addView(timeBtn);
        form.addView(clientBlock);
        form.addView(blockBlock);

        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                boolean block = pos == 1;
                clientBlock.setVisibility(block ? View.GONE : View.VISIBLE);
                blockBlock.setVisibility(block ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Nueva cita o bloqueo")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String err = saveNew(type.getSelectedItemPosition() == 1, dateHolder[0], startHolder[0],
                        client.getText().toString().trim(), phone.getText().toString().trim(),
                        services.get(service.getSelectedItemPosition()),
                        reason.getText().toString().trim(), minutes.getText().toString().trim());
                if (err == null) {
                    dialog.dismiss();
                } else {
                    toast(err);
                }
            }
        });
    }

    /** Valida y guarda. Devuelve null si todo está bien, o el mensaje de error. */
    private String saveNew(boolean block, String date, int start, String client, String phone,
                           JSONObject svc, String reason, String minutesText) {
        int minutes;
        if (block) {
            try { minutes = Integer.parseInt(minutesText); } catch (NumberFormatException e) { minutes = -1; }
            if (minutes < 5 || minutes > 240) return "La duración debe estar entre 5 y 240 minutos.";
        } else {
            if (client.isEmpty()) return "Escribe el nombre del cliente.";
            minutes = svc.optInt("minutes", 30);
        }
        int end = start + minutes;
        int[] win = store.workWindow(date);
        if (win == null) return "Ese día no se trabaja según el perfil del barbero.";
        if (start < win[0] || end > win[1]) {
            return "El horario debe estar entre " + Store.hhmm(win[0]) + " y " + Store.hhmm(win[1]) + ".";
        }
        if (overlaps(date, start, end)) return "Ese horario ya está ocupado.";

        JSONObject a = new JSONObject();
        Store.put(a, "id", store.newId("a"));
        Store.put(a, "date", date);
        Store.put(a, "start", start);
        Store.put(a, "minutes", minutes);
        Store.put(a, "kind", block ? "bloqueo" : "cita");
        Store.put(a, "status", block ? "bloqueado" : "pendiente");
        if (block) {
            Store.put(a, "reason", reason.isEmpty() ? "Bloqueo" : reason);
        } else {
            Store.put(a, "client", client);
            Store.put(a, "phone", phone);
            Store.put(a, "serviceId", svc.optString("id"));
        }
        Store.add(store.arr("appts"), a);
        CalendarSync.onChange(activity, store, a);
        store.save();
        day = date;
        refresh();
        return null;
    }

    private boolean overlaps(String date, int start, int end) {
        JSONArray citas = store.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject o = citas.optJSONObject(i);
            if (o == null || !date.equals(o.optString("date"))) continue;
            if ("cancelada".equals(o.optString("status"))) continue;
            int s = o.optInt("start"), e = s + o.optInt("minutes");
            if (start < e && s < end) return true;
        }
        return false;
    }

    // ---------- Consultas ----------

    private List<JSONObject> apptsOn(String date) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray citas = store.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject o = citas.optJSONObject(i);
            if (o != null && date.equals(o.optString("date"))) out.add(o);
        }
        java.util.Collections.sort(out, new java.util.Comparator<JSONObject>() {
            @Override public int compare(JSONObject x, JSONObject y) { return x.optInt("start") - y.optInt("start"); }
        });
        return out;
    }

    /** Citas activas que empiezan entre ahora y la antelación configurada. */
    private List<JSONObject> remindersDue() {
        List<JSONObject> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        long window = store.settings().optInt("lead", 1440) * 60000L;
        JSONArray citas = store.arr("appts");
        for (int i = 0; i < citas.length(); i++) {
            JSONObject a = citas.optJSONObject(i);
            if (a == null || !"cita".equals(a.optString("kind"))) continue;
            String st = a.optString("status");
            if (!"pendiente".equals(st) && !"confirmada".equals(st)) continue;
            Calendar c = Store.parseDate(a.optString("date"));
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.add(Calendar.MINUTE, a.optInt("start"));
            long t = c.getTimeInMillis();
            if (t > now && t - now <= window) out.add(a);
        }
        java.util.Collections.sort(out, new java.util.Comparator<JSONObject>() {
            @Override public int compare(JSONObject x, JSONObject y) {
                int d = x.optString("date").compareTo(y.optString("date"));
                return d != 0 ? d : x.optInt("start") - y.optInt("start");
            }
        });
        return out;
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
        return s == null ? "Servicio" : s.optString("name");
    }

    private String statusLabel(String s) {
        if ("pendiente".equals(s)) return "Pendiente";
        if ("confirmada".equals(s)) return "Confirmada";
        if ("completada".equals(s)) return "Completada";
        if ("cancelada".equals(s)) return "Cancelada";
        return "";
    }

    private String dayTitle(String iso) {
        String[] days = {"Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};
        Calendar c = Store.parseDate(iso);
        return days[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + Store.displayDate(iso);
    }
}
