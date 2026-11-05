package app.morphe.extension.youtube.bilibilidanmaku;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores only the user's Bilibili SESSDATA, encrypted with a non-exportable Android Keystore key. */
final class BilibiliDanmakuSession {
    private static final String STORE = "morphe_bilibili_danmaku_private";
    private static final String ALIAS = "morphe_bilibili_danmaku_session_v1";
    private static final String VALUE = "session";
    private static final Pattern SESSION = Pattern.compile("(?:^|;\\s*)SESSDATA=([^;\\s]+)", Pattern.CASE_INSENSITIVE);
    private static Context context;

    private BilibiliDanmakuSession() {}

    static synchronized void initialize(Context ctx) {
        context = ctx.getApplicationContext();
    }

    static synchronized boolean hasSession() {
        return !getCookieHeader().isEmpty();
    }

    static synchronized String getCookieHeader() {
        if (context == null) return "";
        String ciphertext = storage().getString(VALUE, "");
        if (ciphertext == null || ciphertext.isEmpty()) return "";
        try {
            byte[] bytes = Base64.decode(ciphertext, Base64.NO_WRAP);
            if (bytes.length < 13) return "";
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[bytes.length - iv.length];
            System.arraycopy(bytes, 0, iv, 0, iv.length);
            System.arraycopy(bytes, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            return "SESSDATA=" + new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // A restored preference without its device-specific key must not crash YouTube.
            return "";
        }
    }

    /** Accepts a SESSDATA value or the full Cookie request header; rejects control characters. */
    static synchronized void save(String pasted) throws Exception {
        if (context == null) throw new IllegalStateException("播放器未初始化");
        String input = pasted == null ? "" : pasted.trim();
        Matcher matcher = SESSION.matcher(input);
        String value = matcher.find() ? matcher.group(1) : input;
        if (value.startsWith("SESSDATA=")) value = value.substring("SESSDATA=".length());
        if (value.length() < 8 || value.length() > 4096 || !value.matches("[a-zA-Z0-9_%.,=+\\-/]+")) {
            throw new IllegalArgumentException("请输入有效的 SESSDATA 值，或含 SESSDATA 的 Cookie 字符串");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] joined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, joined, 0, iv.length);
        System.arraycopy(encrypted, 0, joined, iv.length, encrypted.length);
        if (!storage().edit().putString(VALUE, Base64.encodeToString(joined, Base64.NO_WRAP)).commit()) {
            throw new IllegalStateException("无法保存 B 站登录信息");
        }
    }

    static synchronized void clear() {
        if (context != null) storage().edit().remove(VALUE).apply();
    }

    private static SharedPreferences storage() {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    private static SecretKey encryptionKey() throws Exception {
        KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
        keystore.load(null);
        if (keystore.containsAlias(ALIAS)) {
            return (SecretKey) keystore.getKey(ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
