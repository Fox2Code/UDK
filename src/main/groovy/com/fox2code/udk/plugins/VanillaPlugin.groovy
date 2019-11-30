package com.fox2code.udk.plugins

class VanillaPlugin extends RepackerPlugin {
    @Override
    void injectLibraries(Config config) {
        injectRepackVersion(config)
        injectVersionLibraries(config.version)
    }
}
