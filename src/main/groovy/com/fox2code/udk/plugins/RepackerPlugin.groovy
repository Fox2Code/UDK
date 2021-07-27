package com.fox2code.udk.plugins

import com.fox2code.repacker.utils.ConsoleColors
import com.fox2code.repacker.utils.Utils
import com.fox2code.repacker.rebuild.ClassDataProvider
import com.fox2code.udk.plugins.repacker.UdkRepacker
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
import org.gradle.api.tasks.compile.JavaCompile

import javax.swing.JOptionPane
import javax.swing.UIManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.text.Normalizer

abstract class RepackerPlugin implements Plugin<Project> {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create()

    static final String STARTUP_VER = "1.2.1"
    static final String BUILD_VER = "1.1.0"

    static UdkRepacker repacker = null

    File gradleHome
    File udkCache
    File udkDataFile
    private Project project
    private ClassDataProvider classDataProvider

    Project getProject() {
        return project
    }

    ClassDataProvider getClassDataProvider() {
        if (classDataProvider == null) {
            SourceSet mainSourceSet = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main")
            final ArrayList<URL> urls = new ArrayList<>()
            mainSourceSet.getCompileClasspath().forEach { file ->
                try {
                    urls.add(file.toURI().toURL());
                } catch (MalformedURLException ignored) {}
            }
            URLClassLoader urlClassLoader
            try {
                urlClassLoader = new URLClassLoader(urls.toArray(URL[]) as URL[])
            } catch (NullPointerException ignored) {
                throw new Error("Corrupted JDK")
            }
            classDataProvider = new ClassDataProvider(urlClassLoader)
        }
        return classDataProvider
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    @Override
    void apply(Project project) {
        if (this.project != null && this.project != project) {
            this.getClass().newInstance().apply(project)
            return
        }
        this.project = project
        gradleHome = project.gradle.getGradleUserHomeDir()
        udkCache = new File(gradleHome, "udk")
        udkDataFile = new File(gradleHome, "udk.json")
        if (repacker == null) {
            repacker = new UdkRepacker(udkCache)
        }
        if (!udkCache.exists()) {
            udkCache.mkdirs()
        }
        JsonObject udkData = readData()
        // CHECK VALIDITY OF THE CONFIG
        try {
            JsonElement tmp = udkData.get("username")
            if (tmp != null) tmp.asString
        } catch (Throwable ignored) {
            saveData(udkData = new JsonObject())
        }
        if (!project.buildDir.exists()) {
            project.buildDir.mkdirs()
        }
        File gitIgnore = new File(project.buildDir, ".gitignore")
        if (!gitIgnore.exists()) {
            Files.write(gitIgnore.toPath(), "*".getBytes(StandardCharsets.UTF_8))
        }
        File runDir = new File(project.projectDir, "run").getAbsoluteFile()
        if (runDir.exists()) {
            File gitIgnoreRun = new File(runDir, ".gitignore")
            if (!gitIgnoreRun.exists()) {
                Files.write(gitIgnoreRun.toPath(), "*".getBytes(StandardCharsets.UTF_8))
            }
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
            sourceCompatibility = 16
            targetCompatibility = 16
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
        }.get()
        project.tasks.register("runClient", JavaExec) {
            group = "UDK"
            description = "Run compiled client in gradle"
        }.get().dependsOn(project.getTasks().getByName("classes"))
        project.tasks.register("runClientJar", JavaExec) {
            group = "UDK"
            description = "Run compiled client jar in gradle"
        }.get().dependsOn(project.getTasks().getByName("jar"))
        project.tasks.register("runServer", JavaExec) {
            group = "UDK"
            description = "Run compiled server in gradle"
        }.get().dependsOn(project.getTasks().getByName("classes"))
        project.tasks.register("runServerJar", JavaExec) {
            group = "UDK"
            description = "Run compiled server jar in gradle"
        }.get().dependsOn(project.getTasks().getByName("jar"))
        Jar sourcesJar = project.tasks.register("sourcesJar", Jar) {
            classifier = 'sources'
        }.get() as Jar
        project.tasks.register("defaultUsername", Task) {
            group = "UDK"
            description = "Change the default username used on UDK"
        }.get().doLast {
            JsonElement jsonElement = udkData.get("username")
            String username
            if (jsonElement == null) {
                username = Normalizer.normalize(System.getProperty("user.name"), Normalizer.Form.NFD).replaceAll("[^a-zA-Z0-9_]+","")
            } else {
                username = jsonElement.asString
            }
            try {
                UIManager.setLookAndFeel(UIManager.systemLookAndFeelClassName)
            } catch (Throwable ignored) {}
            username = JOptionPane.showInputDialog("Default username = ???", username)
            if (username == null || username.isEmpty()) {
                udkData.remove("username")
            } else {
                udkData.addProperty("username", username)
            }
            saveData(udkData)
        }
        project.tasks.getByName("build").dependsOn(sourcesJar)
        project.extensions.create("udk", provideConfig())
        preInject()
        project.afterEvaluate {
            Config config = ((Config) project.extensions.getByName("udk"))
            String sourceCompat = (project.tasks.getByName("compileJava") as JavaCompile).sourceCompatibility
            CodeFixer.FixerCfg fixerCfg = new CodeFixer.FixerCfg(config,
                    sourceCompat == null || sourceCompat.startsWith("1."))
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
            if (config.runDir == null) {
                config.runDir = new File(project.projectDir, "run").getAbsoluteFile()
            }
            JsonElement savedUsername = udkData.get("username")
            if (savedUsername != null) {
                config.username = savedUsername.asString
            }
            config.username = project.getProperties().getOrDefault("USERNAME", config.username)
            String version = config.version
            if (version == null) {
                throw new GradleException("Unable to find udk.version in the current project!")
            }
            File startup = new File(udkCache, "com/fox2code/udk-startup/"+STARTUP_VER)
            if (!startup.exists()) {
                startup.mkdirs()
            }
            File startupPom = new File(startup, "udk-startup-"+STARTUP_VER+".pom")
            startup = new File(startup, "udk-startup-"+STARTUP_VER+".jar")
            if (!startup.exists()) {
                System.out.println(ConsoleColors.YELLOW_BRIGHT + "Extracting startup v"+STARTUP_VER+"..." + ConsoleColors.RESET)
                HashMap<String, byte[]> startupZip = new HashMap<>()
                for (String e:[
                        "com/fox2code/udk/startup/Java9Fix.class",
                        "com/fox2code/udk/startup/Startup.class"
                ]) {
                    startupZip.put(e, getResource(e))
                }
                startupZip.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8))
                Utils.writeZIP(startupZip, new FileOutputStream(startup))
            }
            if (!startupPom.exists()) {
                injectPom(startupPom, "com.fox2code","udk-startup", STARTUP_VER)
            }
            File udkBuild = new File(udkCache, "com/fox2code/udk-build/"+BUILD_VER)
            if (!udkBuild.exists()) {
                udkBuild.mkdirs()
            }
            File udkBuildPom = new File(udkBuild, "udk-build-"+BUILD_VER+".pom")
            udkBuild = new File(udkBuild, "udk-build-"+BUILD_VER+".jar")
            if (!udkBuild.exists()) {
                System.out.println(ConsoleColors.YELLOW_BRIGHT + "Extracting build v"+BUILD_VER+"..." + ConsoleColors.RESET)
                HashMap<String, byte[]> buildZip = new HashMap<>()
                for (String e:[
                        "com/fox2code/udk/build/Internal.class",
                        "com/fox2code/udk/build/ASM.class",
                        "com/fox2code/udk/build/ptr.class"
                ]) {
                    buildZip.put(e, getResource(e))
                }
                buildZip.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8))
                Utils.writeZIP(buildZip, new FileOutputStream(udkBuild))
            }
            if (!udkBuildPom.exists()) {
                injectPom(udkBuildPom, "com.fox2code","udk-build", BUILD_VER)
            }
            if (config.useStartup) {
                project.getDependencies().add("api", "com.fox2code:udk-startup:" + STARTUP_VER)
            }
            project.getDependencies().add("compileOnly", "com.fox2code:udk-startup:"+STARTUP_VER)
            project.getDependencies().add("testCompileOnly", "com.fox2code:udk-startup:"+STARTUP_VER)
            project.getDependencies().add("compileOnly", "com.fox2code:udk-build:"+BUILD_VER)
            project.getDependencies().add("testCompileOnly", "com.fox2code:udk-build:"+BUILD_VER)
            project.getDependencies().add("compileOnly", "org.jetbrains:annotations:13.0")
            project.getDependencies().add("testCompileOnly", "org.jetbrains:annotations:13.0")
            project.getDependencies().add("compileOnly", "javax.annotation:javax.annotation-api:1.3.2")
            project.getDependencies().add("testCompileOnly", "javax.annotation:javax.annotation-api:1.3.2")
            project.getDependencies().add("implementation", project.fileTree(dir: 'libs', include: ['*.jar']))
            String assetsIndex = repacker.getVersionManifest(config.version).get("assets").asString
            File currentAssetsIndexFile = new File(OSType.OSType.minecraftDir, "assets/indexes/" + repacker.getVersionManifest(config.version).get("assets").asString+".json")
            JsonObject objects;
            File currentAssetsObjectDir = new File(OSType.OSType.minecraftDir, "assets/objects")
            if (!currentAssetsIndexFile.exists() || currentAssetsIndexFile.size() != repacker.getVersionManifest(config.version).getAsJsonObject("assetIndex").get("size").asLong) {
                System.out.println(ConsoleColors.YELLOW_BRIGHT + "Downloading " + assetsIndex + " assets index..." + ConsoleColors.RESET)
                File assetsIndexes = currentAssetsIndexFile.getParentFile()
                if (!assetsIndexes.exists()) {
                    assetsIndexes.mkdirs()
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream()
                Utils.download(repacker.getVersionManifest(config.version).getAsJsonObject("assetIndex").get("url").asString, baos)
                byte[] indexData = baos.toByteArray()
                if (!currentAssetsObjectDir.exists()) {
                    currentAssetsObjectDir.mkdirs()
                }
                Files.write(currentAssetsIndexFile.toPath(), indexData)
                objects = JsonParser.parseString(new String(indexData)).asJsonObject.getAsJsonObject("objects")
            } else {
                objects = JsonParser.parseString(new String(Files.readAllBytes(currentAssetsIndexFile.toPath()))).asJsonObject.getAsJsonObject("objects")
                for (String item:["icons/icon_16x16.png","icons/icon_32x32.png", "icons/minecraft.icns",
                                  "minecraft/icons/icon_16x16.png", "minecraft/icons/icon_32x32.png",
                                  "minecraft/icons/minecraft.icns",
                                  "minecraft/textures/gui/title/background/panorama_0.png",
                                  "minecraft/textures/gui/title/background/panorama_1.png",
                                  "minecraft/textures/gui/title/background/panorama_2.png",
                                  "minecraft/textures/gui/title/background/panorama_3.png",
                                  "minecraft/textures/gui/title/background/panorama_4.png",
                                  "minecraft/textures/gui/title/background/panorama_5.png",
                                  "minecraft/texts/splashes.txt",
                                  null]) {
                    if (item == null) {
                        objects = null
                        break
                    }
                    JsonElement tmp = objects.get(item)
                    if (tmp == null) {
                        continue
                    }
                    JsonObject obj = tmp.asJsonObject
                    if (obj == null) {
                        continue
                    }
                    String hash = obj.get("hash").asString
                    File asset = new File(currentAssetsObjectDir, hash.substring(0, 2) + File.separator + hash)
                    if (!asset.exists()) {
                        break
                    }
                }
            }
            if (objects != null) {
                int max = objects.size()
                int progress = 0
                long lastCheck = System.currentTimeMillis()
                System.out.println(ConsoleColors.YELLOW_BRIGHT +
                        "Downloading "+assetsIndex+" assets objects... (0/"+max+")" +
                        ConsoleColors.RESET)
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
                        System.out.println(ConsoleColors.YELLOW_BRIGHT +
                                "Downloading "+assetsIndex+" assets objects... ("+progress+"/"+max+")" +
                                ConsoleColors.RESET)
                    }
                }
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
            String mainClassServer = config.mainServer
            if (mainClassServer == null) {
                mainClassServer = getServerMain()
            }
            String[] mainClassArgsServer = config.mainArgsServer
            if (mainClassArgsServer == null) {
                mainClassArgsServer = getServerMainArgs()
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
                mainClassArgs[i] = mainClassArgs[i]
                        .replace("%run_dir%", config.runDir.getAbsolutePath())
                        .replace("%assets_dir%", new File(OSType.OSType.minecraftDir, "assets").getAbsolutePath())
                        .replace("%assets_index%", assetsIndex)
                        .replace("%username%", config.username)
                        .replace("%mc_ver%", config.version)
            }
            for (int i = 0;i < mainClassArgsServer.length;i++) {
                mainClassArgsServer[i] = mainClassArgsServer[i]
                        .replace("%run_dir%", config.runDir.getAbsolutePath())
                        .replace("%assets_dir%", new File(OSType.OSType.minecraftDir, "assets").getAbsolutePath())
                        .replace("%assets_index%", assetsIndex)
                        .replace("%username%", config.username)
                        .replace("%mc_ver%", config.version)
            }
            // CLIENT LAUNCH
            JavaExec runClient = ((JavaExec) project.tasks.getByName("runClient")).args(mainClassArgs)
            runClient.doFirst {
                config.runDir.mkdirs()
            }
            runClient.workingDir(config.runDir)
            runClient.main = config.useStartup ? "com.fox2code.udk.startup.Startup" : mainClass;
            runClient.classpath = mainSourceSet.runtimeClasspath;
            runClient.systemProperty("java.library.path", udkNativeCache.getPath());
            runClient.systemProperty("user.dir", config.runDir.getAbsolutePath())
            if (config.useStartup) {
                runClient.systemProperty("udk.startup.natives", udkNativeCache.getPath())
                runClient.systemProperty("udk.startup.main", mainClass)
            }
            JavaExec runClientJar = ((JavaExec) project.tasks.getByName("runClientJar")).args(mainClassArgs);
            runClientJar.doFirst {
                config.runDir.mkdirs()
            }
            runClientJar.workingDir(config.runDir)
            runClientJar.main = config.useStartup ? "com.fox2code.udk.startup.Startup" : mainClass;
            runClientJar.classpath = project.files(((Jar) project.tasks.getByName("jar")).getArchiveFile().get().getAsFile()) + mainSourceSet.runtimeClasspath;
            runClientJar.systemProperty("java.library.path", udkNativeCache.getPath());
            runClientJar.systemProperty("user.dir", config.runDir.getAbsolutePath())
            if (config.useStartup) {
                runClientJar.systemProperty("udk.startup.natives", udkNativeCache.getPath())
                runClientJar.systemProperty("udk.startup.main", mainClass)
            }
            // SERVER LAUNCH
            JavaExec runServer = ((JavaExec) project.tasks.getByName("runServer")).args(mainClassArgsServer)
            runServer.doFirst {
                config.runDir.mkdirs()
            }
            runServer.workingDir(config.runDir)
            runServer.doFirst {
                repacker.repackServer(config.version)
            }
            runServer.main = config.useStartup ? "com.fox2code.udk.startup.Startup" : mainClassServer;
            runServer.classpath = mainSourceSet.runtimeClasspath;
            if (config.nativeServer) {
                runServer.systemProperty("java.library.path", udkNativeCache.getPath());
            }
            runServer.systemProperty("user.dir", config.runDir.getAbsolutePath())
            if (config.useStartup) {
                if (config.nativeServer) {
                    runServer.systemProperty("udk.startup.natives", udkNativeCache.getPath())
                }
                runServer.systemProperty("udk.startup.main", mainClassServer)
            }
            JavaExec runServerJar = ((JavaExec) project.tasks.getByName("runServerJar")).args(mainClassArgsServer);
            runServerJar.doFirst {
                config.runDir.mkdirs()
            }
            runServerJar.workingDir(config.runDir)
            runServerJar.doFirst {
                repacker.repackServer(config.version)
            }
            runServerJar.main = config.useStartup ? "com.fox2code.udk.startup.Startup" : mainClassServer;
            runServerJar.classpath = project.files(((Jar) project.tasks.getByName("jar")).getArchiveFile().get().getAsFile()) + mainSourceSet.runtimeClasspath;
            if (config.nativeServer) {
                runServerJar.systemProperty("java.library.path", udkNativeCache.getPath());
            }
            runServerJar.systemProperty("user.dir", config.runDir.getAbsolutePath())
            if (config.useStartup) {
                if (config.nativeServer) {
                    runServerJar.systemProperty("udk.startup.natives", udkNativeCache.getPath())
                }
                runServerJar.systemProperty("udk.startup.main", mainClassServer)
            }
            postInject(config)
        }
    }

    static class Config extends BasePlugin.BaseConfig {
        public String version = null
        public String main = null
        public String mainServer = null
        public String username = Normalizer.normalize(System.getProperty("user.name"), Normalizer.Form.NFD).replaceAll("[^a-zA-Z0-9_]+","")
        public String[] mainArgs = null
        public String[] mainArgsServer = null
        public File runDir = null
        public boolean server = false
        public boolean useStartup = true
        public boolean nativeServer = false
        public boolean open = false

        void setRunDir(File file) {
            this.runDir = file.getAbsoluteFile()
        }

        void setRunDir(String file) {
            this.runDir = new File(file).getAbsoluteFile()
        }
    }

    void preInject() {}

    void postInject(Config config) {}

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
        return ["--version", "%mc_ver%-udk", "--accessToken", "0", "--userProperties", "{}", "--gameDir", "%run_dir%", "--assetsDir", "%assets_dir%", "--assetIndex", "%assets_index%", "--username", "%username%"]
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    String getServerMain() {
        return "net.minecraft.server.dedicated.DedicatedServer"
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    String[] getServerMainArgs() {
        return ["nogui"]
    }

    void injectVersionLibraries(String version) {
        JsonObject manifest = repacker.getVersionManifest(version)
        JsonArray libraries = manifest.getAsJsonArray("libraries")

        System.out.println(ConsoleColors.CYAN_BOLD + "Injecting library")
        int check = 0;
        for (JsonElement jsonElement : libraries) {
            String name = jsonElement.asJsonObject.get("name").asString
            if (name.startsWith("tv.twitch:twitch-") && name.contains("-platform") && !name.contains("natives")) {
                continue
            }
            project.getDependencies().add("implementation", name)
            if (jsonElement.asJsonObject.has("natives") && (OSType.getOSType() == OSType.MACOS || !name.startsWith("ca.weblite:java-objc-bridge:"))) {
                project.getDependencies().add("implementation", name + ":" + OSType.getOSType().gradleExt)
            }
            check++
            System.out.println(" " + ConsoleColors.CYAN + name + " Ok")
        }

        if (check == 0) {
            System.out.println(ConsoleColors.RED + "Anomaly detected," + ConsoleColors.CYAN_BOLD + " 0 library" + ConsoleColors.CYAN + " have been loaded but the server say " + ConsoleColors.CYAN_BOLD + libraries.size() + ConsoleColors.CYAN + " remaining.")
        } else {
            System.out.println(ConsoleColors.GREEN + "All " + ConsoleColors.CYAN + "library has been loaded.")
        }
    }

    void injectRepackVersion(Config config) {
        if (config.server) {
            injectRepackServerVersion(config.version)
        } else {
            injectRepackClientVersion(config.version, config.open)
        }
    }

    void injectRepackClientVersion(String version,final boolean open) {
        repacker.repackClient(version)
        if (open) {
            File remap = repacker.getClientRemappedFile(version)
            File revOpen = new File(udkCache, "net/minecraft/minecraft-open-remap/" + version + "/.rev")
            File openJar = new File(udkCache, "net/minecraft/minecraft-open-remap/" + version + "/minecraft-open-remap-" + version + ".jar")
            if (openJar.exists()) {
                int rev = 0
                if (revOpen.exists()) {
                    List<String> lines = Files.readAllLines(revOpen.toPath())
                    try {
                        rev = Integer.parseInt(lines.get(0))
                    } catch (Exception ignored) {}
                }
                if (rev < repacker.repackRevision()) {
                    revOpen.delete()
                } else {
                    project.getDependencies().add("implementation", "net.minecraft:minecraft-open-remap:" + version)
                    return
                }
            }
            OpenMC.openIfNeeded(remap, openJar)
            revOpen.createNewFile()
            try {
                Files.setAttribute(revOpen.toPath(), "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS)
            } catch (Exception ignored) {}
            Files.write(revOpen.toPath(), (repacker.repackRevision()+"\n").getBytes(StandardCharsets.UTF_8))
        /*}
        [...] <= Comment because decompiler code need to be here
        if (open) {*/
            project.getDependencies().add("implementation", "net.minecraft:minecraft-open-remap:" + version)
        } else {
            project.getDependencies().add("implementation", "net.minecraft:minecraft-remap:" + version)
        }
    }

    void injectRepackServerVersion(String version) {
        repacker.repackServer(version)
        project.getDependencies().add("implementation", "net.minecraft:minecraft:" + version + ":server-remaped")
    }

    static void injectPom(File file,String group,String id,String ver) {
        Files.write(file.toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\" xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>"+group+"</groupId>\n" +
                "    <artifactId>"+id+"</artifactId>\n" +
                "    <version>"+ver+"</version>\n" +
                "    <packaging>jar</packaging>\n" +
                "    <dependencies>\n" +
                "    </dependencies>\n" +
                "</project>").getBytes(StandardCharsets.UTF_8))
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

    JsonObject readData() {
        if (udkDataFile.exists()) try {
            return JsonParser.parseString(new String(Files.readAllBytes(udkDataFile.toPath()), StandardCharsets.UTF_8)) as JsonObject
        } catch (Throwable ignored) {}
        return new JsonObject()
    }

    void saveData(JsonObject jsonObject) {
        Files.write(udkDataFile.toPath(),gson.toJson(jsonObject).getBytes(StandardCharsets.UTF_8))
    }
}