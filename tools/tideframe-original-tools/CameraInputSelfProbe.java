package dev.linqfy.bigCasares.modules.tideframe.engine;

/** Optional probe against the installed official server's own packet/Netty classes. */
public final class CameraInputSelfProbe {
    public static void main(String[] args) throws Exception {
        var diagnostic=System.err;
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            new CameraInputPackets().selfTest();
            diagnostic.println("TIDEFRAME_INPUT_SELFTEST_PASSED real26.2Packets=true embeddedChannel=true player=false world=false");
        } catch(Throwable failure) {
            failure.printStackTrace(diagnostic);System.exit(1);
        }
    }
}
