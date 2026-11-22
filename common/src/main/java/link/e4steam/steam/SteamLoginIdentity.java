package link.e4steam.steam;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;

/** Preserve vanilla identities only after Mojang verification or server-owned offline binding. */
public final class SteamLoginIdentity {
 private SteamLoginIdentity(){}
 public static GameProfile resolve(MinecraftServer server,GameProfile profile,long peer,boolean mojangVerified) throws IOException {
  CompletableFuture<GameProfile> result=new CompletableFuture<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
  Runnable action=()-> {
   if(result.isCancelled()||System.nanoTime()>deadline)return;
   try {result.complete(resolveOnServer(server,profile,peer,mojangVerified));}catch(Exception failure){result.completeExceptionally(failure);}
  };
  if(server.isSameThread())action.run();else server.execute(action);
  try{return result.get(10,TimeUnit.SECONDS);}
  catch(InterruptedException stopped){Thread.currentThread().interrupt();result.cancel(false);throw new IOException("Player identity verification interrupted",stopped);}
  catch(TimeoutException unavailable){result.cancel(false);throw new IOException("Server did not finish player identity verification",unavailable);}
  catch(ExecutionException failure){throw failure.getCause() instanceof IOException?(IOException)failure.getCause():new IOException("Player identity verification failed",failure.getCause());}
 }
 private static GameProfile resolveOnServer(MinecraftServer server,GameProfile profile,long peer,boolean verified) throws IOException {
  if(peer==0L)return profile;
  String name=SteamMinecraftIdentity.profileName(profile);
  boolean vanillaOnline=server.usesAuthentication();
  UUID id=verified&&vanillaOnline?SteamMinecraftProfile.profileId(profile):UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  GameProfile selected=verified&&vanillaOnline?profile:SteamMinecraftProfile.bind(profile,peer,id);
  if(server.isSingleplayer()&&link.e4steam.Mirror.isSingleplayerOwnerObj(server,selected))throw new IOException("Use a different Minecraft account or name than the world host");
  UUID legacy=SteamMinecraftIdentity.legacyUuid(peer);
  if(!legacy.equals(id)&&server.getPlayerList().getPlayer(legacy)!=null)throw new IOException("This Steam player is already connected; disconnect the previous client first");
  Path world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
  if(!verified || !vanillaOnline)SteamPlayerDataMigration.offlineIdentity(world,peer,name,verified);
  if(server.getPlayerList().getPlayer(id)==null)SteamPlayerDataMigration.migrate(world,peer,id);
  SteamPlayerDataMigration.recordIdentity(world,peer,id);
  SteamMinecraftIdentity.verified(peer,id);
  return selected;
 }
}
