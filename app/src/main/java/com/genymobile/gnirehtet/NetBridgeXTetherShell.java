package com.genymobile.gnirehtet;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: classes2.dex */
public final class NetBridgeXTetherShell {
    private static final String PID_FILE = "/data/local/tmp/netbridgex-tether.pid";
    private static final String SHELL_PACKAGE = "com.android.shell";
    private static final String STATUS_FILE = "/data/local/tmp/netbridgex-tether.status";
    private static final String TAG = "NetBridgeXTetherShell";
    private static final int TRANSPORT_TEST = 7;
    private static final String TUN_ADDRESS = "192.0.2.2";
    private static final String TUN_ADDRESS_V6 = "2001:db8::2";
    private static final int WIFI_TETHERING = 0;
    private static ConnectivityManager connectivity;
    private static Forwarder forwarder;
    private static ConnectivityManager.NetworkCallback keepAlive;
    private static final Binder lifetimeToken = new Binder();
    private static Network testNetwork;
    private static Object testNetworkManager;
    private static ParcelFileDescriptor tunInterface;

    private NetBridgeXTetherShell() {
    }

    public static void main(String[] args) {
        try {
            if (Looper.getMainLooper() == null) {
                Looper.prepareMainLooper();
            }
            Context context = asShellContext(acquireSystemContext());
            if (args.length > 0 && "stop".equalsIgnoreCase(args[0])) {
                stop(context);
                return;
            }
            if (args.length > 0 && "unprefer".equalsIgnoreCase(args[0])) {
                clearPreferTestNetworks(context);
                writeStatus("UNPREFERRED");
            } else if (args.length > 0 && "prefer".equalsIgnoreCase(args[0])) {
                preferTestNetworks(context, true);
                writeStatus("PREFERRED");
            } else {
                writeStatus("STEP=context-ready");
                start(context);
                writeStatus("ACTIVE\ninterface=" + interfaceName() + "\npid=" + Process.myPid());
                new CountDownLatch(1).await();
            }
        } catch (Throwable failure) {
            Throwable report = failure;
            while (report.getCause() != null) {
                report = report.getCause();
            }
            Log.e(TAG, "tether helper failed", failure);
            try {
                writeStatus("ERROR\n" + report.getClass().getName() + ": " + report.getMessage() + "\n" + Log.getStackTraceString(failure));
            } catch (Throwable th) {
            }
            stopQuietly();
        }
    }

    private static void enableHiddenApiAccess() {
        try {
            Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
            Object runtime = vmRuntime.getDeclaredMethod("getRuntime", new Class[0]).invoke(null, new Object[0]);
            Method exemptions = vmRuntime.getDeclaredMethod("setHiddenApiExemptions", String[].class);
            exemptions.invoke(runtime, new String[]{"L"});
        } catch (Throwable failure) {
            Log.w(TAG, "Hidden API exemption setup failed: " + failure.getMessage());
        }
    }

    private static Context asShellContext(Context context) throws Exception {
        Context rebased = context.createAttributionContext(SHELL_PACKAGE);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Field field = rebased.getClass().getDeclaredField("mAttributionSource");
                field.setAccessible(true);
                Object current = field.get(rebased);
                Object updated = current.getClass().getMethod("withPackageName", String.class).invoke(current, SHELL_PACKAGE);
                field.set(rebased, updated);
            } else {
                Field field2 = rebased.getClass().getDeclaredField("mOpPackageName");
                field2.setAccessible(true);
                field2.set(rebased, SHELL_PACKAGE);
            }
            Log.i(TAG, "context attribution: package=" + rebased.getPackageName() + " op=" + rebased.getOpPackageName());
            return rebased;
        } catch (Throwable failure) {
            throw new IllegalStateException("could not attribute context to com.android.shell", failure);
        }
    }

    private static Context acquireSystemContext() throws Exception {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object systemMain = activityThread.getMethod("systemMain", new Class[0]).invoke(null, new Object[0]);
        return (Context) activityThread.getMethod("getSystemContext", new Class[0]).invoke(systemMain, new Object[0]);
    }

    private static void start(Context context) throws Exception {
        writeStatus("STEP=hidden-api");
        enableHiddenApiAccess();
        writeStatus("STEP=connectivity");
        connectivity = (ConnectivityManager) context.getSystemService("connectivity");
        if (connectivity == null) {
            throw new IllegalStateException("connectivity service unavailable");
        }
        writeStatus("STEP=stop-preference");
        clearPreferTestNetworks(context);
        writeStatus("STEP=test-network-service");
        testNetworkManager = context.getSystemService("test_network");
        if (testNetworkManager == null) {
            throw new IllegalStateException("test_network service unavailable");
        }
        Class<?> managerClass = Class.forName("android.net.TestNetworkManager");
        writeStatus("STEP=create-tun");
        Object tun = createTunInterface(managerClass, testNetworkManager);
        Method fdMethod = tun.getClass().getMethod("getFileDescriptor", new Class[0]);
        Method nameMethod = tun.getClass().getMethod("getInterfaceName", new Class[0]);
        tunInterface = (ParcelFileDescriptor) fdMethod.invoke(tun, new Object[0]);
        String iface = (String) nameMethod.invoke(tun, new Object[0]);
        writeStatus("STEP=setup-test-network iface=" + iface);
        setupTestNetwork(managerClass, testNetworkManager, iface);
        writeStatus("STEP=await-test-network");
        testNetwork = awaitTestNetwork(iface);
        writeStatus("STEP=keepalive");
        requestKeepAlive();
        writeStatus("STEP=prefer-test-networks-pre");
        preferTestNetworks(context, true);
        writeStatus("STEP=start-hotspot");
        startWifiTethering(context);
        writeStatus("STEP=prefer-test-networks-post");
        preferTestNetworks(context, true);
        Thread.sleep(3000L);
        forwarder = new Forwarder(null, tunInterface.getFileDescriptor(), null);
        forwarder.forward();
        writeText(PID_FILE, Integer.toString(Process.myPid()));
    }

    private static Object createTunInterface(Class<?> managerClass, Object manager) throws Exception {
        LinkAddress v4 = linkAddress(TUN_ADDRESS, 24);
        LinkAddress v6 = linkAddress(TUN_ADDRESS_V6, 64);
        ArrayList<LinkAddress> addresses = new ArrayList<>();
        addresses.add(v4);
        addresses.add(v6);
        try {
            Method method = managerClass.getMethod("createTunInterface", Collection.class);
            return method.invoke(manager, addresses);
        } catch (NoSuchMethodException e) {
            Class<?> arrayType = Class.forName("[Landroid.net.LinkAddress;");
            Method method2 = managerClass.getMethod("createTunInterface", arrayType);
            Object array = Array.newInstance((Class<?>) LinkAddress.class, addresses.size());
            for (int i = 0; i < addresses.size(); i++) {
                Array.set(array, i, addresses.get(i));
            }
            return method2.invoke(manager, array);
        }
    }

    private static LinkAddress linkAddress(String address, int prefix) throws Exception {
        Constructor<LinkAddress> constructor = LinkAddress.class.getDeclaredConstructor(InetAddress.class, Integer.TYPE);
        constructor.setAccessible(true);
        return constructor.newInstance(InetAddress.getByName(address), Integer.valueOf(prefix));
    }

    private static void setupTestNetwork(Class<?> managerClass, Object manager, String iface) throws Exception {
        LinkProperties properties = new LinkProperties();
        properties.setInterfaceName(iface);
        ArrayList<InetAddress> dns = new ArrayList<>();
        dns.add(InetAddress.getByName("8.8.8.8"));
        properties.setDnsServers(dns);
        try {
            Method method = managerClass.getMethod("setupTestNetwork", LinkProperties.class, Boolean.TYPE, IBinder.class);
            method.invoke(manager, properties, true, lifetimeToken);
        } catch (NoSuchMethodException e) {
            Method method2 = managerClass.getMethod("setupTestNetwork", String.class, IBinder.class);
            method2.invoke(manager, iface, lifetimeToken);
        }
    }

    private static Network awaitTestNetwork(String iface) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            for (Network network : connectivity.getAllNetworks()) {
                LinkProperties properties = connectivity.getLinkProperties(network);
                if (properties != null && iface.equals(properties.getInterfaceName())) {
                    return network;
                }
            }
            Thread.sleep(200L);
        }
        throw new IllegalStateException("test network " + iface + " did not become available");
    }

    private static void requestKeepAlive() {
        NetworkRequest request = new NetworkRequest.Builder().clearCapabilities().addTransportType(TRANSPORT_TEST).build();
        keepAlive = new ConnectivityManager.NetworkCallback() { // from class: com.genymobile.gnirehtet.NetBridgeXTetherShell.1
        };
        connectivity.requestNetwork(request, keepAlive);
    }

    private static void preferTestNetworks(Context context, boolean prefer) throws Exception {
        Object tethering = context.getSystemService("tethering");
        if (tethering == null) {
            throw new IllegalStateException("tethering service unavailable");
        }
        Class<?> manager = Class.forName("android.net.TetheringManager");
        Method method = manager.getMethod("setPreferTestNetworks", Boolean.TYPE);
        method.invoke(tethering, Boolean.valueOf(prefer));
    }

    private static void clearPreferTestNetworks(Context context) {
        try {
            preferTestNetworks(context, false);
        } catch (Throwable th) {
        }
    }

    private static void startWifiTethering(Context context) throws Exception {
        if (isWifiApEnabled(context)) {
            Log.i(TAG, "Wi-Fi hotspot already enabled; reusing existing hotspot.");
            return;
        }
        Object tethering = context.getSystemService("tethering");
        if (tethering == null) {
            throw new IllegalStateException("tethering service unavailable");
        }
        Class<?> managerClass = Class.forName("android.net.TetheringManager");
        Class<?> requestClass = Class.forName("android.net.TetheringManager$TetheringRequest");
        Class<?> builderClass = Class.forName("android.net.TetheringManager$TetheringRequest$Builder");
        Class<?> callbackClass = Class.forName("android.net.TetheringManager$StartTetheringCallback");
        Object builder = builderClass.getConstructor(Integer.TYPE).newInstance(0);
        try {
            builderClass.getDeclaredMethod("setExemptFromEntitlementCheck", Boolean.TYPE).invoke(builder, true);
        } catch (NoSuchMethodException e) {
        }
        Object request = builderClass.getMethod("build", new Class[0]).invoke(builder, new Object[0]);
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {"timeout"};
        Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class[]{callbackClass}, new InvocationHandler() { // from class: com.genymobile.gnirehtet.NetBridgeXTetherShell$$ExternalSyntheticLambda0
            @Override // java.lang.reflect.InvocationHandler
            public final Object invoke(Object obj, Method method, Object[] objArr) {
                return NetBridgeXTetherShell.lambda$startWifiTethering$0(result, latch, obj, method, objArr);
            }
        });
        Executor executor = new Executor() { // from class: com.genymobile.gnirehtet.NetBridgeXTetherShell$$ExternalSyntheticLambda1
            @Override // java.util.concurrent.Executor
            public final void execute(Runnable runnable) {
                runnable.run();
            }
        };
        Method start = managerClass.getMethod("startTethering", requestClass, Executor.class, callbackClass);
        start.invoke(tethering, request, executor, callback);
        if (!latch.await(15000L, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("TetheringManager.startTethering timed out");
        }
        if (!"started".equals(result[0])) {
            throw new IllegalStateException("TetheringManager.startTethering " + result[0]);
        }
        Thread.sleep(2000L);
    }

    static /* synthetic */ Object lambda$startWifiTethering$0(String[] result, CountDownLatch latch, Object proxy, Method method, Object[] args) throws Throwable {
        if ("onTetheringStarted".equals(method.getName())) {
            result[0] = "started";
            latch.countDown();
            return null;
        }
        if ("onTetheringFailed".equals(method.getName())) {
            int code = (args == null || args.length <= 0 || !(args[0] instanceof Integer)) ? -1 : ((Integer) args[0]).intValue();
            if (code == 18) {
                result[0] = "started";
            } else {
                result[0] = "failed:" + code;
            }
            latch.countDown();
            return null;
        }
        return null;
    }

    private static boolean isWifiApEnabled(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService("wifi");
            if (wifi == null) {
                return false;
            }
            Method method = wifi.getClass().getMethod("isWifiApEnabled", new Class[0]);
            return Boolean.TRUE.equals(method.invoke(wifi, new Object[0]));
        } catch (Throwable th) {
            return false;
        }
    }

    private static void stopWifiTethering(Context context) {
        try {
            Object tethering = context.getSystemService("tethering");
            Class<?> manager = Class.forName("android.net.TetheringManager");
            Method stop = manager.getMethod("stopTethering", Integer.TYPE);
            stop.invoke(tethering, 0);
            Thread.sleep(1000L);
        } catch (Throwable th) {
        }
    }

    private static String interfaceName() {
        try {
            if (testNetwork == null) {
                return "unknown";
            }
            return connectivity.getLinkProperties(testNetwork).getInterfaceName();
        } catch (Throwable th) {
            return "unknown";
        }
    }

    private static void stop(Context context) {
        stopQuietly();
        clearPreferTestNetworks(context);
        stopWifiTethering(context);
        deleteFile(STATUS_FILE);
        deleteFile(PID_FILE);
    }

    private static void stopQuietly() {
        try {
            if (forwarder != null) {
                forwarder.stop();
            }
        } catch (Throwable th) {
        }
        forwarder = null;
        try {
            if (keepAlive != null && connectivity != null) {
                connectivity.unregisterNetworkCallback(keepAlive);
            }
        } catch (Throwable th2) {
        }
        keepAlive = null;
        try {
            if (testNetwork != null && testNetworkManager != null) {
                Class<?> manager = Class.forName("android.net.TestNetworkManager");
                Method teardown = manager.getMethod("teardownTestNetwork", Network.class);
                teardown.invoke(testNetworkManager, testNetwork);
            }
        } catch (Throwable th3) {
        }
        testNetwork = null;
        try {
            if (tunInterface != null) {
                tunInterface.close();
            }
        } catch (Throwable th4) {
        }
        tunInterface = null;
    }

    private static void writeStatus(String value) throws IOException {
        writeText(STATUS_FILE, value);
    }

    private static void writeText(String path, String value) throws IOException {
        FileWriter writer = new FileWriter(new File(path), false);
        try {
            writer.write(value);
        } finally {
            writer.close();
        }
    }

    private static void deleteFile(String path) {
        try {
            new File(path).delete();
        } catch (Throwable th) {
        }
    }
}