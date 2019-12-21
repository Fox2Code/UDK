package com.fox2code.udk.plugins

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
        Objects.requireNonNull(puzzleConfig.puzzleVersion, "Require puzzleVersion!")
        Objects.requireNonNull(puzzleConfig.modMain, "Require modMain!")
        Objects.requireNonNull(puzzleConfig.modID, "Require modID!")
        Objects.requireNonNull(puzzleConfig.modVersion, "Require modVersion!")
        injectRepackVersion(config)
        injectVersionLibraries(config.version)
        project.getDependencies().add("implementation", "net.puzzle-mod-loader:puzzle-mod-loader:"+puzzleConfig.puzzleVersion)
        project.getByName("jar") {
            manifest {
                attributes 'ModMain': puzzleConfig.modMain
                attributes 'ModId': puzzleConfig.modID
                attributes 'ModVersion': puzzleConfig.modVersion
            }
        }
    }

    @Override
    String getDefaultMain() {
        return "net.puzzle_mod_loader.launch.LaunchClient"
    }

    static class PuzzleConfig extends RepackerPlugin.Config {
        public String puzzleVersion
        public String modMain
        public String modID
        public String modVersion
    }

    @Override
    Class<? extends Config> provideConfig() {
        return PuzzleConfig
    }
}
