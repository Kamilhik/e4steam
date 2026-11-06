package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import static org.junit.jupiter.api.Assertions.*;

class SteamWorkerWakeupTest {
    @TempDir Path directory;

    @Test void submittingATaskDoesNotInterruptNativeVerification() throws Exception {
        SteamRuntime runtime = fakeCurrentWorker();
        Method method=SteamRuntime.class.getDeclaredMethod("submitSteamTask", Callable.class);
        method.setAccessible(true);
        try {
            method.invoke(runtime, (Callable<Object>) () -> null);
            verifyNativeCacheRemainsReadable();
        } finally { Thread.interrupted(); }
    }

    @Test void releasingAnActivityDoesNotInterruptNativeVerification() throws Exception {
        SteamRuntime runtime = fakeCurrentWorker();
        Method method=SteamRuntime.class.getDeclaredMethod("releaseActivity");
        method.setAccessible(true);
        try {
            method.invoke(runtime);
            verifyNativeCacheRemainsReadable();
        } finally { Thread.interrupted(); }
    }

    private void verifyNativeCacheRemainsReadable() throws Exception {
        assertFalse(Thread.currentThread().isInterrupted(), "Ordinary queue wakeup interrupted NIO");
        Path cache=SteamNativeLibraryLoader.createPrivateCacheDirectory(directory, "wake-cache");
        SteamNativeLibraryLoader.VerifiedLibrary library=SteamNativeLibraryLoader.materialize(
                cache, "steam_api64.dll", new byte[]{1,2,3,4});
        assertDoesNotThrow(() -> SteamNativeLibraryLoader.validateForLoad(library));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SteamRuntime fakeCurrentWorker() throws Exception {
        SteamApi api=(SteamApi)Proxy.newProxyInstance(SteamApi.class.getClassLoader(),
                new Class<?>[]{SteamApi.class}, (proxy, method, args) ->
                        method.getReturnType()==boolean.class ? Boolean.FALSE : null);
        SteamRuntime runtime=new SteamRuntime(api, false);
        Class<?> type=Class.forName(SteamRuntime.class.getName()+"$WorkerGeneration");
        Constructor<?> constructor=type.getDeclaredConstructor(long.class); constructor.setAccessible(true);
        Object generation=constructor.newInstance(1L);
        Field worker=type.getDeclaredField("worker");worker.setAccessible(true);worker.set(generation,Thread.currentThread());
        Field current=SteamRuntime.class.getDeclaredField("generation");current.setAccessible(true);current.set(runtime,generation);
        Field status=SteamRuntime.class.getDeclaredField("status");status.setAccessible(true);
        status.set(runtime,Enum.valueOf((Class<? extends Enum>)status.getType(),"RUNNING"));
        return runtime;
    }
}
