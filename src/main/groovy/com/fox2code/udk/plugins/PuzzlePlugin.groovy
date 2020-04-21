package com.fox2code.udk.plugins

import com.fox2code.repacker.utils.Utils
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec

import java.nio.charset.StandardCharsets

class PuzzlePlugin extends RepackerPlugin {
    @Override
    void preInject() {
        project.getRepositories().maven({
            url "https://repo.spongepowered.org/maven"
        })
        project.getRepositories().maven({
            url "http://62.4.29.69/maven"
        })
    }

    @Override
    void injectLibraries(Config config) {
        PuzzleConfig puzzleConfig = (PuzzleConfig) config
        if (puzzleConfig.modVersion == null) {
            puzzleConfig.modVersion = project.version
        }
        Objects.requireNonNull(puzzleConfig.puzzleVersion, "Require puzzleVersion!")
        Objects.requireNonNull(puzzleConfig.modMain, "Require modMain!")
        Objects.requireNonNull(puzzleConfig.modID, "Require modID!")
        Objects.requireNonNull(puzzleConfig.modVersion, "Require modVersion!")
        Objects.requireNonNull(puzzleConfig.modName, "Require modName!")
        injectRepackVersion(config)
        injectVersionLibraries(config.version)
        project.getDependencies().add("implementation", "net.puzzle-mod-loader:puzzle-mod-loader:"+puzzleConfig.puzzleVersion)
        project.tasks.getByName("jar") {
            manifest {
                attributes 'ModMain': puzzleConfig.modMain
                attributes 'ModId': puzzleConfig.modID
                attributes 'ModVersion': puzzleConfig.modVersion
                attributes 'ModName': puzzleConfig.modName
                if (puzzleConfig.modHook != null) {
                    attributes 'ModHook': puzzleConfig.modHook
                }
                if (puzzleConfig.modDesc != null) {
                    attributes 'ModDesc': puzzleConfig.modDesc
                }
                if (puzzleConfig.modWebsite != null) {
                    attributes 'ModWebsite': puzzleConfig.modWebsite
                }
                if (puzzleConfig.modUpdateURL != null) {
                    attributes 'ModUpdateURL': puzzleConfig.modUpdateURL
                }
            }
        }
    }

    @Override
    String getDefaultMain() {
        return "net.puzzle_mod_loader.launch.LaunchClient"
    }

    @Override
    String getServerMain() {
        return "net.puzzle_mod_loader.launch.LaunchServer"
    }

    @Override
    void postInject(Config config) {
        PuzzleConfig puzzleConfig = (PuzzleConfig) config
        File dummy = new File(project.buildDir, "dummy.zip")
        Task dummyTask = project.tasks.register("dummyJar").get()
        for (String taskName:["runClient","runClientJar","runServer","runServerJar"]) {
            JavaExec task = project.tasks.getByName(taskName) as JavaExec
            task.dependsOn(dummyTask)
            task.systemProperty("udk.puzzle.dev-mode", "true")
            task.systemProperty("udk.puzzle.inject", dummy.getAbsolutePath())
        }
        dummyTask.doFirst {
            Map<String, byte[]> dummyZip = new HashMap<>()
            dummyZip.put("META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\n" +
                    "ModMain: " + puzzleConfig.modMain + "\n" +
                    "ModId: " + puzzleConfig.modID + "\n" +
                    "ModVersion: " + puzzleConfig.modVersion+"\n" +
                    "ModName: " + puzzleConfig.modName + (
                    puzzleConfig.modHook == null ? "" : ("\n" +
                            "ModHook: "+puzzleConfig.modHook
                    )) + (
                    puzzleConfig.modDesc == null ? "" : ("\n" +
                            "ModDesc: "+puzzleConfig.modDesc
                    )) + (
                    puzzleConfig.modWebsite == null ? "" : ("\n" +
                            "ModWebsite: "+puzzleConfig.modWebsite
                    )) + (
                    puzzleConfig.modUpdateURL == null ? "" : ("\n" +
                            "ModUpdateURL: "+puzzleConfig.modUpdateURL
                    ))+"\n").getBytes(StandardCharsets.UTF_8))
            Utils.writeZIP(dummyZip, new FileOutputStream(dummy))
        }
    }

    static class PuzzleConfig extends RepackerPlugin.Config {
        PuzzleConfig() {
            this.keepFramesByDefault = false
        }

        public String puzzleVersion
        public String modMain
        public String modID
        public String modVersion
        public String modName
        public String modHook
        public String modDesc
        public String modWebsite
        public String modUpdateURL
    }

    @Override
    Class<? extends Config> provideConfig() {
        return PuzzleConfig
    }
}
