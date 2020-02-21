package com.fox2code.udk.plugins;

import com.fox2code.repacker.Utils;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

public class Decompiler {
    private static final Map<String, Object> opt = new HashMap<>();

    static {
        opt.put("log", "ERROR");
        opt.put("nls", "1");
        opt.put("ind", "    ");
        opt.put("bsm", "1");
        opt.put("dgs", "1");
        opt.put("hes", "0");
        //noinspection StatementWithEmptyBody
        if (!RepackerPlugin.FLAG_DECOMPILER) {
            /*
            * This class is useless until someone fixes the sources import issue
            * Ctrl+Click on "FLAG_DECOMPILER" to see about what I'm talking about
            * Until then I'm sad :(
            * */
        }
    }

    public static void decompile(File from, File to) throws IOException {
        System.gc(); // Avoid OutOfMemoryError
        Closeable closeable = null;
        Map<String,byte[]> orig;
        try {
            orig = Utils.readZIP((InputStream) (closeable = new FileInputStream(from)));
        } finally {
            if (closeable != null) try {
                closeable.close();
            } catch (Throwable ignored) {}
            closeable = null;
        }
        Map<String,byte[]> patched = new HashMap<>(orig.size()/2);
        Saver saver = new Saver(orig, patched);
        Fernflower fernflower = new Fernflower(saver, saver, opt, new PrintStreamLogger(System.out));
        fernflower.addSource(from);
        fernflower.decompileContext();
        System.gc(); // Avoid OutOfMemoryError
        try {
        Utils.writeZIP(patched, (OutputStream) (closeable = new FileOutputStream(to)));
        } finally {
            if (closeable != null) try {
                closeable.close();
            } catch (Throwable ignored) {}
        }
    }

    private static class Saver implements IResultSaver, IBytecodeProvider {
        private final Map<String,byte[]> orig;
        private final Map<String,byte[]> patched;

        private Saver(Map<String,byte[]> orig, Map<String,byte[]> patched) {
            this.orig = orig;
            this.patched = patched;
        }

        @Override
        public void saveFolder(String s) {

        }

        @Override
        public void copyFile(String s, String s1, String s2) {

        }

        @Override
        public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
            this.patched.put(entryName, content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void createArchive(String s, String s1, Manifest manifest) {

        }

        @Override
        public void saveDirEntry(String s, String s1, String s2) {

        }

        @Override
        public void copyEntry(String s, String s1, String s2, String s3) {

        }

        @Override
        public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
            this.patched.put(entryName, content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void closeArchive(String s, String s1) {

        }

        @Override
        public byte[] getBytecode(String s, String s1) throws IOException {
            return orig.get(s1);
        }
    }
}
