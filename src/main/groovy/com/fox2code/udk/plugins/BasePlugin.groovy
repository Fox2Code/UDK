package com.fox2code.udk.plugins

import com.fox2code.repacker.Utils
import com.fox2code.repacker.rebuild.ClassDataProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.AbstractCompile

import java.nio.charset.StandardCharsets

class BasePlugin implements Plugin<Project> {
    static final String BUILD_VER = RepackerPlugin.BUILD_VER

    File gradleHome
    File udkCache
    private Project project
    private ClassDataProvider classDataProvider

    ClassDataProvider getClassDataProvider() {
        if (classDataProvider == null) {
            ArrayList<URL> urls = new ArrayList<>();
            project.getConfigurations().getByName("compile").forEach { file ->
                try {
                    urls.add(file.toURI().toURL());
                } catch (MalformedURLException ignored) {}
            }
            classDataProvider = new ClassDataProvider(new URLClassLoader(urls.toArray(URL[]) as URL[]))
        }
        return classDataProvider
    }

    @Override
    void apply(Project project) {
        this.project = project
        gradleHome = project.gradle.getGradleUserHomeDir()
        udkCache = new File(gradleHome, "udk")
        if (!udkCache.exists()) {
            udkCache.mkdirs()
        }
        project.apply([plugin: 'java'])
        project.apply([plugin: 'idea'])
        project.apply([plugin: 'eclipse'])
        project.getRepositories().maven({
            url udkCache.toURI().toString()
        })
        project.getRepositories().mavenCentral()
        project.compileJava {
            sourceCompatibility = 1.8
            targetCompatibility = 1.8
            options.encoding = 'UTF-8'
        }
        project.eclipse {
            classpath {
                downloadSources = true
            }
        }
        project.idea {
            module {
                downloadSources = true
            }
        }
        project.tasks.register("clearCache", Task) {
            group = "UDK"
            description = "Clear Universal Development Kit cache"
        }.get().doFirst {
            RepackerPlugin.delRecursiveSoft(udkCache)
        }
        Jar sourcesJar = project.tasks.register("sourcesJar", Jar) {
            classifier = 'sources'
        }.get() as Jar
        project.tasks.getByName("build").dependsOn(sourcesJar)
        project.extensions.create("udk", BaseConfig)
        project.afterEvaluate {
            BaseConfig config = ((BaseConfig) project.extensions.getByName("udk"))
            CodeFixer.FixerCfg fixerCfg = new CodeFixer.FixerCfg(config)
            project.tasks.findAll().forEach({ task ->
                if (task instanceof AbstractCompile) {
                    final File dest = ((AbstractCompile) task).destinationDir
                    final File dest0 = new File(dest.getParentFile(), dest.getName()+"0")
                    ((AbstractCompile) task).destinationDir = dest0
                    task.doLast {
                        CodeFixer.patchCode(dest0, dest, getClassDataProvider(), fixerCfg)
                    }
                }
            })
            File udkBuild = new File(udkCache, "com/fox2code/udk-build/"+BUILD_VER)
            if (!udkBuild.exists()) {
                udkBuild.mkdirs()
            }
            udkBuild = new File(udkBuild, "udk-build-"+BUILD_VER+".jar")
            if (!udkBuild.exists()) {
                System.out.println("Extracting build v"+BUILD_VER+"...")
                HashMap<String, byte[]> buildZip = new HashMap<>()
                for (String e:[
                        "com/fox2code/udk/build/Internal.class",
                        "com/fox2code/udk/build/ASM.class",
                        "com/fox2code/udk/build/ptr.class"
                ]) {
                    buildZip.put(e, RepackerPlugin.getResource(e))
                }
                buildZip.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8))
                Utils.writeZIP(buildZip, new FileOutputStream(udkBuild))
            }
            project.getDependencies().add("compileOnly", "com.fox2code:udk-build:"+BUILD_VER)
            project.getDependencies().add("testCompileOnly", "com.fox2code:udk-build:"+BUILD_VER)
            project.getDependencies().add("implementation", project.fileTree(dir: 'libs', include: ['*.jar']))
            SourceSet mainSourceSet = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main")
            sourcesJar.from mainSourceSet.allSource
        }
    }

    static class BaseConfig {
        public boolean keepFramesByDefault = true
        public List<String> keepFrames

        void setKeepFrames(String... packages) {
            this.keepFramesByDefault = false
            this.keepFrames = Arrays.asList(packages)
        }

        void setKeepFrames(List<String> packages) {
            this.keepFramesByDefault = false
            this.keepFrames = packages
        }

        void setKeepFrames(boolean keepFramesByDefault) {
            this.keepFramesByDefault = keepFramesByDefault
        }

        void keepFrames(String pkg) {
            if (keepFrames == null) {
                keepFramesByDefault = false
                keepFrames = new ArrayList<>()
            }
            keepFrames.add(pkg)
        }

        void keepFrames(String... pkgs) {
            if (keepFrames == null) {
                keepFramesByDefault = false
                keepFrames = new ArrayList<>()
            }
            keepFrames.addAll(pkgs)
        }

        void keepFrames(Collection<String> pkgs) {
            if (keepFrames == null) {
                keepFramesByDefault = false
                keepFrames = new ArrayList<>()
            }
            keepFrames.addAll(pkgs)
        }
    }
}
