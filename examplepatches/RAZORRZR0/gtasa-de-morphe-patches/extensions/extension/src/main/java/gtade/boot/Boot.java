package gtade.boot;

import android.app.Application;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * GameActivity's static init calls start() right after loadLibrary("UE4") (Mod loader patch).
 * The mod patches put their files in assets/gtade/{mods,configs,cleo}/. After each (re)patch this copies them to
 * Android/data/<game>/{mods,configs,cleo}, where AML and CLEO load them, then loads AML. An existing .ini is kept: it holds
 * the user's settings. Mod libraries from an earlier patch that are no longer included are deleted.
 */
public final class Boot {
    private static final String TAG = "GTADEBoot";
    private static final String STATE = "mods/.gtade_patched";  // line 1: APK update time; then installed mod paths

    public static void start() {
        try {
            sync();
        } catch (Throwable t) {
            Log.w(TAG, "mod sync failed, starting with the current mods", t);
        }
        System.loadLibrary("AML");
    }

    private static void sync() throws Exception {
        Application app = (Application) Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null);
        File root = app.getExternalFilesDir(null).getParentFile();  // Android/data/<game>
        String stamp = Long.toString(app.getPackageManager().getPackageInfo(app.getPackageName(), 0).lastUpdateTime);
        File state = new File(root, STATE);
        List<String> old = new ArrayList<>();
        if (state.exists()) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(state)))) {
                for (String l; (l = r.readLine()) != null; ) old.add(l);
            }
        }
        if (!old.isEmpty() && old.get(0).equals(stamp)) return;  // already synced for this APK

        AssetManager assets = app.getAssets();
        List<String> now = new ArrayList<>();
        for (String dir : new String[]{"mods", "configs", "cleo"}) {
            String[] names = assets.list("gtade/" + dir);
            if (names == null) continue;
            for (String n : names) {
                String path = dir + "/" + n;
                File dst = new File(root, path);
                if (dir.equals("mods")) now.add(path);
                if (n.endsWith(".ini") && dst.exists()) continue;
                dst.getParentFile().mkdirs();
                File tmp = new File(dst.getPath() + ".tmp");
                try (InputStream in = assets.open("gtade/" + path); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[1 << 16];
                    for (int k; (k = in.read(buf)) > 0; ) out.write(buf, 0, k);
                }
                if (!tmp.renameTo(dst)) throw new IllegalStateException("cannot write " + dst);
                Log.i(TAG, "installed " + path);
            }
        }
        for (String p : old.subList(Math.min(1, old.size()), old.size())) {
            if (!now.contains(p) && new File(root, p).delete()) Log.i(TAG, "removed " + p);
        }
        state.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(state)) {
            out.write((stamp + "\n").getBytes("UTF-8"));
            for (String p : now) out.write((p + "\n").getBytes("UTF-8"));
        }
    }
}
