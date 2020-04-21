/*

BSD 3-Clause License

Copyright (c) 2019-2020, Fox2Code
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package com.fox2code.udk.plugins;

import com.fox2code.repacker.rebuild.ClassDataProvider;
import com.fox2code.repacker.utils.ConsoleColors;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;

public class CodeFixer implements Opcodes {
    private static final PatchRemapper PATCH_REMAPPER = new PatchRemapper();

    public static void patchCode(File dir,File dir2, ClassDataProvider classDataProvider,FixerCfg fixerCfg) {
        int[] counter = new int[]{0};
        RepackerPlugin.delRecursiveSoft(dir2);
        patchDir(dir, dir2, classDataProvider, fixerCfg, counter);
        if (counter[0] != 0) {
            System.out.println(ConsoleColors.CYAN+counter[0]+" bytecode compatibility issues fixed!");
        }
    }

    public static void patchDir(File dir,File dir2, ClassDataProvider classDataProvider,FixerCfg fixerCfg,int[] counter) {
        if (!dir2.exists()) {
            dir2.mkdirs();
        }
        dir.listFiles(pathname -> {
            if (pathname.getName().charAt(0) != '.') {
                if (pathname.isDirectory()) {
                    patchDir(pathname, new File(dir2, pathname.getName()), classDataProvider, fixerCfg, counter);
                } else if (pathname.getName().endsWith(".class")) {
                    try {
                        patchClass(pathname, counter, fixerCfg);
                        patchClass2(pathname, new File(dir2, pathname.getName()), classDataProvider, fixerCfg);
                    } catch (IOException|RuntimeException e) {
                        System.out.println(ConsoleColors.RED_BOLD+"Fail to patch " + pathname.getName());
                    }
                }
            }
            return false;
        });
    }

    public static void patchClass(File file,int[] counter,final FixerCfg fixerCfg) throws IOException {
        boolean[] patched = new boolean[]{false};
        final boolean laxCast = fixerCfg.laxCast;
        ClassReader classReader = new ClassReader(new FileInputStream(file));
        ClassNode classNode = null;
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classReader.accept(new ClassVisitor(ASM7, laxCast ? (classNode  = new ClassNode()) : classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM7 ,super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == INVOKEVIRTUAL && matchMethod(name) && matchCL(owner) && descriptor.endsWith("Buffer;") && matchCL(descriptor.substring(descriptor.indexOf(")L")+2, descriptor.length()-1))) {
                            super.visitMethodInsn(INVOKEVIRTUAL, "java/nio/Buffer", name, descriptor.substring(0, descriptor.indexOf(")")+1)+"Ljava/nio/Buffer;", isInterface);
                            super.visitTypeInsn(CHECKCAST, owner);
                            counter[0]++;
                            patched[0]=true;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (laxCast) {
            for (MethodNode methodNode : classNode.methods) {
                AbstractInsnNode abstractInsnNode = methodNode.instructions.getFirst();
                while (abstractInsnNode != null) {
                    if (abstractInsnNode.getOpcode() == CHECKCAST && ownersLaxCast(((TypeInsnNode) abstractInsnNode).desc)) {
                        AbstractInsnNode next = abstractInsnNode.getNext();
                        if (next.getOpcode() == INVOKEVIRTUAL &&
                                ((MethodInsnNode) next).owner.equals(((TypeInsnNode) abstractInsnNode).desc)
                                && namesLaxCast(((MethodInsnNode) next).name)) {
                            ((TypeInsnNode) abstractInsnNode).desc = "java/lang/Number";
                            ((MethodInsnNode) next).owner = "java/lang/Number";
                            patched[0]=true;
                        }
                    }
                    abstractInsnNode = abstractInsnNode.getNext();
                }
            }
            classNode.accept(classWriter);
        }
        if (patched[0]) {
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

    public static void patchClass2(File in, File out, ClassDataProvider classDataProvider,final FixerCfg fixerCfg) throws IOException {
        boolean[] patched = new boolean[]{false};
        ClassReader classReader = new ClassReader(new FileInputStream(in));
        HashSet<String> INTERNAL = new HashSet<>();
        boolean[] keepFrames = new boolean[]{false};
        classReader.accept(new ClassVisitor(ASM7) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                if (!(keepFrames[0] = fixerCfg.keepFrames(name))) {
                    patched[0] = true;
                }
            }

            @Override
            public MethodVisitor visitMethod(int access,final String name,final String mDescriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM7) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.equals("Lcom/fox2code/udk/build/Internal;")) {
                            INTERNAL.add(name+mDescriptor);
                            patched[0]=true;
                        }
                        return null;
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access,final String name,final String fDescriptor, String signature, Object value) {
                return new FieldVisitor(ASM7) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.equals("Lcom/fox2code/udk/build/Internal;")) {
                            INTERNAL.add(name+fDescriptor);
                            patched[0]=true;
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
        ClassWriter classWriter = keepFrames[0] ? classDataProvider.newClassWriter() : new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classReader.accept(new ClassVisitor(ASM7, new ClassRemapper(classWriter, PATCH_REMAPPER)) {
            @Override
            public MethodVisitor visitMethod(int access, String name,final String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM7, super.visitMethod(access|(INTERNAL.contains(name+descriptor)?ACC_SYNTHETIC:0), name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == INVOKESTATIC && owner.equals("com/fox2code/udk/build/ASM")) {
                            switch (name) {
                                default:
                                    throw new Error("ASM."+name+" Not Implemented!");
                                case "_POP_":
                                case "__POP__":
                                    super.visitInsn(POP);
                                    break;
                                case "_POP2_":
                                case "__POP2__":
                                    super.visitInsn(POP2);
                                    break;
                                case "_ATHROW_":
                                case "__ATHROW__":
                                    super.visitInsn(ATHROW);
                                    break;
                                case "_DUP_":
                                case "__DUP__":
                                    super.visitInsn(DUP);
                                    break;
                                case "_DUP_X1_":
                                case "__DUP_X1__":
                                    super.visitInsn(DUP_X1);
                                    break;
                                case "__DUP2__":
                                    super.visitInsn(DUP2);
                                    break;
                                case "__SWAP__":
                                    super.visitInsn(SWAP);
                                    break;
                                case "_ALOAD_0_":
                                    super.visitVarInsn(ALOAD, 0);
                                    break;
                                case "_ASTORE_0_":
                                    super.visitVarInsn(ASTORE, 0);
                                    break;
                                case "_MONITORENTER_":
                                case "__MONITORENTER__":
                                    super.visitInsn(MONITORENTER);
                                    break;
                                case "_MONITOREXIT_":
                                case "__MONITOREXIT__":
                                    super.visitInsn(MONITOREXIT);
                                    break;
                                case "__A":
                                case "__I":
                                case "__J":
                                case "__D":
                                case "__S":
                                case "__B":
                                case "__Z":
                                    break;
                            }
                            patched[0]=true;
                        } else if (opcode == INVOKESTATIC && fixerCfg.inline && (owner.equals("java/lang/Math") || owner.equals("java/lang/StrictMath") || owner.equals("net/minecraft/util/Mth"))) {
                            if (descriptor.indexOf('I') != -1) {
                                switch (name) {
                                    default:
                                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                        return;
                                    case "abs": {
                                        Label label = new Label();
                                        super.visitInsn(DUP);
                                        super.visitJumpInsn(IFGE, label);
                                        super.visitInsn(INEG);
                                        super.visitLabel(label);
                                        break;
                                    }
                                    case "max": {
                                        Label label = new Label();
                                        super.visitInsn(DUP2);
                                        super.visitJumpInsn(IF_ICMPGE, label);
                                        super.visitInsn(SWAP);
                                        super.visitLabel(label);
                                        super.visitInsn(DUP);
                                        break;
                                    }
                                    case "min": {
                                        Label label = new Label();
                                        super.visitInsn(DUP2);
                                        super.visitJumpInsn(IF_ICMPLE, label);
                                        super.visitInsn(SWAP);
                                        super.visitLabel(label);
                                        super.visitInsn(DUP);
                                        break;
                                    }
                                }
                            } else if (descriptor.indexOf('D') != -1) {
                                switch (name) {
                                    default:
                                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                        return;
                                    case "toRadians":
                                        super.visitLdcInsn(180D);
                                        super.visitInsn(DDIV);
                                        super.visitLdcInsn(Math.PI);
                                        super.visitInsn(DMUL);
                                        break;
                                    case "toDegrees":
                                        super.visitLdcInsn(180D);
                                        super.visitInsn(DMUL);
                                        super.visitLdcInsn(Math.PI);
                                        super.visitInsn(DDIV);
                                        break;
                                    case "abs": {
                                        Label label = new Label();
                                        super.visitInsn(DUP2);
                                        super.visitInsn(DCONST_0);
                                        super.visitInsn(DCMPG);
                                        super.visitJumpInsn(IFGE, label);
                                        super.visitInsn(DNEG);
                                        super.visitLabel(label);
                                        break;
                                    }
                                }
                            } else if (descriptor.indexOf('F') != -1) {
                                switch (name) {
                                    default:
                                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                        return;
                                    case "abs": {
                                        Label label = new Label();
                                        super.visitInsn(DUP);
                                        super.visitInsn(FCONST_0);
                                        super.visitInsn(FCMPG);
                                        super.visitJumpInsn(IFGE, label);
                                        super.visitInsn(FNEG);
                                        super.visitLabel(label);
                                        break;
                                    }
                                    case "max": {
                                        Label label = new Label();
                                        super.visitInsn(DUP2);
                                        super.visitInsn(FCMPL);
                                        super.visitJumpInsn(IFGE, label);
                                        super.visitInsn(SWAP);
                                        super.visitLabel(label);
                                        super.visitInsn(POP);
                                        break;
                                    }
                                    case "min": {
                                        Label label = new Label();
                                        super.visitInsn(DUP2);
                                        super.visitInsn(FCMPL);
                                        super.visitJumpInsn(IFLE, label);
                                        super.visitInsn(SWAP);
                                        super.visitLabel(label);
                                        super.visitInsn(POP);
                                        break;
                                    }
                                }
                            }
                            patched[0]=true;
                        } else if (owner.equals("com/fox2code/udk/build/ptr")) {
                            if (opcode == INVOKESPECIAL && name.equals("<init>")) {
                                if (descriptor.equals("()V")) {
                                    super.visitInsn(POP);
                                } else {
                                    this.visitPtrSet();
                                }
                            } else {
                                switch (name) {
                                    default:
                                        throw new Error("ptr."+name+" is not implemented!");
                                    case "from":
                                    case "asArray":
                                        // bye bye opcode
                                        break;
                                    case "valueOf":
                                    case "get":
                                        this.visitPtrGet();
                                        break;
                                    case "clear":
                                        super.visitInsn(ICONST_0);
                                        super.visitInsn(ACONST_NULL);
                                        super.visitInsn(AASTORE);
                                        break;
                                    case "set":
                                        this.visitPtrSet();
                                        break;
                                    case "copy":
                                        this.visitPtrGet();
                                        this.visitPtrNew();
                                        super.visitInsn(DUP_X1);
                                        super.visitInsn(SWAP);
                                        this.visitPtrSet();
                                        break;
                                    case "isPtr":
                                        Label e = new Label();
                                        Label n = new Label();
                                        super.visitInsn(ARRAYLENGTH);
                                        super.visitInsn(ICONST_1);
                                        super.visitJumpInsn(IF_ICMPEQ, e);
                                        super.visitInsn(ICONST_0);
                                        super.visitJumpInsn(GOTO, n);
                                        super.visitLabel(e);
                                        super.visitInsn(ICONST_1);
                                        super.visitLabel(n);
                                        break;
                                }
                            }
                        } else {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        boolean tmp;
                        if (((tmp = (opcode == NEW)) || opcode == INSTANCEOF) && type.equals("com/fox2code/udk/build/ptr")) {
                            if (tmp) {
                                this.visitPtrNew();
                            } else {
                                this.visitPtrInstanceOf();
                            }
                        } else {
                            super.visitTypeInsn(opcode, type);
                        }
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        if (owner.equals("com/fox2code/udk/build/ptr")) {
                            if (opcode == GETFIELD) {
                                if (name.equals("length")) {
                                    super.visitInsn(ARRAYLENGTH);
                                } else {
                                    this.visitPtrGet();
                                }
                            } else if (opcode == PUTFIELD) {
                                this.visitPtrSet();
                            }
                        } else {
                            super.visitFieldInsn(opcode, owner, name, descriptor);
                        }
                    }

                    private void visitPtrNew() {
                        super.visitInsn(ICONST_1);
                        super.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                        patched[0]=true;
                    }

                    private void visitPtrSet() {
                        super.visitInsn(ICONST_0);
                        super.visitInsn(SWAP);
                        super.visitInsn(AASTORE);
                        patched[0]=true;
                    }

                    private void visitPtrGet() {
                        super.visitInsn(ICONST_0);
                        super.visitInsn(AALOAD);
                        patched[0]=true;
                    }

                    private void visitPtrInstanceOf() {
                        Label f = new Label();
                        Label e = new Label();
                        super.visitInsn(DUP);
                        super.visitTypeInsn(INSTANCEOF, "[Ljava/lang/Object;");
                        super.visitJumpInsn(IFEQ, f);
                        super.visitInsn(ARRAYLENGTH);
                        super.visitInsn(ICONST_1);
                        super.visitInsn(DUP_X1);
                        super.visitJumpInsn(IF_ICMPEQ, e);
                        super.visitLabel(f);
                        super.visitInsn(POP);
                        super.visitInsn(ICONST_0);
                        super.visitLabel(e);
                        patched[0]=true;
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (descriptor.equals("Lcom/fox2code/udk/build/ptr;")) {
                    descriptor = "[Ljava/lang/Object;";
                    if (signature != null) {
                        signature = signature.replace("Lcom/fox2code/udk/build/ptr<", "[Ljava/lang/Object<");
                    }
                    patched[0]=true;
                }
                return super.visitField(access|(INTERNAL.contains(name+descriptor)?ACC_SYNTHETIC:0), name, descriptor, signature, value);
            }
        }, ClassReader.SKIP_FRAMES);
        if (patched[0]) {
            Files.write(out.toPath(), classWriter.toByteArray());
        } else {
            Files.copy(in.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean ownersLaxCast(String string) {
        switch (string) {
            default:
                return false;
            case "java/lang/Byte":
            case "java/lang/Double":
            case "java/lang/Float":
            case "java/lang/Integer":
            case "java/lang/Long":
            case "java/lang/Short":
            case "java/math/BigInteger":
            case "java/math/BigDecimal":
                return true;
        }
    }

    private static boolean namesLaxCast(String string) {
        switch (string) {
            default:
                return false;
            case "byteValue":
            case "doubleValue":
            case "floatValue":
            case "intValue":
            case "longValue":
            case "shortValue":
                return true;
        }
    }

    public static class FixerCfg {
        public boolean inline;
        public boolean laxCast;
        public boolean keepFramesByDefault;
        public HashSet<String> strict;
        public ArrayList<String> pkgs;

        public FixerCfg(BasePlugin.BaseConfig config) {
            this.inline = config.inline;
            this.laxCast = config.laxCast;
            if (!(keepFramesByDefault = config.keepFramesByDefault) && config.keepFrames != null) {
                strict = new HashSet<>();
                pkgs = new ArrayList<>();
                for (String str:config.keepFrames) {
                    str = str.replace('.', '/');
                    if (str.endsWith("/")) {
                        pkgs.add(str);
                    } else {
                        strict.add(str);
                    }
                }
            }
        }

        boolean keepFrames(String name) {
            if (strict == null) {
                return keepFramesByDefault;
            }
            if (strict.contains(name)) {
                return true;
            }
            for (String pkg:pkgs) {
                if (name.startsWith(pkg)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class PatchRemapper extends Remapper {
        @Override
        public String mapType(String internalName) {
            return super.map(internalName);
        }

        @Override
        public String mapDesc(String descriptor) {
            return super.mapDesc(descriptor.replace("Lcom/fox2code/udk/build/ptr;", "[Ljava/lang/Object;"));
        }

        @Override
        public String map(String internalName) {
            switch (internalName) {
                default:
                    return super.map(internalName);
                case "Lcom/fox2code/udk/build/ptr;":
                    return "[Ljava/lang/Object;";
                case "Lcom/fox2code/udk/build/ASM;":
                    return "Ljava/lang/Object;";
                case "com/fox2code/udk/build/ptr":
                    throw new Error("Please report this to the udk maintainer with the stacktrace");
            }
        }
    }
}
