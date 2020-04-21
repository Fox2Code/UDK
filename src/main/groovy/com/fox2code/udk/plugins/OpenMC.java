package com.fox2code.udk.plugins;

import com.fox2code.repacker.utils.ConsoleColors;
import com.fox2code.repacker.utils.Utils;
import org.objectweb.asm.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;

public class OpenMC implements Opcodes {
    private static final int MASK = ~(ACC_PROTECTED|ACC_PRIVATE|ACC_SYNTHETIC|ACC_FINAL);
    private static final int MASK2 = ~(ACC_PROTECTED|ACC_PRIVATE|ACC_FINAL);

    public static void open(File from, File to) throws IOException {
        System.out.println(ConsoleColors.YELLOW_BRIGHT + "Opening client jar...");
        Map<String, byte[]> zip = Utils.readZIP(new FileInputStream(from));
        for (Map.Entry<String, byte[]> entry:zip.entrySet()) {
            if (entry.getKey().startsWith("net/minecraft/") && entry.getKey().endsWith(".class")) {
                ClassWriter classWriter = new ClassWriter(0);
                ClassReader classReader = new ClassReader(entry.getValue());
                classReader.accept(new ClassVisitor(ASM7, classWriter) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        super.visit(version, (access&MASK)|ACC_PUBLIC, name, signature, superName, interfaces);
                    }

                    @Override
                    public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        if (outerName == null && innerName == null) {
                            return;
                        }
                        super.visitInnerClass(name, outerName, innerName, access);
                    }

                    @Override
                    public void visitOuterClass(String owner, String name, String descriptor) {}

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return super.visitMethod((access& ACC_BRIDGE) != 0 ? (access&MASK2)|ACC_PUBLIC : (access&MASK)|ACC_PUBLIC, name, descriptor, signature, exceptions);
                    }

                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        return super.visitField((access&MASK)|ACC_PUBLIC, name, descriptor, signature, value);
                    }
                }, 0);
                entry.setValue(classWriter.toByteArray());
            }
        }
        to.getParentFile().mkdirs();
        Utils.writeZIP(zip, new FileOutputStream(to));
    }

    public static void openIfNeeded(File from, File to) throws IOException {
        File revFrom = new File(from.getParent(), ".rev");
        File revTo = new File(to.getParent(), ".rev");
        boolean need = !to.exists() || !revTo.exists() || revFrom.length() != to.length() ||
                !Arrays.equals(Files.readAllBytes(revFrom.toPath()),
                        Files.readAllBytes(revTo.toPath()));
        if (need) {
            open(from, to);
            Files.copy(revFrom.toPath(), revTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.setAttribute(revTo.toPath(), "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
            } catch (Exception ignored) {}
        }
    }
}
