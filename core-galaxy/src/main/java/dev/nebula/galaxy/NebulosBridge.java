package dev.nebula.galaxy;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * What the Nebulos core needs from Nebula's package-private classes (GameFiles, Importer): where the game lives, and setting it up from the player's
 * disc image (the same importer as Nebula's launcher: crates/nebula-import, libnebula_import.so).
 */
public final class NebulosBridge {
    private NebulosBridge() {}

    public interface Progress {
        /** false cancels */
        boolean update(long done, long total);
    }

    public static File dataDir(Context c) { return GameFiles.data(c); }

    public static File savesDir(Context c) { return GameFiles.savesIn(GameFiles.data(c)); }

    /** The disc image's game id ("RMGE01"...) and whether Nebula supports it; null when the file cannot be read. */
    public static String identify(File disc) {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(disc, ParcelFileDescriptor.MODE_READ_ONLY)) {
            return Importer.identify(fd.getFd());
        } catch (IOException e) {
            return null;
        }
    }

    private static File marker(Context c) { return new File(GameFiles.data(c), "game/nebulos-install.properties"); }

    /** The game is set up and the store is the one this importer writes. */
    public static boolean installed(Context c) {
        if (!GameFiles.pak(c).isFile()) return false;
        final Properties p = new Properties();
        try (java.io.FileInputStream in = new java.io.FileInputStream(marker(c))) {
            p.load(in);
            return String.valueOf(Importer.storeVersion()).equals(p.getProperty("storeVersion"));
        } catch (IOException e) {
            return false;
        }
    }

    /** Sets the game up from [disc] (3.9 GB, minutes); null when done, else the reason. Built beside the old one, so a failure keeps it. */
    public static String install(Context c, File disc, String gameId, Progress progress) {
        final File folder = GameFiles.pak(c).getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) return "Could not make " + folder;
        final File out = new File(folder, "game.pak.new");
        out.delete();
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(disc, ParcelFileDescriptor.MODE_READ_ONLY)) {
            final String error = Importer.build(fd.getFd(), out.getPath(), new Importer.Progress() {
                @Override public boolean update(long done, long total) { return progress.update(done, total); }
            });
            if (error != null) { out.delete(); return error; }
            final File pak = GameFiles.pak(c);
            pak.delete();
            if (!out.renameTo(pak)) return "Could not name game.pak";
            final Properties p = new Properties();
            p.setProperty("gameId", gameId == null ? "" : gameId);
            p.setProperty("storeVersion", String.valueOf(Importer.storeVersion()));
            p.setProperty("installedAt", String.valueOf(System.currentTimeMillis()));
            try (FileOutputStream o = new FileOutputStream(marker(c))) { p.store(o, null); }
            // Nebula's own record, so the game's checks (and a later standalone Nebula install) agree
            try (FileOutputStream o = new FileOutputStream(GameFiles.install(c))) {
                final Properties q = new Properties();
                q.setProperty("gameId", gameId == null ? "" : gameId);
                q.setProperty("title", "Super Mario Galaxy");
                q.setProperty("format", "");
                q.setProperty("source", disc.getName());
                q.setProperty("bytes", String.valueOf(pak.length()));
                q.setProperty("installedAt", String.valueOf(System.currentTimeMillis()));
                q.store(o, null);
            }
            return null;
        } catch (IOException e) {
            out.delete();
            return e.toString();
        }
    }
}
