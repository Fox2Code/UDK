package com.fox2code.udk.startup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Startup {
    private static String main = System.getProperty("udk.startup.main", "net.minecraft.client.main.Main");
    private static File natives = new File(Objects.requireNonNull(System.getProperty("udk.startup.natives")));

    public static void main(String[] args) throws Exception {
        System.out.println("Initialising...");
        boolean needSetup = true;
        if (!natives.exists()) {
            natives.mkdirs();
        }
        String[] files = natives.list();
        if (files != null) for (String str: files) {
            if (!str.startsWith(".")) {
                needSetup = false;
                break;
            }
        }
        if (needSetup) {
            setup();
        }
        System.setProperty("udk.startup.init", "true");
        Method method = Class.forName(main).getDeclaredMethod("main", String[].class);
        if (!Modifier.isPublic(method.getModifiers())) {
            Java9Fix.setAccessible(method);
        }
        method.invoke(null, (Object) args);
    }

    public static void setup() throws Exception {
        System.out.println("Setup natives...");
        String[] validExt = null;
        String os_name = System.getProperty("os.name").toLowerCase();
        if (os_name.contains("win")) {
            validExt = new String[]{".dll"};
        } else if (os_name.contains("mac")) {
            validExt = new String[]{".dylib", ".jnilib"};
        } else if (os_name.contains("nux")) {
            validExt = new String[]{".so"};
        } else {
            throw new Error("Invalid System: \""+os_name+"\"");
        }
        Class<?> URLClassPath;
        try {
            URLClassPath = Class.forName("sun.misc.URLClassPath");
        } catch (ClassNotFoundException e) {
            URLClassPath = Class.forName("jdk.internal.loader.URLClassPath");
        }
        Method urLs = URLClassPath.getDeclaredMethod("getURLs");
        Java9Fix.setAccessible(urLs);
        Class<?> ccl = Startup.class.getClassLoader().getClass();
        Field ucp = null;
        while (ucp == null && ccl != null) {
            try {
                ucp = ccl.getDeclaredField("ucp");
            } catch (ReflectiveOperationException e) {
                ccl = ccl.getSuperclass();
            }
        }
        if (ucp == null) {
            throw new NoSuchFieldException("Unable to find URLClassPath field in the current class loader");
        }
        Java9Fix.setAccessible(ucp);
        URL[] urls = (URL[]) urLs.invoke(ucp.get(Startup.class.getClassLoader()));
        for (URL url:urls) {
            if (!new File(url.getFile()).isFile()) {
                continue;
            }
            try (ZipFile zipFile = new ZipFile(url.getFile())) {
                Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry zipEntry = enumeration.nextElement();
                    if (zipEntry.getName().contains("/")) {
                        //let the auto extracting library do this job by their self
                        continue;
                    }
                    for (String ext : validExt) {
                        if (zipEntry.getName().endsWith(ext)) {
                            try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                                try (OutputStream outputStream = new FileOutputStream(new File(natives, zipEntry.getName()))) {
                                    int nRead;
                                    byte[] data = new byte[16384];

                                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                                        outputStream.write(data, 0, nRead);
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        System.out.println("Setup finished!");
    }
}
