package com.barberia.app;

import android.view.View;

/** Base de cada pestaña. La vista se construye una vez y luego se refresca al mostrarla. */
public abstract class Screen {

    protected final MainActivity activity;
    protected final Store store;
    protected View view;

    protected Screen(MainActivity activity) {
        this.activity = activity;
        this.store = Store.get(activity);
    }

    /** Construye la jerarquía de vistas. Se llama una sola vez. */
    protected abstract View build();

    /** Vuelve a leer los datos y actualiza la pantalla. Se llama cada vez que se muestra. */
    public abstract void refresh();

    public View view() {
        if (view == null) {
            view = build();
            refresh();
        }
        return view;
    }

    /** Indica si esta pestaña muestra el botón flotante (+) dorado. */
    public boolean hasFab() {
        return false;
    }

    /** Acción del botón flotante (+): agregar lo principal de la pantalla. */
    public void onFab() {
    }

    /** Mensaje corto al usuario. */
    protected void toast(String msg) {
        android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
