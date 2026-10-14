package com.barberia.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Activación de BarberPro.
 *
 * El código de activación (16 caracteres, 10 bytes) lleva dentro:
 *   - número de emisión: días desde el 1 de enero de 2026 (2 bytes)
 *   - plan: 1 = mensual (30 días), 2 = 6 meses (180 días), 3 = de por vida (1 byte)
 *   - firma: 7 bytes de HMAC sobre código de dispositivo, plan y emisión
 *
 * Como cada emisión tiene un número nuevo, renovar produce un código distinto.
 * La app rechaza códigos ya usados y códigos con un número menor al último aceptado.
 */
public final class License {

    // Clave secreta del vendedor. Debe coincidir con el bot de Telegram.
    private static final String SECRET = "9c631804f560cce55b4a2062c853eb483b43330a4961939f8baf622ff738e15c";
    private static final String ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 32 caracteres
    private static final String PREFS = "barberia_lic";
    private static final long TRIAL_DAYS = 3;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static final int PLAN_MONTH = 1;
    private static final int PLAN_HALF = 2;
    private static final int PLAN_LIFE = 3;

    private License() { }

    /** Código de dispositivo que el cliente envía al vendedor (formato XXXX-XXXX-XXXX-XXXX). */
    public static String deviceCode(Context c) {
        String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null) id = "sin-id";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(("dev:" + id).getBytes("UTF-8"));
            byte[] cut = Arrays.copyOf(h, 10);
            return format(encode(cut));
        } catch (Exception e) {
            return "ERROR-DE-CODIGO";
        }
    }

    private static String plain(String s) {
        return s == null ? "" : s.replace("-", "").replace(" ", "").toUpperCase(Locale.US);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Firma de 7 bytes para un dispositivo, un plan y un número de emisión. */
    private static byte[] tagFor(String dev, int plan, int counter) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes("UTF-8"), "HmacSHA256"));
        byte[] h = mac.doFinal((plain(dev) + "|" + plan + "|" + counter).getBytes("UTF-8"));
        return Arrays.copyOf(h, 7);
    }

    /** Código antiguo (sin plan ni número): cuenta como de por vida. */
    private static boolean legacyMatches(String dev, String input) {
        String clean = plain(input);
        if (clean.isEmpty()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes("UTF-8"), "HmacSHA256"));
            byte[] h = mac.doFinal(plain(dev).getBytes("UTF-8"));
            return clean.equals(encode(Arrays.copyOf(h, 10)));
        } catch (Exception e) {
            return false;
        }
    }

    private static int counterIn(byte[] b) {
        return ((b[0] & 0xFF) << 8) | (b[1] & 0xFF);
    }

    /** Plan del código para este dispositivo (1, 2 o 3), o -1 si la firma no coincide. */
    private static int planIn(String dev, byte[] b) {
        int plan = b[2] & 0xFF;
        if (plan < PLAN_MONTH || plan > PLAN_LIFE) return -1;
        try {
            if (MessageDigest.isEqual(Arrays.copyOfRange(b, 3, 10), tagFor(dev, plan, counterIn(b)))) return plan;
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }

    /** Plan del código guardado si sigue siendo válido para este dispositivo; -1 si no. */
    private static int savedPlan(Context c) {
        String saved = prefs(c).getString("code", "");
        if (saved.isEmpty()) return -1;
        String dev = deviceCode(c);
        byte[] b = decode(saved);
        int plan = b != null ? planIn(dev, b) : -1;
        if (plan > 0) return plan;
        return legacyMatches(dev, saved) ? PLAN_LIFE : -1;
    }

    public static boolean isActivated(Context c) {
        int plan = savedPlan(c);
        if (plan < 0) return false;
        if (plan == PLAN_LIFE) return true;
        return System.currentTimeMillis() < expiresAt(c);
    }

    /** Tiene una licencia guardada en este dispositivo que ya venció. */
    public static boolean isExpired(Context c) {
        return savedPlan(c) > 0 && !isActivated(c);
    }

    /** Nombre del plan activo: Mensual, 6 meses o De por vida. */
    public static String planLabel(Context c) {
        int plan = savedPlan(c);
        if (plan == PLAN_MONTH) return "Mensual";
        if (plan == PLAN_HALF) return "6 meses";
        if (plan == PLAN_LIFE) return "De por vida";
        return "";
    }

    /** Momento en que vence la licencia guardada, en milisegundos. 0 si es de por vida. */
    public static long expiresAt(Context c) {
        int plan = savedPlan(c);
        if (plan < 0 || plan == PLAN_LIFE) return 0;
        long days = plan == PLAN_MONTH ? 30 : 180;
        return prefs(c).getLong("start", 0) + days * DAY;
    }

    /** Texto con la fecha de vencimiento, para mostrar en Ajustes. */
    public static String expiryText(Context c) {
        long end = expiresAt(c);
        if (end == 0) return "Licencia de por vida. Todas las funciones están disponibles.";
        return "Vence el " + new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date(end)) + ".";
    }

    /** Momento de la primera apertura; empieza a contar el período de prueba. */
    public static long trialStart(Context c) {
        SharedPreferences p = prefs(c);
        long t = p.getLong("trial_start", 0);
        if (t == 0) {
            t = System.currentTimeMillis();
            p.edit().putLong("trial_start", t).apply();
        }
        return t;
    }

    /** Días de prueba que quedan (0 si ya terminó). */
    public static long trialDaysLeft(Context c) {
        long left = trialStart(c) + TRIAL_DAYS * DAY - System.currentTimeMillis();
        if (left <= 0) return 0;
        return (left + DAY - 1) / DAY;
    }

    /** La app funciona si la licencia está vigente o si aún está en el período de prueba. */
    public static boolean isUnlocked(Context c) {
        return isActivated(c) || trialDaysLeft(c) > 0;
    }

    /** Guarda el código si es válido para este dispositivo y no es una copia de uno ya usado. */
    public static boolean activate(Context c, String input) {
        String clean = plain(input);
        if (clean.isEmpty()) return false;
        String dev = deviceCode(c);
        SharedPreferences p = prefs(c);

        byte[] b = decode(clean);
        int plan = b != null ? planIn(dev, b) : -1;
        if (plan > 0) {
            // Cada emisión sirve una sola vez: el número tiene que ser mayor al último usado.
            int counter = counterIn(b);
            if (counter <= p.getInt("last_ctr", 0)) return false;
            p.edit()
                    .putString("code", clean)
                    .putLong("start", System.currentTimeMillis())
                    .putInt("last_ctr", counter)
                    .apply();
            return true;
        }
        if (legacyMatches(dev, clean)) {
            p.edit().putString("code", clean).putLong("start", System.currentTimeMillis()).apply();
            return true;
        }
        return false;
    }

    /** Convierte 16 caracteres del alfabeto en 10 bytes. Devuelve null si el texto no sirve. */
    private static byte[] decode(String s) {
        if (s.length() != 16) return null;
        byte[] out = new byte[10];
        int buffer = 0;
        int bits = 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            int idx = ALPHA.indexOf(s.charAt(i));
            if (idx < 0) return null;
            buffer = ((buffer << 5) | idx) & 0xFFFFF;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out[n++] = (byte) ((buffer >> bits) & 0xFF);
            }
        }
        return n == 10 ? out : null;
    }

    private static String encode(byte[] b) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte x : b) {
            buffer = ((buffer << 8) | (x & 0xFF)) & 0xFFFFF;
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(ALPHA.charAt((buffer >> bits) & 31));
            }
        }
        if (bits > 0) sb.append(ALPHA.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    private static String format(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && i % 4 == 0) out.append('-');
            out.append(s.charAt(i));
        }
        return out.toString();
    }
}
