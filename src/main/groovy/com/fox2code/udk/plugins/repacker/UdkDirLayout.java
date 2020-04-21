package com.fox2code.udk.plugins.repacker;

import com.fox2code.repacker.layout.MavenDirLayout;

import java.io.File;

public class UdkDirLayout extends MavenDirLayout {
    public UdkDirLayout(File root) {
        super(root);
    }

    @Override
    public File getMinecraftRepackFile(String version, boolean client) {
        if (client) {
            return new File(root, "net/minecraft/minecraft-remap/" + version + "/minecraft-remap-" + version + ".jar");
        }
        return super.getMinecraftRepackFile(version, false);
    }

    @Override
    public void generateDirsFor(String version) {
        super.generateDirsFor(version);
        new File(root, "net/minecraft/minecraft-remap/" + version).mkdirs();
    }
}
