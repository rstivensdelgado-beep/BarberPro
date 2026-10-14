package com.barberia.app.screens;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.barberia.app.GalleryProvider;
import com.barberia.app.MainActivity;
import com.barberia.app.Screen;
import com.barberia.app.Store;
import com.barberia.app.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Galería de trabajos. Cada foto se asigna a un cliente para armar un catálogo
 * por cliente que después se puede compartir. Las fotos se guardan solo en este teléfono.
 */
public class GalleryScreen extends Screen {

    private static final int MAX_PX = 900;
    private static final int SHOW_LIMIT = 60;
    private static final int SHARE_LIMIT = 10;

    private LinearLayout content;
    /** "" = todas; "general" = sin cliente; si no, la clave del cliente. */
    private String filter = "";

    public GalleryScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    protected View build() {
        content = Ui.column(activity);
        return Ui.page(activity, content);
    }

    @Override
    public boolean hasFab() {
        return true;
    }

    @Override
    public void onFab() {
        activity.pickGalleryPhoto();
    }

    @Override
    public void refresh() {
        if (content == null) return;
        content.removeAllViews();
        content.addView(Ui.title(activity, "Galería"));
        content.addView(Ui.muted(activity, "Fotos de tus trabajos, por cliente. Se guardan solo en este teléfono."));

        JSONArray g = store.arr("gallery");
        if (g.length() == 0) {
            content.addView(Ui.label(activity, "Fotos"));
            LinearLayout empty = Ui.card(activity);
            empty.addView(Ui.emptyState(activity, "Aún no hay fotos",
                    "Toca + para agregar la primera. Te preguntará de qué cliente es."));
            content.addView(empty, Ui.matchWrap());
            return;
        }

        content.addView(Ui.label(activity, "Clientes"));
        content.addView(chips(g), Ui.matchWrap());

        // Catálogo del cliente seleccionado: se comparte con sus fotos.
        if (!filter.isEmpty()) {
            LinearLayout share = Ui.column(activity);
            share.setPadding(0, Ui.dp(activity, 14), 0, 0);
            android.widget.Button btn = Ui.button(activity, "Compartir catálogo", true);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { shareCatalog(); }
            });
            share.addView(btn);
            content.addView(share, Ui.matchWrap());
        }

        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int cell = (screen - Ui.dp(activity, 32) - Ui.dp(activity, 10)) / 2;
        content.addView(Ui.label(activity, filter.isEmpty() ? "Fotos recientes" : "Fotos del catálogo"));

        List<JSONObject> shownItems = new ArrayList<>();
        for (int i = g.length() - 1; i >= 0 && shownItems.size() < SHOW_LIMIT; i--) {
            JSONObject it = g.optJSONObject(i);
            if (it != null && matchesFilter(it)) shownItems.add(it);
        }
        if (shownItems.isEmpty()) {
            LinearLayout empty = Ui.card(activity);
            empty.addView(Ui.emptyState(activity, "Sin fotos aquí", "Este cliente aún no tiene fotos en su catálogo."));
            content.addView(empty, Ui.matchWrap());
            return;
        }

        LinearLayout row = null;
        for (int shown = 0; shown < shownItems.size(); shown++) {
            final JSONObject it = shownItems.get(shown);
            if (shown % 2 == 0) {
                row = Ui.row(activity);
                row.setGravity(Gravity.TOP);
                content.addView(row, Ui.matchWrap());
                if (shown > 0) row.setPadding(0, Ui.dp(activity, 12), 0, 0);
            }
            LinearLayout box = Ui.column(activity);
            ImageView iv = new ImageView(activity);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.solid(activity, Ui.SURFACE, 12));
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            Bitmap bm = BitmapFactory.decodeFile(it.optString("file"), o);
            if (bm != null) iv.setImageBitmap(bm);
            box.addView(iv, new LinearLayout.LayoutParams(cell, cell));

            String cname = it.optString("cname");
            box.addView(Ui.muted(activity, (cname.isEmpty() ? "General" : cname) + " · " + Store.displayDate(it.optString("date"))));

            final String id = it.optString("id");
            LinearLayout actions = Ui.row(activity);
            actions.addView(linkAction("Cliente", Ui.ACCENT, new Runnable() {
                public void run() { askClient(id); }
            }));
            actions.addView(linkAction("Quitar", Ui.DANGER, new Runnable() {
                public void run() { remove(id); }
            }));
            box.addView(actions);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cell, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (shown % 2 == 0) lp.rightMargin = Ui.dp(activity, 10);
            row.addView(box, lp);
        }
        if (g.length() > SHOW_LIMIT && filter.isEmpty()) {
            content.addView(Ui.muted(activity, "Se muestran las " + SHOW_LIMIT + " fotos más recientes."));
        }
    }

    /** Fila horizontal de clientes con fotos: "Todas", "General" y cada cliente. */
    private HorizontalScrollView chips(JSONArray g) {
        Map<String, String> clients = new LinkedHashMap<>();
        boolean hasGeneral = false;
        for (int i = 0; i < g.length(); i++) {
            JSONObject it = g.optJSONObject(i);
            if (it == null) continue;
            String key = it.optString("ckey");
            if (key.isEmpty()) hasGeneral = true;
            else if (!clients.containsKey(key)) clients.put(key, it.optString("cname", "Cliente"));
        }
        LinearLayout row = Ui.row(activity);
        row.addView(chip("Todas", ""));
        if (hasGeneral) row.addView(chip("General", "general"));
        for (Map.Entry<String, String> e : clients.entrySet()) {
            row.addView(chip(e.getValue(), e.getKey()));
        }
        HorizontalScrollView hs = new HorizontalScrollView(activity);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        return hs;
    }

    private TextView chip(String label, final String key) {
        boolean selected = key.equals(filter);
        TextView t = Ui.text(activity, label, 13, selected ? Ui.ON_INK : Ui.INK, true);
        t.setSingleLine(true);
        t.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8), Ui.dp(activity, 14), Ui.dp(activity, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(activity, 18));
        bg.setColor(selected ? Ui.ACCENT : Ui.SURFACE);
        bg.setStroke(Ui.dp(activity, 1), Ui.LINE);
        t.setBackground(bg);
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                filter = key;
                refresh();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(activity, 8);
        lp.topMargin = Ui.dp(activity, 6);
        lp.bottomMargin = Ui.dp(activity, 4);
        t.setLayoutParams(lp);
        return t;
    }

    private boolean matchesFilter(JSONObject it) {
        if (filter.isEmpty()) return true;
        String key = it.optString("ckey");
        if ("general".equals(filter)) return key.isEmpty();
        return filter.equals(key);
    }

    /** Guarda la foto elegida como JPEG reducido y pregunta de qué cliente es. */
    public void onPicked(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream a = activity.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(a, null, bounds);
            a.close();

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_PX * 2) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            InputStream b = activity.getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(b, null, o);
            b.close();
            if (bmp == null) {
                toast("No se pudo abrir la foto.");
                return;
            }

            int w = bmp.getWidth();
            int h = bmp.getHeight();
            float k = Math.min(1f, (float) MAX_PX / Math.max(w, h));
            Bitmap out = k < 1f ? Bitmap.createScaledBitmap(bmp, Math.round(w * k), Math.round(h * k), true) : bmp;

            String id = store.newId("f");
            File dir = new File(activity.getFilesDir(), "galeria");
            dir.mkdirs();
            File f = new File(dir, id + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            out.compress(Bitmap.CompressFormat.JPEG, 82, fos);
            fos.close();

            JSONObject item = new JSONObject();
            Store.put(item, "id", id);
            Store.put(item, "file", f.getAbsolutePath());
            Store.put(item, "date", Store.today());
            Store.put(item, "cname", "");
            Store.put(item, "ckey", "");
            Store.add(store.arr("gallery"), item);
            store.save();
            refresh();
            askClient(id);
        } catch (Exception e) {
            toast("No se pudo guardar la foto.");
        }
    }

    /** Pregunta de qué cliente es la foto. Se puede dejar como "General". */
    private void askClient(final String id) {
        final List<JSONObject> clients = clientList();
        String[] names = new String[clients.size() + 1];
        names[0] = "General (sin cliente)";
        for (int i = 0; i < clients.size(); i++) {
            JSONObject c = clients.get(i);
            String tel = c.optString("phone");
            names[i + 1] = c.optString("name") + (tel.isEmpty() ? "" : " · " + tel);
        }
        new AlertDialog.Builder(activity)
                .setTitle("¿De qué cliente es esta foto?")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            setClient(id, "", "");
                        } else {
                            JSONObject c = clients.get(which - 1);
                            String name = c.optString("name");
                            setClient(id, name, Store.clientKey(name, c.optString("phone")));
                        }
                    }
                })
                .setNegativeButton("Después", null)
                .show();
    }

    private void setClient(String id, String name, String key) {
        JSONArray g = store.arr("gallery");
        for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                Store.put(o, "cname", name);
                Store.put(o, "ckey", key);
                break;
            }
        }
        store.save();
        refresh();
    }

    private List<JSONObject> clientList() {
        List<JSONObject> out = new ArrayList<>();
        JSONArray c = store.arr("clients");
        for (int i = 0; i < c.length(); i++) {
            JSONObject o = c.optJSONObject(i);
            if (o != null && !o.optString("name").isEmpty()) out.add(o);
        }
        return out;
    }

    /** Comparte hasta 10 fotos del catálogo seleccionado, con un texto de presentación. */
    private void shareCatalog() {
        JSONArray g = store.arr("gallery");
        ArrayList<Uri> uris = new ArrayList<>();
        for (int i = g.length() - 1; i >= 0 && uris.size() < SHARE_LIMIT; i--) {
            JSONObject it = g.optJSONObject(i);
            if (it == null || !matchesFilter(it)) continue;
            File f = new File(it.optString("file"));
            if (!f.exists()) continue;
            uris.add(Uri.parse("content://" + GalleryProvider.AUTHORITY + "/" + f.getName()));
        }
        if (uris.isEmpty()) {
            toast("No hay fotos para compartir.");
            return;
        }
        String shop = store.settings().optString("shopName", "BarberPro");
        String who = "general".equals(filter) ? "" : " de " + nameOfFilter();
        String caption = "Catálogo de trabajos" + who + " · " + shop;

        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        send.setType("image/jpeg");
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        send.putExtra(Intent.EXTRA_TEXT, caption);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        ClipData clip = null;
        for (Uri u : uris) {
            if (clip == null) clip = ClipData.newUri(activity.getContentResolver(), "catalogo", u);
            else clip.addItem(new ClipData.Item(u));
        }
        send.setClipData(clip);

        Intent chooser = Intent.createChooser(send, "Compartir catálogo");
        chooser.setClipData(clip);
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(chooser);
    }

    private String nameOfFilter() {
        JSONArray g = store.arr("gallery");
        for (int i = 0; i < g.length(); i++) {
            JSONObject it = g.optJSONObject(i);
            if (it != null && filter.equals(it.optString("ckey"))) return it.optString("cname", "cliente");
        }
        return "cliente";
    }

    /** Quita la foto con confirmación y aviso para deshacer. */
    private void remove(final String id) {
        new AlertDialog.Builder(activity)
                .setTitle("¿Quitar esta foto?")
                .setMessage("Se borra de este teléfono.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Quitar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        JSONArray g = store.arr("gallery");
                        for (int i = 0; i < g.length(); i++) {
                            JSONObject o = g.optJSONObject(i);
                            if (o != null && id.equals(o.optString("id"))) {
                                new File(o.optString("file")).delete();
                                Store.removeAt(g, i);
                                break;
                            }
                        }
                        store.save();
                        refresh();
                    }
                })
                .show();
    }

    private View linkAction(String label, int color, final Runnable r) {
        View l = Ui.link(activity, label, color);
        l.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        return l;
    }
}
