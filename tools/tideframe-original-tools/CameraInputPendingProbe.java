package dev.tideframe;

import io.netty.channel.*;
import io.netty.channel.local.LocalChannel;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Optional real off-loop lifecycle probe. Never run on a player/region/server thread. */
public final class CameraInputPendingProbe {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        var diagnostic=System.err;
        try {run();diagnostic.println("TIDEFRAME_PENDING_PROBE_PASSED realEventLoop=true addPendingGuard=true selfPackets=true player=false world=false");}
        catch(Throwable failure){failure.printStackTrace(diagnostic);System.exit(1);}
    }
    private static void run() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var loop=new DefaultEventLoop();var channel=new LocalChannel();var release=new CountDownLatch(1);
        try {
            loop.register(channel).sync();
            var received=new AtomicReference<Object>();var clicks=new AtomicInteger();
            var base=new ChannelInboundHandlerAdapter(){@Override public void channelRead(ChannelHandlerContext ctx,Object message){received.set(message);}};
            channel.pipeline().addLast("probe_base",base);loop.submit(()->{}).get(5,TimeUnit.SECONDS);
            var adapter=new CameraInputPackets();
            var install=CameraInputPackets.class.getDeclaredMethod("installOnChannel",Object.class,Object.class,int.class,Runnable.class);install.setAccessible(true);
            var first=(CameraPackets.InputLease)install.invoke(adapter,channel,base,123,(Runnable)clicks::incrementAndGet);
            loop.submit(()->{}).get(5,TimeUnit.SECONDS);first.close();
            var blocked=new CountDownLatch(1);
            loop.submit(()->{blocked.countDown();try{check(release.await(5,TimeUnit.SECONDS),"loop barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}});
            check(blocked.await(5,TimeUnit.SECONDS),"event loop must reach barrier");
            var second=(CameraPackets.InputLease)install.invoke(adapter,channel,base,123,(Runnable)clicks::incrementAndGet);
            var gateField=first.getClass().getDeclaredField("gate");gateField.setAccessible(true);
            var oldGate=(CameraInputGate)gateField.get(first);
            check(oldGate.isRestoring(),"old guard must survive ADD_PENDING until new handlerAdded");
            release.countDown();loop.submit(()->{}).get(5,TimeUnit.SECONDS);
            check(!oldGate.isActive()&&!oldGate.isRestoring(),"new handlerAdded retires old guard");
            Object self=new net.minecraft.network.protocol.game.ServerboundAttackPacket(123);
            loop.submit(()->channel.pipeline().fireChannelRead(self)).get(5,TimeUnit.SECONDS);
            check(received.get()==null&&clicks.get()==1,"reopened active guard consumes self and dispatches once");
            Object other=new net.minecraft.network.protocol.game.ServerboundAttackPacket(124);
            loop.submit(()->channel.pipeline().fireChannelRead(other)).get(5,TimeUnit.SECONDS);
            check(received.getAndSet(null)==other,"other IDs pass unchanged");
            second.close();loop.submit(()->channel.pipeline().fireChannelRead(self)).get(5,TimeUnit.SECONDS);
            check(received.get()==null&&clicks.get()==1,"late close packets consumed without click");
            loop.schedule(()->{},1700,TimeUnit.MILLISECONDS).get(5,TimeUnit.SECONDS);
            loop.submit(()->channel.pipeline().fireChannelRead(self)).get(5,TimeUnit.SECONDS);
            check(received.get()==self,"after grace original server protection restored");
        } finally {release.countDown();channel.close().sync();loop.shutdownGracefully(0,2,TimeUnit.SECONDS).sync();}
    }
}
