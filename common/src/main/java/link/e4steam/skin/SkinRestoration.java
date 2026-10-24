package link.e4steam.skin;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;

/** Optional cosmetic fallback; Steam identity and existing skin providers always win. */
public final class SkinRestoration {
    private SkinRestoration() { }
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(64), runnable -> {
                Thread thread = new Thread(runnable, "e4steam-skin-fetch"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final Map<String, Cached> CACHE = new LinkedHashMap<String, Cached>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Cached> entry) { return size() > 512; }
    };

    public static boolean externalProviderPresent() {
        for (ClassLoader loader : new ClassLoader[]{Thread.currentThread().getContextClassLoader(), SkinRestoration.class.getClassLoader()}) {
            try { Class.forName("net.lionarius.skinrestorer.SkinRestorer", false, loader); return true; }
            catch (ClassNotFoundException | LinkageError ignored) { }
        }
        return false;
    }

    public static boolean hasTextures(Object profile) {
        try {
            Object map = properties(profile);
            return Boolean.TRUE.equals(map.getClass().getMethod("containsKey", Object.class).invoke(map, "textures"));
        } catch (ReflectiveOperationException | RuntimeException failure) { return true; }
    }

    public static CompletableFuture<Texture> fetch(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return CompletableFuture.completedFuture(null);
        String key = name.toLowerCase(Locale.ROOT);
        synchronized (CACHE) {
            Cached cached = CACHE.get(key);
            if (cached != null && System.currentTimeMillis() < cached.expires) return CompletableFuture.completedFuture(cached.texture);
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                Texture texture = null;
                try {
                    JsonObject account = read("https://api.mojang.com/users/profiles/minecraft/" + name);
                    if (account != null && account.has("id")) {
                        String id = account.get("id").getAsString();
                        if (id.matches("[a-fA-F0-9]{32}")) texture = parseTexture(read(
                                "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false"));
                    }
                } catch (IOException | RuntimeException ignored) { }
                synchronized (CACHE) { CACHE.put(key, new Cached(texture, System.currentTimeMillis() + (texture == null ? 60_000 : 3_600_000))); }
                return texture;
            }, WORKER);
        } catch (RejectedExecutionException unavailable) { return CompletableFuture.completedFuture(null); }
    }

    public static Texture parseTexture(JsonObject response) {
        if (response == null || !response.has("properties")) return null;
        for (JsonElement element : response.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !"textures".equals(property.get("name").getAsString())) continue;
            if (!property.has("value") || !property.has("signature")) continue;
            String value = property.get("value").getAsString();
            String signature = property.get("signature").getAsString();
            if (value.isEmpty() || value.length() > 32_768 || signature.isEmpty() || signature.length() > 8_192) return null;
            return new Texture(value, signature);
        }
        return null;
    }

    public static void applyIfMissing(Object profile, Texture texture) { restoreIfMissing(profile, texture); }

    public static Object restoreIfMissing(Object profile, Texture texture) {
        if (texture == null || externalProviderPresent() || hasTextures(profile)) return profile;
        try {
            Object map = properties(profile);
            ClassLoader loader = profile.getClass().getClassLoader();
            Class<?> propertyType = Class.forName("com.mojang.authlib.properties.Property", false, loader);
            Object property = propertyType.getConstructor(String.class, String.class, String.class)
                    .newInstance("textures", texture.value, texture.signature);
            // Authlib 7 profiles and their property maps are immutable.
            for (Constructor<?> constructor : profile.getClass().getConstructors()) {
                Class<?>[] args=constructor.getParameterTypes();
                if (args.length!=3 || args[0]!=UUID.class || args[1]!=String.class || !args[2].isInstance(map)) continue;
                Class<?> multimap=Class.forName("com.google.common.collect.Multimap", false, loader);
                Object copy=Class.forName("com.google.common.collect.HashMultimap", false, loader).getMethod("create").invoke(null);
                multimap.getMethod("putAll",multimap).invoke(copy,map);
                multimap.getMethod("put",Object.class,Object.class).invoke(copy,"textures",property);
                Object restoredMap=args[2].getConstructor(multimap).newInstance(copy);
                return constructor.newInstance(access(profile,"getId","id"),access(profile,"getName","name"),restoredMap);
            }
            map.getClass().getMethod("put", Object.class, Object.class).invoke(map, "textures", property);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return profile;
    }

    /** One state per login listener; failures and timeouts always allow login. */
    public static final class LoginState {
        private CompletableFuture<Texture> pending;
        private long deadline;
        private Object profile;
        public Object profile() { return profile; }
        public boolean prepare(Object original) {
            profile = original;
            if (original == null || externalProviderPresent() || hasTextures(original)) return true;
            try {
                String name = (String)access(original, "getName", "name");
                Object id = access(original, "getId", "id");
                // This is vanilla's offline identity; keep every existing Steam/Mojang UUID.
                if (id == null) profile = original.getClass().getConstructor(UUID.class, String.class)
                        .newInstance(UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8)), name);
                if (pending == null) { deadline=System.nanoTime()+12_000_000_000L; pending=fetch(name); }
                if (!pending.isDone() && System.nanoTime()<deadline) return false;
                if (pending.isDone() && !pending.isCompletedExceptionally()) profile = restoreIfMissing(profile, pending.getNow(null));
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
            return true;
        }
    }

    private static Object access(Object target, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            try { return target.getClass().getMethod(name).invoke(target); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException();
    }

    private static Object properties(Object profile) throws ReflectiveOperationException {
        for (String name : new String[]{"getProperties", "properties"}) {
            try { return profile.getClass().getMethod(name).invoke(profile); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("Profile properties");
    }

    private static JsonObject read(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(2500); connection.setReadTimeout(2500);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "e4steam-skin-restoration/1");
        try {
            if (connection.getResponseCode() != 200) return null;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 65536) throw new IOException("Oversized skin response");
                    output.write(buffer, 0, count);
                }
                return new JsonParser().parse(new String(output.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } finally { connection.disconnect(); }
    }

    public static final class Texture {
        public final String value, signature;
        public Texture(String value, String signature) { this.value = value; this.signature = signature; }
    }
    private static final class Cached {
        final Texture texture; final long expires;
        Cached(Texture texture, long expires) { this.texture = texture; this.expires = expires; }
    }
}