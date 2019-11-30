package com.fox2code.udk.plugins;

import java.io.File;

public enum OSType {
    WINDOWS("natives-windows", "windows") {
        @Override
        public boolean matchName(String name) {
            return name.endsWith(".dll");
        }

        @Override
        public File getMinecraftDir() {
            return new File(System.getenv("APPDATA") + "\\.minecraft");
        }
    },
    MACOS("natives-macos", "os") {
        @Override
        public boolean matchName(String name) {
            return name.endsWith(".dylib") || name.endsWith(".jnilib");
        }

        @Override
        public File getMinecraftDir() {
            return new File(System.getProperty("user.home") + "/Library/Application Support/minecraft");
        }
    },
    LINUX("natives-linux", "linux") {
        @Override
        public boolean matchName(String name) {
            return name.endsWith(".so");
        }

        @Override
        public File getMinecraftDir() {
            return new File(System.getProperty("user.home") + "/.minecraft");
        }
    };

    private final String gradleExt, nativeExt;

    OSType(String gradleExt, String nativeExt) {
        this.gradleExt = gradleExt;

        this.nativeExt = nativeExt;
    }

    public String getGradleExt() {
        return gradleExt;
    }

    public String getNativeExt() {
        return nativeExt;
    }

    public abstract boolean matchName(String name);

    public abstract File getMinecraftDir();

    private static final OSType OS_TYPE = getOSType0();

    private static OSType getOSType0() {
        String os_name = System.getProperty("os.name").toLowerCase();
        if (os_name.contains("win")) {
            return WINDOWS;
        } else if (os_name.contains("mac")) {
            return MACOS;
        } else if (os_name.contains("nux")) {
            return LINUX;
        } else {
            throw new Error("Invalid System: \""+os_name+"\"");
        }
    }

    public static OSType getOSType() {
        return OS_TYPE;
    }
}
