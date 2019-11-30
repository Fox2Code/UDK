package com.fox2code.udk.plugins;

public class LibraryPlugin extends RepackerPlugin {
    @Override
    public void injectLibraries(Config config) {
        injectVersionLibraries(config.version);
    }
}
