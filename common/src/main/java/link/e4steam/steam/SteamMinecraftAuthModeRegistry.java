package link.e4steam.steam;
import java.util.concurrent.*;
/** Server-owned socket policy; hints are locked before the first Minecraft byte. */
public final class SteamMinecraftAuthModeRegistry {
 private static final ConcurrentMap<Integer,Long> ONLINE=new ConcurrentHashMap<>();
 private SteamMinecraftAuthModeRegistry(){}
 static void record(int port,long steam,boolean online){if(online)ONLINE.put(port,steam);else ONLINE.remove(port,steam);}
 static void forget(int port,long steam){ONLINE.remove(port,steam);}
 static boolean online(int port,long authenticated){Long owner=ONLINE.get(port);return authenticated!=0L&&owner!=null&&owner==authenticated;}
}
