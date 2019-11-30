package com.fox2code.udk.plugins;

import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.Files;

public class CodeFixer implements Opcodes {

    public static void patchCode(File dir) {
        int[] counter = new int[]{0};
        patchDir(dir, counter);
        if (counter[0] == 0) {
            System.out.println("No bytecode compatibility issues detected!");
        } else {
            System.out.println(counter[0]+" bytecode compatibility issues fixed!");
        }
    }

    public static void patchDir(File dir,int[] counter) {
        dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.isDirectory()) {
                    patchDir(pathname, counter);
                } else if (pathname.getName().endsWith(".class")) {
                    try {
                        patchClass(pathname, counter);
                    } catch (IOException e) {
                        System.out.println("Fail to patch "+pathname.getName());
                    }
                }
                return false;
            }
        });
    }

    public static void patchClass(File file,int[] counter) throws IOException {
        int pre_counter = counter[0];
        ClassReader classReader = new ClassReader(new FileInputStream(file));
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
        classReader.accept(new ClassVisitor(ASM7, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM7 ,super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == INVOKEVIRTUAL && matchMethod(name) && matchCL(owner) && descriptor.endsWith("Buffer;") && matchCL(descriptor.substring(descriptor.indexOf(")L")+2, descriptor.length()-1))) {
                            super.visitMethodInsn(INVOKEVIRTUAL, "java/nio/Buffer", name, descriptor.substring(0, descriptor.indexOf(")")+1)+"Ljava/nio/Buffer;", isInterface);
                            super.visitTypeInsn(CHECKCAST, owner);
                            counter[0]++;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (pre_counter != counter[0]) {
            Files.write(file.toPath(), classWriter.toByteArray());
        }
    }

    static boolean matchCL(String cl) {
        return cl.length() > 15 && cl.startsWith("java/nio/") && cl.endsWith("Buffer");
    }

    static boolean matchMethod(String name) {
        switch (name) {
            case "mark":
            case "swap":
            case "flip":
            case "clear":
            case "limit":
            case "rewind":
            case "position":
                return true;
            default:
                return false;
        }
    }
}
