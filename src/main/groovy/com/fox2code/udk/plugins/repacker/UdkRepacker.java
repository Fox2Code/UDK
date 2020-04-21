package com.fox2code.udk.plugins.repacker;

import com.fox2code.repacker.Repacker;
import com.fox2code.repacker.patchers.PostPatcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.List;

public class UdkRepacker extends Repacker {
    public UdkRepacker(File cacheDir) {
        super(new UdkDirLayout(cacheDir));
    }

    @Override
    public void repackClient(String version) throws IOException {
        this.repackClient(version, null);
    }

    @Override
    public void repackClient(String version, PostPatcher postPatcher) throws IOException {
        File repack = super.getClientRemappedFile(version);
        File repackRev = new File(repack.getParentFile(), ".rev");
        if (repack.exists()) {
            int rev = 0;
            if (repackRev.exists()) {
                List<String> lines = Files.readAllLines(repackRev.toPath());
                try {
                    rev = Integer.parseInt(lines.get(0));
                } catch (Exception ignored) {}
            }
            if (rev < super.repackRevision()) {
                repack.delete();
            } else {
                return;
            }
        }
        super.repackClient(version, postPatcher);
        repackRev.createNewFile();
        try {
            Files.setAttribute(repackRev.toPath(), "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
        } catch (Exception ignored) {}
        Files.write(repackRev.toPath(), (super.repackRevision()+"\n").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void repackServer(String version) throws IOException {
        this.repackServer(version, null);
    }

    @Override
    public void repackServer(String version,PostPatcher postPatcher) throws IOException {
        File repack = super.getServerRemappedFile(version);
        File repackRev = new File(repack.getParentFile(), ".rev");
        if (repack.exists()) {
            int rev = 0;
            if (repackRev.exists()) {
                List<String> lines = Files.readAllLines(repackRev.toPath());
                try {
                    rev = Integer.parseInt(lines.get(0));
                } catch (Exception ignored) {}
            }
            if (rev < super.repackRevision()) {
                repack.delete();
            } else {
                return;
            }
        }
        super.repackServer(version, postPatcher);
        repackRev.createNewFile();
        try {
            Files.setAttribute(repackRev.toPath(), "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
        } catch (Exception ignored) {}
        Files.write(repackRev.toPath(), (super.repackRevision()+"\n").getBytes(StandardCharsets.UTF_8));
    }
}
