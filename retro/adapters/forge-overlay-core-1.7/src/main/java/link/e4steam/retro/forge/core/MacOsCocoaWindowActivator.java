package link.e4steam.retro.forge.core;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.opengl.Display;

/** Brings LWJGL 2's exact native window forward after an overlay relaunch. */
final class MacOsCocoaWindowActivator {
    private static final String OBJC_SYSTEM_LIBRARY = "/usr/lib/libobjc.A.dylib";
    private static final String LIB_SYSTEM = "/usr/lib/libSystem.B.dylib";
    private static final String APPLICATION_SERVICES =
            "/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices";
    private static final String PERFORM_ON_MAIN_THREAD =
            "performSelectorOnMainThread:withObject:waitUntilDone:";
    private static final int ACTIVATION_ATTEMPTS = 100;
    private static final long ACTIVATION_RETRY_DELAY_MILLIS = 100L;
    private static volatile boolean activationPathReported;

    private MacOsCocoaWindowActivator() {
    }

    static boolean activateCurrentDisplay() {
        try {
            Method getImplementation = Display.class.getDeclaredMethod("getImplementation");
            getImplementation.setAccessible(true);
            return activate(getImplementation.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            reportFailure(failure);
            return false;
        }
    }

    static boolean activate(Object displayImplementation) {
        if (displayImplementation == null) return false;
        try {
            ByteBuffer windowHandle = windowHandle(displayImplementation);
            Pointer windowInfo = Native.getDirectBufferPointer(windowHandle);
            if (isNull(windowInfo)) {
                throw new IllegalStateException("LWJGL returned no native window structure");
            }
            Pointer window = windowInfo.getPointer(0L);
            if (isNull(window)) {
                throw new IllegalStateException("LWJGL returned no NSWindow");
            }

            ObjectiveC objc = new ObjectiveC();
            Pointer application = objc.sendPointer(
                    objc.classNamed("NSApplication"), objc.selector("sharedApplication")
            );
            if (isNull(application)) {
                throw new IllegalStateException("Cocoa returned no NSApplication");
            }

            ActivationState state = new ActivationState(
                    objc,
                    application,
                    window,
                    ForegroundProcess.tryCreate(),
                    MainQueue.tryCreate(objc),
                    MainThreadDetector.tryCreate(),
                    EawtForeground.tryCreate()
            );
            state.activateDirectlyIfMainThread();
            if (state.isActivated()) return true;
            state.requestActivation();
            startActivationRetries(state);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            reportFailure(failure);
            return false;
        }
    }

    private static ByteBuffer windowHandle(Object displayImplementation)
            throws ReflectiveOperationException {
        Field windowField = displayImplementation.getClass().getDeclaredField("window");
        windowField.setAccessible(true);
        Object value = windowField.get(displayImplementation);
        if (!(value instanceof ByteBuffer) || !((ByteBuffer) value).isDirect()) {
            throw new IllegalStateException("LWJGL window handle is not a direct buffer");
        }
        return (ByteBuffer) value;
    }

    private static NativeLibrary loadObjectiveC() {
        try {
            return NativeLibrary.getInstance(OBJC_SYSTEM_LIBRARY);
        } catch (UnsatisfiedLinkError missingAbsolutePath) {
            return NativeLibrary.getInstance("objc");
        }
    }

    private static boolean isNull(Pointer pointer) {
        return pointer == null || Pointer.nativeValue(pointer) == 0L;
    }

    private static void startActivationRetries(final ActivationState state) {
        Thread activator = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    for (int attempt = 0; attempt < ACTIVATION_ATTEMPTS; attempt++) {
                        Thread.sleep(ACTIVATION_RETRY_DELAY_MILLIS);
                        if (state.isActivated()) return;
                        state.requestActivation();
                    }
                    if (!state.isActivated()) state.reportTimeout();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException | LinkageError failure) {
                    reportFailure(failure);
                }
            }
        }, "e4steam-macos-window-activation");
        activator.setDaemon(true);
        activator.start();
    }

    private static void reportFailure(Throwable failure) {
        System.err.println("[e4steam] Could not activate the macOS Minecraft window: " + failure);
    }

    /**
     * Owns one bounded activation attempt. If LWJGL 2 is already executing on
     * the macOS first thread, AppKit calls can run immediately; otherwise the
     * retry thread only asks AppKit to run the work from its main run loop.
     */
    private static final class ActivationState {
        private final ObjectiveC objc;
        private final Pointer application;
        private final Pointer window;
        private final ForegroundProcess foregroundProcess;
        private final MainQueue mainQueue;
        private final MainThreadDetector mainThreadDetector;
        private final EawtForeground eawtForeground;
        private final AtomicBoolean queued = new AtomicBoolean(false);

        private volatile boolean firstThread;
        private volatile boolean mainTaskRan;
        private volatile boolean activated;
        private volatile boolean eawtRequested;
        private volatile int attempts;
        private volatile ProcessStatus processStatus = ProcessStatus.notAttempted();
        private volatile long policyBefore = Long.MIN_VALUE;
        private volatile long policyAfter = Long.MIN_VALUE;
        private volatile boolean policyChanged;
        private volatile boolean appActive;
        private volatile boolean runningApplicationActive;
        private volatile boolean windowVisible;
        private volatile boolean windowKey;
        private volatile String failure = "none";

        private ActivationState(
                ObjectiveC objc,
                Pointer application,
                Pointer window,
                ForegroundProcess foregroundProcess,
                MainQueue mainQueue,
                MainThreadDetector mainThreadDetector,
                EawtForeground eawtForeground
        ) {
            this.objc = objc;
            this.application = application;
            this.window = window;
            this.foregroundProcess = foregroundProcess;
            this.mainQueue = mainQueue;
            this.mainThreadDetector = mainThreadDetector;
            this.eawtForeground = eawtForeground;
            if (mainQueue == null) {
                failure = "AppKit main-thread dispatcher unavailable: "
                        + MainQueue.unavailableDetail();
            }
        }

        private boolean isActivated() {
            return activated;
        }

        private void activateDirectlyIfMainThread() {
            if (activated || mainThreadDetector == null) return;
            try {
                firstThread = mainThreadDetector.isCurrentThreadMain();
                reportActivationPath();
                if (!firstThread) return;
                activateOnMainThread();
            } catch (RuntimeException | LinkageError activationFailure) {
                failure = "direct first-thread activation failed: " + activationFailure;
            }
        }

        private void reportActivationPath() {
            if (activationPathReported) return;
            activationPathReported = true;
            System.err.println(
                    "[e4steam] macOS activation path: firstThread=" + firstThread
                            + ", appKitQueue=" + (mainQueue != null)
                            + ", eawt=" + (eawtForeground != null)
            );
        }

        private void requestActivation() {
            if (foregroundProcess != null && attempts == 0
                    && processStatus.lookup == ProcessStatus.NOT_ATTEMPTED) {
                // Keep the relaunched JVM visible in Dock even if AppKit has
                // not yet serviced the custom main-thread selector.
                processStatus = foregroundProcess.bringForward();
            }
            if (activated || !queued.compareAndSet(false, true)) return;
            if (mainQueue == null) {
                queued.set(false);
                requestFallbackActivation();
                return;
            }

            try {
                if (!mainQueue.dispatch(new Runnable() {
                    @Override public void run() {
                        try {
                            activateOnMainThread();
                        } catch (RuntimeException | LinkageError activationFailure) {
                            failure = activationFailure.toString();
                        } finally {
                            queued.set(false);
                        }
                    }
                })) {
                    failure = "AppKit activation queue is full";
                    queued.set(false);
                }
            } catch (RuntimeException | LinkageError dispatchFailure) {
                failure = "AppKit main-thread dispatch failed: " + dispatchFailure;
                queued.set(false);
                requestFallbackActivation();
            }
        }

        private void activateOnMainThread() {
            mainTaskRan = true;
            attempts++;
            processStatus = foregroundProcess == null
                    ? ProcessStatus.unavailable()
                    : foregroundProcess.bringForward();
            if (eawtForeground != null) {
                eawtRequested = eawtForeground.requestForeground();
            }

            policyBefore = objc.sendLong(application, "activationPolicy");
            policyChanged = objc.sendBoolean(
                    application,
                    "setActivationPolicy:",
                    new NativeLong(0L)
            );
            policyAfter = objc.sendLong(application, "activationPolicy");

            objc.sendVoid(application, "unhide:", Pointer.NULL);
            objc.sendVoid(window, "deminiaturize:", Pointer.NULL);
            objc.sendVoid(window, "orderFrontRegardless");
            objc.sendVoid(window, "makeMainWindow");
            objc.sendVoid(window, "makeKeyWindow");
            objc.sendVoid(window, "makeKeyAndOrderFront:", Pointer.NULL);
            objc.sendVoid(application, "activateIgnoringOtherApps:", Integer.valueOf(1));

            Pointer runningApplication = objc.currentApplication();
            objc.sendBoolean(
                    runningApplication,
                    "activateWithOptions:",
                    new NativeLong(3L)
            );

            // OpenJDK applies the Carbon foreground transformation before the
            // AppKit activation. Repeating SetFrontProcess afterwards avoids a
            // launcher reclaiming focus between the two calls.
            if (foregroundProcess != null) {
                processStatus = foregroundProcess.bringForward();
            }
            if (eawtForeground != null) {
                eawtRequested = eawtForeground.requestForeground() || eawtRequested;
            }
            objc.sendVoid(window, "orderFrontRegardless");
            objc.sendVoid(window, "makeKeyAndOrderFront:", Pointer.NULL);

            appActive = objc.sendBoolean(application, "isActive");
            runningApplicationActive = objc.sendBoolean(runningApplication, "isActive");
            windowVisible = objc.sendBoolean(window, "isVisible");
            windowKey = objc.sendBoolean(window, "isKeyWindow");
            activated = windowVisible && (appActive || runningApplicationActive || windowKey);
        }

        /** Last-resort path for an unusually stripped libSystem. */
        private void requestFallbackActivation() {
            attempts++;
            processStatus = foregroundProcess == null
                    ? ProcessStatus.unavailable()
                    : foregroundProcess.bringForward();
            try {
                objc.scheduleOnMainThread(application, "setActivationPolicy:", Pointer.NULL);
                objc.scheduleOnMainThread(application, "unhide:", Pointer.NULL);
                objc.scheduleOnMainThread(window, "deminiaturize:", Pointer.NULL);
                objc.scheduleOnMainThread(window, "orderFrontRegardless", Pointer.NULL);
                objc.scheduleOnMainThread(window, "makeKeyAndOrderFront:", Pointer.NULL);
                objc.activateCurrentApplication();
            } catch (RuntimeException | LinkageError fallbackFailure) {
                failure = "fallback failed: " + fallbackFailure;
            }
        }

        private void reportTimeout() {
            ProcessStatus status = processStatus;
            System.err.println(
                    "[e4steam] macOS activation failed after "
                            + (ACTIVATION_ATTEMPTS * ACTIVATION_RETRY_DELAY_MILLIS)
                            + " ms: firstThread=" + firstThread
                            + ", mainTask=" + mainTaskRan
                            + ", attempts=" + attempts
                            + ", processLookup=" + status.lookup
                            + ", transform=" + status.transform
                            + ", front=" + status.front
                            + ", eawt=" + eawtRequested
                            + ", policyBefore=" + policyBefore
                            + ", policyChanged=" + policyChanged
                            + ", policyAfter=" + policyAfter
                            + ", appActive=" + appActive
                            + ", runningActive=" + runningApplicationActive
                            + ", windowVisible=" + windowVisible
                            + ", windowKey=" + windowKey
                            + ", detail=" + failure
            );
        }
    }

    /** Detects the native Darwin main thread used by -XstartOnFirstThread. */
    private static final class MainThreadDetector {
        private final Function pthreadMain;

        private MainThreadDetector(NativeLibrary library) {
            pthreadMain = library.getFunction("pthread_main_np");
        }

        private static MainThreadDetector tryCreate() {
            try {
                return new MainThreadDetector(NativeLibrary.getInstance(LIB_SYSTEM));
            } catch (RuntimeException | LinkageError missingAbsolutePath) {
                try {
                    return new MainThreadDetector(NativeLibrary.getInstance("System"));
                } catch (RuntimeException | LinkageError unavailable) {
                    System.err.println(
                            "[e4steam] macOS main-thread detector is unavailable: "
                                    + unavailable
                    );
                    return null;
                }
            }
        }

        private boolean isCurrentThreadMain() {
            return pthreadMain.invokeInt(new Object[0]) != 0;
        }
    }

    /** Uses Apple's Java 8 desktop bridge when it is present in the runtime. */
    private static final class EawtForeground {
        private final Object application;
        private final Method requestForeground;

        private EawtForeground(Object application, Method requestForeground) {
            this.application = application;
            this.requestForeground = requestForeground;
        }

        private static EawtForeground tryCreate() {
            try {
                Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
                Object application = applicationClass.getMethod("getApplication").invoke(null);
                Method requestForeground =
                        applicationClass.getMethod("requestForeground", Boolean.TYPE);
                return new EawtForeground(application, requestForeground);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return null;
            }
        }

        private boolean requestForeground() {
            try {
                requestForeground.invoke(application, Boolean.TRUE);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                return false;
            }
        }
    }

    /**
     * Schedules a Java callback through NSObject's main-thread selector path.
     * LWJGL 2 uses this same AppKit run-loop mechanism to create its NSWindow;
     * legacy Java applications do not necessarily drain GCD's main queue.
     */
    private static final class MainQueue {
        private static final String DISPATCHER_CLASS = "E4steamMainThreadDispatcher";
        private static final String DISPATCHER_ACTION = "e4steamPerform:";
        private static final String DISPATCHER_ENCODING = "v@:@";
        private static final int MAX_PENDING_TASKS = 16;
        private static final ConcurrentLinkedQueue<Runnable> PENDING_TASKS =
                new ConcurrentLinkedQueue<Runnable>();
        private static final MainThreadCallback MAIN_THREAD_CALLBACK =
                new MainThreadCallback() {
            @Override public void invoke(Pointer receiver, Pointer action, Pointer argument) {
                Runnable task = PENDING_TASKS.poll();
                if (task == null) return;
                try {
                    task.run();
                } catch (RuntimeException | LinkageError failure) {
                    reportFailure(failure);
                }
            }
        };
        private static volatile String creationFailure = "not initialized";

        private final ObjectiveC objc;
        private final Pointer dispatcher;
        private final Pointer dispatcherAction;

        private MainQueue(ObjectiveC objc) {
            this.objc = objc;
            dispatcherAction = objc.selector(DISPATCHER_ACTION);

            Pointer dispatcherClass = objc.classNamedIfPresent(DISPATCHER_CLASS);
            if (isNull(dispatcherClass)) {
                Pointer objectClass = objc.classNamed("NSObject");
                dispatcherClass = objc.allocateClassPair(objectClass, DISPATCHER_CLASS);
                if (isNull(dispatcherClass)) {
                    dispatcherClass = objc.classNamedIfPresent(DISPATCHER_CLASS);
                } else {
                    if (!objc.addMethod(
                            dispatcherClass,
                            dispatcherAction,
                            MAIN_THREAD_CALLBACK,
                            DISPATCHER_ENCODING
                    )) {
                        throw new IllegalStateException(
                                "could not install the AppKit dispatcher method"
                        );
                    }
                    objc.registerClassPair(dispatcherClass);
                }
            }
            if (isNull(dispatcherClass)) {
                throw new IllegalStateException("could not create the AppKit dispatcher class");
            }

            Pointer allocated = objc.sendPointer(dispatcherClass, objc.selector("alloc"));
            dispatcher = objc.sendPointer(allocated, objc.selector("init"));
            if (isNull(dispatcher)) {
                throw new IllegalStateException("could not create the AppKit dispatcher object");
            }
        }

        private static MainQueue tryCreate(ObjectiveC objc) {
            try {
                return new MainQueue(objc);
            } catch (RuntimeException | LinkageError unavailable) {
                creationFailure = unavailable.toString();
                System.err.println(
                        "[e4steam] macOS main-thread dispatcher is unavailable: " + unavailable
                );
                return null;
            }
        }

        private static String unavailableDetail() {
            return creationFailure;
        }

        private boolean dispatch(Runnable task) {
            if (PENDING_TASKS.size() >= MAX_PENDING_TASKS) return false;
            PENDING_TASKS.add(task);
            try {
                objc.scheduleOnMainThread(
                        dispatcher,
                        dispatcherAction,
                        Pointer.NULL,
                        false
                );
                return true;
            } catch (RuntimeException | LinkageError failure) {
                PENDING_TASKS.remove(task);
                throw failure;
            }
        }

        private interface MainThreadCallback extends Callback {
            void invoke(Pointer receiver, Pointer action, Pointer argument);
        }
    }

    private static final class ObjectiveC {
        private final Function objcGetClass;
        private final Function objcAllocateClassPair;
        private final Function classAddMethod;
        private final Function objcRegisterClassPair;
        private final Function selectorRegister;
        private final Function messageSend;

        private ObjectiveC() {
            NativeLibrary library = loadObjectiveC();
            objcGetClass = library.getFunction("objc_getClass");
            objcAllocateClassPair = library.getFunction("objc_allocateClassPair");
            classAddMethod = library.getFunction("class_addMethod");
            objcRegisterClassPair = library.getFunction("objc_registerClassPair");
            selectorRegister = library.getFunction("sel_registerName");
            messageSend = library.getFunction("objc_msgSend");
        }

        private Pointer classNamed(String name) {
            Pointer result = classNamedIfPresent(name);
            if (isNull(result)) {
                throw new IllegalStateException("Objective-C class is missing: " + name);
            }
            return result;
        }

        private Pointer classNamedIfPresent(String name) {
            return objcGetClass.invokePointer(new Object[]{name});
        }

        private Pointer allocateClassPair(Pointer superclass, String name) {
            return objcAllocateClassPair.invokePointer(new Object[]{
                    superclass, name, new NativeLong(0L)
            });
        }

        private boolean addMethod(
                Pointer targetClass,
                Pointer action,
                Callback implementation,
                String encoding
        ) {
            return classAddMethod.invokeInt(new Object[]{
                    targetClass, action, implementation, encoding
            }) != 0;
        }

        private void registerClassPair(Pointer targetClass) {
            objcRegisterClassPair.invokeVoid(new Object[]{targetClass});
        }

        private Pointer selector(String name) {
            Pointer result = selectorRegister.invokePointer(new Object[]{name});
            if (isNull(result)) {
                throw new IllegalStateException("Objective-C selector is missing: " + name);
            }
            return result;
        }

        private Pointer sendPointer(Pointer receiver, Pointer selector) {
            return messageSend.invokePointer(new Object[]{receiver, selector});
        }

        private long sendLong(Pointer receiver, String action) {
            return messageSend.invokeLong(new Object[]{receiver, selector(action)});
        }

        private boolean sendBoolean(Pointer receiver, String action) {
            return messageSend.invokeInt(new Object[]{receiver, selector(action)}) != 0;
        }

        private boolean sendBoolean(Pointer receiver, String action, Object argument) {
            return messageSend.invokeInt(new Object[]{
                    receiver, selector(action), argument
            }) != 0;
        }

        private void sendVoid(Pointer receiver, String action) {
            messageSend.invokeVoid(new Object[]{receiver, selector(action)});
        }

        private void sendVoid(Pointer receiver, String action, Object argument) {
            messageSend.invokeVoid(new Object[]{receiver, selector(action), argument});
        }

        private boolean activateCurrentApplication() {
            return sendBoolean(
                    currentApplication(),
                    "activateWithOptions:",
                    new NativeLong(3L)
            );
        }

        private Pointer currentApplication() {
            Pointer runningApplication = sendPointer(
                    classNamed("NSRunningApplication"), selector("currentApplication")
            );
            if (isNull(runningApplication)) {
                throw new IllegalStateException("Cocoa returned no NSRunningApplication");
            }
            return runningApplication;
        }

        private void scheduleOnMainThread(Pointer receiver, String action, Pointer argument) {
            scheduleOnMainThread(receiver, selector(action), argument, false);
        }

        private void scheduleOnMainThread(
                Pointer receiver,
                Pointer action,
                Pointer argument,
                boolean waitUntilDone
        ) {
            messageSend.invokeVoid(new Object[]{
                    receiver,
                    selector(PERFORM_ON_MAIN_THREAD),
                    action,
                    argument,
                    Integer.valueOf(waitUntilDone ? 1 : 0)
            });
        }
    }

    private static final class ProcessStatus {
        private static final int NOT_ATTEMPTED = Integer.MIN_VALUE;

        private final int lookup;
        private final int transform;
        private final int front;

        private ProcessStatus(int lookup, int transform, int front) {
            this.lookup = lookup;
            this.transform = transform;
            this.front = front;
        }

        private static ProcessStatus notAttempted() {
            return new ProcessStatus(NOT_ATTEMPTED, NOT_ATTEMPTED, NOT_ATTEMPTED);
        }

        private static ProcessStatus unavailable() {
            return new ProcessStatus(NOT_ATTEMPTED, NOT_ATTEMPTED, NOT_ATTEMPTED);
        }
    }

    /** Carbon foreground conversion used by OpenJDK for a bare Java process. */
    private static final class ForegroundProcess {
        private static final int NO_ERROR = 0;
        private static final int TRANSFORM_TO_FOREGROUND_APPLICATION = 1;

        private final Function getCurrentProcess;
        private final Function transformProcessType;
        private final Function setFrontProcess;

        private ForegroundProcess(NativeLibrary library) {
            getCurrentProcess = library.getFunction("GetCurrentProcess");
            transformProcessType = library.getFunction("TransformProcessType");
            setFrontProcess = library.getFunction("SetFrontProcess");
        }

        private static ForegroundProcess tryCreate() {
            try {
                return new ForegroundProcess(NativeLibrary.getInstance(APPLICATION_SERVICES));
            } catch (RuntimeException | LinkageError unavailable) {
                System.err.println(
                        "[e4steam] macOS foreground-process API is unavailable: " + unavailable
                );
                return null;
            }
        }

        private ProcessStatus bringForward() {
            Memory processSerialNumber = new Memory(8L);
            int lookupStatus = getCurrentProcess.invokeInt(
                    new Object[]{processSerialNumber}
            );
            if (lookupStatus != NO_ERROR) {
                return new ProcessStatus(
                        lookupStatus,
                        ProcessStatus.NOT_ATTEMPTED,
                        ProcessStatus.NOT_ATTEMPTED
                );
            }

            int transformStatus = transformProcessType.invokeInt(new Object[]{
                    processSerialNumber,
                    Integer.valueOf(TRANSFORM_TO_FOREGROUND_APPLICATION)
            });
            int frontStatus = setFrontProcess.invokeInt(
                    new Object[]{processSerialNumber}
            );
            return new ProcessStatus(lookupStatus, transformStatus, frontStatus);
        }
    }
}
