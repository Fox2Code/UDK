package com.fox2code.udk.plugins

import com.fox2code.repacker.Repacker
import com.fox2code.repacker.Utils
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.google.gson.JsonObject
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.AbstractCompile

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.Normalizer

abstract class RepackerPlugin implements Plugin<Project> {
    private static final String STARTUP_VER = "1.1.0"

    File gradleHome
    File udkCache
    Repacker repacker
    private Project project

    Project getProject() {
        return project
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    @Override
    void apply(Project project) {
        this.project = project
        gradleHome = project.gradle.getGradleUserHomeDir()
        udkCache = new File(gradleHome, "udk")
        repacker = new Repacker(udkCache)
        if (!udkCache.exists()) {
            udkCache.mkdirs()
        }
        if (!project.buildDir.exists()) {
            project.buildDir.mkdirs()
        }
        File gitIgnore = new File(project.buildDir, ".gitignore")
        if (!gitIgnore.exists()) {
            Files.write(gitIgnore.toPath(), "*".getBytes(StandardCharsets.UTF_8))
        }
        project.apply([plugin: 'java'])
        project.apply([plugin: 'idea'])
        project.apply([plugin: 'eclipse'])
        project.getRepositories().maven({
            url udkCache.toURI().toString()
        })
        project.getRepositories().mavenCentral()
        project.getRepositories().maven({
            url "https://jitpack.io"
        })
        project.getRepositories().maven({
            url "https://libraries.minecraft.net"
        })
        project.compileJava {
            sourceCompatibility = 1.8
            targetCompatibility = 1.8
            options.encoding = 'UTF-8'
        }
        project.tasks.register("clearCache", Task) {
            group = "UDK"
            description = "Clear Universal Development Kit cache"
        }.get()
        project.tasks.register("runClient", JavaExec) {
            group = "UDK"
            description = "Run compiled client in gradle"
        }.get().dependsOn(project.getTasks().getByName("classes"))
        project.tasks.register("runClientJar", JavaExec) {
            group = "UDK"
            description = "Run compiled client jar in gradle"
        }.get().dependsOn(project.getTasks().getByName("jar"))
        Jar sourcesJar = project.tasks.register("sourcesJar", Jar) {
            classifier = 'sources'
        }.get()
        project.tasks.getByName("build").dependsOn(sourcesJar)
        project.extensions.create("udk", provideConfig())
        preInject()
        project.afterEvaluate {
            project.tasks.findAll().forEach({ task ->
                if (task instanceof AbstractCompile) {
                    task.doLast {
                        CodeFixer.patchCode(((AbstractCompile) task).destinationDir)
                    }
                }
            })
            Config config = ((Config) project.extensions.getByName("udk"))
            config.username = project.getProperties().getOrDefault("USERNAME", config.username)
            String version = config.version
            if (version == null) {
                throw new GradleException("Unable to find udk.version in the current project!")
            }
            File startup = new File(udkCache, "com/fox2code/udk-startup/"+STARTUP_VER)
            if (!startup.exists()) {
                startup.mkdirs()
            }
            startup = new File(startup, "udk-startup-"+STARTUP_VER+".jar")
            if (!startup.exists()) {
                System.out.println("Extracting startup v"+STARTUP_VER+"...")
                HashMap<String, byte[]> startupZip = new HashMap<>()
                for (String e:[
                        "com/fox2code/udk/startup/Java9Fix.class",
                        "com/fox2code/udk/startup/Startup.class",
                        "com/fox2code/udk/startup/Internal.class"
                ]) {
                    startupZip.put(e, getResource(e))
                }
                startupZip.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8))
                Utils.writeZIP(startupZip, new FileOutputStream(startup))
            }
            if (config.useStartup) {
                project.getDependencies().add("runtime", "com.fox2code:udk-startup:" + STARTUP_VER)
            } else {
                project.getDependencies().add("testRuntimeOnly", "com.fox2code:udk-startup:" + STARTUP_VER)
            }
            project.getDependencies().add("compileOnly", "com.fox2code:udk-startup:"+STARTUP_VER)
            project.getDependencies().add("testCompileOnly", "com.fox2code:udk-startup:"+STARTUP_VER)
            project.getDependencies().add("compileOnly", "org.jetbrains:annotations:18.0.0")
            project.getDependencies().add("testCompileOnly", "org.jetbrains:annotations:18.0.0")
            project.getDependencies().add("implementation", project.fileTree(dir: 'libs', include: ['*.jar']))
            String assetsIndex = repacker.getVersionManifest(config.version).get("assets").asString
            File currentAssetsIndexFile = new File(OSType.OSType.minecraftDir, "assets/indexes/"
                    +repacker.getVersionManifest(config.version).get("assets").asString+".json")
            if (!currentAssetsIndexFile.exists() || currentAssetsIndexFile.size() != repacker.getVersionManifest(config.version).getAsJsonObject("assetIndex").get("size").asLong) {
                System.out.println("Downloading "+assetsIndex+" assets index...")
                File assetsIndexes = currentAssetsIndexFile.getParentFile()
                if (!assetsIndexes.exists()) {
                    assetsIndexes.mkdirs()
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream()
                Utils.download(repacker.getVersionManifest(config.version).getAsJsonObject("assetIndex").get("url").asString, baos)
                byte[] indexData = baos.toByteArray()
                File currentAssetsObjectDir = new File(OSType.OSType.minecraftDir, "assets/objects")
                if (!currentAssetsObjectDir.exists()) {
                    currentAssetsObjectDir.mkdirs()
                }
                JsonObject objects = JsonParser.parseString(new String(indexData)).asJsonObject.getAsJsonObject("objects")
                int max = objects.size()
                int progress = 0
                long lastCheck = System.currentTimeMillis()
                System.out.println("Downloading "+assetsIndex+" assets objects... (0/"+max+")")
                for (Map.Entry<String, JsonElement> entry:objects.entrySet()) {
                    String hash = entry.value.asJsonObject.get("hash").asString
                    File asset = new File(currentAssetsObjectDir, hash.substring(0, 2))
                    if (!asset.exists()) {
                        asset.mkdirs()
                    }
                    asset = new File(asset, hash)
                    if (asset.exists()) {
                        progress++
                        continue
                    }
                    Utils.download("https://resources.download.minecraft.net/" +
                            hash.substring(0, 2) +"/"+hash, new FileOutputStream(asset))
                    progress++
                    if (lastCheck + 1000 < System.currentTimeMillis()) {
                        lastCheck = System.currentTimeMillis()
                        System.out.println("Downloading "+assetsIndex+" assets objects... ("+progress+"/"+max+")")
                    }
                }
                Files.write(currentAssetsIndexFile.toPath(), indexData)
            }
            injectLibraries(config)
            String mainClass = config.main
            if (mainClass == null) {
                mainClass = getDefaultMain()
            }
            String[] mainClassArgs = config.mainArgs
            if (mainClassArgs == null) {
                mainClassArgs = getDefaultMainArgs()
            }
            File udkNativeCache = repacker.getClientRemappedFile(version).getParentFile()
            udkNativeCache = new File(udkNativeCache, OSType.getOSType().gradleExt)
            SourceSet mainSourceSet = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main")
            sourcesJar.from mainSourceSet.allSource
            Task clearCache = project.tasks.getByName("clearCache")
            clearCache.doFirst {
                delRecursiveSoft(udkCache)
            }
            for (int i = 0;i < mainClassArgs.length;i++) {
                switch (mainClassArgs[i]) {
                    default:
                        break
                    case "%run_dir%":
                        mainClassArgs[i] = config.runDir.getAbsolutePath()
                        break
                    case "%assets_dir%":
                        mainClassArgs[i] = new File(OSType.OSType.minecraftDir, "assets").getAbsolutePath()
                        break
                    case "%assets_index%":
                        mainClassArgs[i] = assetsIndex
                        break
                    case "%username%":
                        mainClassArgs[i] = config.username
                        break
                }
            }
            JavaExec runClient = ((JavaExec) project.tasks.getByName("runClient")).args(mainClassArgs)
            runClient.main = config.useStartup ? "com.fox2code.udk.startup.Startup" : mainClass;
            runClient.classpath = mainSourceSet.runtimeClasspath;
            runClient.systemProperty("java.library.path", udkNativeCache.getPath());
            runClient.systemProperty("user.dir", config.runDir.getAbsolutePath())
            if (config.useStartup) {
                runClient.systemProperty("udk.startup.natives", udkNativeCache.getPath())
                runClient.systemProperty("udk.startup.main", mainClass)
            }
            JavaExec runClientJar = ((JavaExec) project.tasks.getByName("runClientJar")).args(mainClassArgs);
            runClientJar.main = config.useStartup ? "com.fox2code.udk.startup.Startup" : mainClass;
            runClientJar.classpath = project.files(((Jar) project.tasks.getByName("jar")).getArchiveFile().get().getAsFile()) + mainSourceSet.runtimeClasspath;
            runClientJar.systemProperty("java.library.path", udkNativeCache.getPath());
            runClientJar.systemProperty("user.dir", config.runDir.getAbsolutePath())
            if (config.useStartup) {
                runClientJar.systemProperty("udk.startup.natives", udkNativeCache.getPath())
                runClientJar.systemProperty("udk.startup.main", mainClass)
            }
        }
    }

    static class Config {
        public String version = null
        public String main = null
        public String username = Normalizer.normalize(System.getProperty("user.name"), Normalizer.Form.NFD).replaceAll("[^a-zA-Z0-9_]+","")
        public String[] mainArgs = null
        public File runDir = new File("run").getAbsoluteFile()
        public boolean server = false
        public boolean useStartup = true

        void setRunDir(File file) {
            this.runDir = file.getAbsoluteFile()
        }

        void setRunDir(String file) {
            this.runDir = new File(file).getAbsoluteFile()
        }
    }

    void preInject() {}

    Class<? extends Config> provideConfig() {
        return Config
    }

    abstract void injectLibraries(Config version)

    @SuppressWarnings("GrMethodMayBeStatic")
    String getDefaultMain() {
        return "net.minecraft.client.main.Main"
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    String[] getDefaultMainArgs() {
        return ["--version", "udk", "--accessToken", "0", "--userProperties", "{}", "--gameDir", "%run_dir%", "--assetsDir", "%assets_dir%", "--assetIndex", "%assets_index%", "--username", "%username%"]
    }

    void injectVersionLibraries(String version) {
        JsonObject manifest = repacker.getVersionManifest(version)
        JsonArray libraries = manifest.getAsJsonArray("libraries")
        for (JsonElement jsonElement : libraries) {
            String name = jsonElement.asJsonObject.get("name").asString
            if (name.startsWith("tv.twitch:twitch-") && name.contains("-platform") && !name.contains("natives")) {
                continue
            }
            project.getDependencies().add("implementation", name)
            if (jsonElement.asJsonObject.has("natives") && (OSType.getOSType() == OSType.MACOS || !name.startsWith("ca.weblite:java-objc-bridge:"))) {
                project.getDependencies().add("implementation", name + ":" + OSType.getOSType().gradleExt)
            }
        }
    }

    void injectRepackVersion(Config config) {
        if (config.server) {
            injectRepackServerVersion(config.version)
        } else {
            injectRepackClientVersion(config.version)
        }
    }

    void injectRepackClientVersion(String version) {
        repacker.repackClient(version)
        project.getDependencies().add("implementation", "net.minecraft:minecraft:" + version + ":remaped")
    }

    void injectRepackServerVersion(String version) {
        repacker.repackServer(version)
        project.getDependencies().add("implementation", "net.minecraft:minecraft:" + version + ":server-remaped")
    }

    private static final FilenameFilter filenameFilter = new FilenameFilter() {
        @Override
        boolean accept(File dir, String name) {
            return !(name == "." || name == "..")
        }
    }

    static void delRecursiveSoft(File file) {
        if (file.isDirectory()) {
            for (File child:file.listFiles(filenameFilter)) {
                delRecursive(child)
            }
        }
    }

    static void delRecursive(File file) {
        if (file.isDirectory()) {
            for (File child:file.listFiles(filenameFilter)) {
                delRecursive(child)
            }
        }
        file.delete();
    }

    static byte[] getResource(String resource) {
        InputStream inputStream = RepackerPlugin.class.getClassLoader().getResourceAsStream(resource)
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()

        int nRead
        byte[] data = new byte[16384]

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            byteArrayOutputStream.write(data, 0, nRead)
        }
        inputStream.close()
        return byteArrayOutputStream.toByteArray()
    }
}