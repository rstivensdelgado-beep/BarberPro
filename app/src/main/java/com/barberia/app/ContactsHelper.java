package com.barberia.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Contactos del móvil: importar clientes y elegir uno al crear una cita. */
public final class ContactsHelper {

    /** Código del permiso de lectura de contactos. */
    public static final int REQ_READ = 404;
    /** Código del selector de contactos del sistema. */
    public static final int REQ_PICK = 405;

    public static final class Contact {
        public final String name;
        public final String phone;

        public Contact(String name, String phone) {
            this.name = name == null ? "" : name.trim();
            this.phone = phone == null ? "" : localNumber(phone);
        }
    }

    private ContactsHelper() { }

    public static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < 23
                || c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void request(Activity a) {
        a.requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_READ);
    }

    /** Número como lo escribe la gente en Cuba: 8 dígitos, sin código de país ni cero inicial. */
    public static String localNumber(String phone) {
        String d = phone.replaceAll("\\D", "");
        if (d.length() == 10 && d.startsWith("53")) d = d.substring(2);
        if (d.length() == 9 && d.startsWith("0")) d = d.substring(1);
        return d;
    }

    /** Todos los teléfonos guardados en el móvil, sin repetir número. */
    public static List<Contact> readAll(Context c) {
        List<Contact> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Cursor cur = c.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
        if (cur == null) return out;
        try {
            while (cur.moveToNext()) {
                String name = cur.getString(0);
                String num = cur.getString(1);
                if (name == null || num == null) continue;
                Contact ct = new Contact(name, num);
                if (ct.name.isEmpty() || ct.phone.length() < 6 || !seen.add(ct.phone)) continue;
                out.add(ct);
            }
        } finally {
            cur.close();
        }
        return out;
    }

    /** Intent del selector de contactos del sistema (no requiere permiso). */
    public static Intent pickIntent() {
        return new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
    }

    /** Lee nombre y teléfono del contacto que se eligió en el selector. */
    public static Contact fromPicked(Context c, Uri uri) {
        Cursor cur = c.getContentResolver().query(uri,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null);
        if (cur == null) return null;
        try {
            if (!cur.moveToFirst()) return null;
            return new Contact(cur.getString(0), cur.getString(1));
        } finally {
            cur.close();
        }
    }
}
