package com.fox2code.udk.plugins;

import com.fox2code.repacker.rebuild.ClassDataProvider;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;

public class CodeFixer implements Opcodes {
    private static PatchRemapper PATCH_REMAPPER = new PatchRemapper();

    public static void patchCode(File dir,File dir2, ClassDataProvider classDataProvider,FixerCfg fixerCfg) {
        int[] counter = new int[]{0};
        RepackerPlugin.delRecursiveSoft(dir2);
        patchDir(dir, dir2, classDataProvider, fixerCfg, counter);
        if (counter[0] == 0) {
            System.out.println("No bytecode compatibility issues detected!");
        } else {
            System.out.println(counter[0]+" bytecode compatibility issues fixed!");
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
                        patchClass(pathname, counter);
                        patchClass2(pathname, new File(dir2, pathname.getName()), classDataProvider, fixerCfg);
                    } catch (IOException e) {
                        System.out.println("Fail to patch " + pathname.getName());
                    }
                }
            }
            return false;
        });
    }

    public static void patchClass(File file,int[] counter) throws IOException {
        boolean[] patched = new boolean[]{false};
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
                            patched[0]=true;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
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

    public static void patchClass2(File in, File out, ClassDataProvider classDataProvider,FixerCfg fixerCfg) throws IOException {
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
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM7, super.visitMethod(access|(INTERNAL.contains(name+descriptor)?ACC_SYNTHETIC:0), name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == INVOKESTATIC && owner.equals("com/fox2code/udk/build/ASM")) {
                            switch (name) {
                                default:
                                    throw new Error("ASM."+name+"Not Implemented!");
                                case "_POP_":
                                    super.visitInsn(POP);
                                    break;
                                case "_POP2_":
                                    super.visitInsn(POP2);
                                    break;
                                case "_ATHROW_":
                                    super.visitInsn(ATHROW);
                                    break;
                                case "_DUP_":
                                    super.visitInsn(DUP);
                                    break;
                                case "_ALOAD_0_":
                                    super.visitVarInsn(ALOAD, 0);
                                    break;
                                case "_ASTORE_0_":
                                    super.visitVarInsn(ASTORE, 0);
                                    break;
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
                                this.visitPtrGet();
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
            Files.copy(in.toPath(), out.toPath());
        }
    }

    public static class FixerCfg {
        public boolean keepFramesByDefault;
        public HashSet<String> strict;
        public ArrayList<String> pkgs;

        public FixerCfg(BasePlugin.BaseConfig config) {
            if (!(keepFramesByDefault = config.keepFramesByDefault) && config.keepFrames != null) {
                strict = new HashSet<>();
                pkgs = new ArrayList<>();
                for (String str:config.keepFrames) {
                    if (str.endsWith(".") || str.endsWith("/")) {
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
