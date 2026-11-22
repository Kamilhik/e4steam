package link.e4steam.steam;
import com.codedisaster.steamworks.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Searches Steam's anonymous GameServer directory; requires no server-side Steam user. */
final class SteamDedicatedNameDirectory implements AutoCloseable {
    private final SteamMatchmakingServers browser=new SteamMatchmakingServers();
    private final List<SteamDedicatedNameProtocol.Target> found=new ArrayList<>();
    private SteamServerListRequest request;
    private SteamMatchmakingServerListResponse response;
    private CompletableFuture<List<SteamDedicatedNameProtocol.Target>> pending;
    private String name;
    private long deadline;
    private boolean complete,overflow;
    CompletableFuture<List<SteamDedicatedNameProtocol.Target>> search(String name) throws IOException {
        if(pending!=null)throw new IOException("Поиск выделенного адреса уже выполняется.");
        this.name=SteamNamedAddress.normalize(name);found.clear();complete=overflow=false;
        pending=new CompletableFuture<>();deadline=System.currentTimeMillis()+20_000;
        CompletableFuture<List<SteamDedicatedNameProtocol.Target>> result=pending;
        response=new SteamMatchmakingServerListResponse() {
            @Override public void serverResponded(SteamServerListRequest handle,int index){read(handle,index);}
            @Override public void serverFailedToRespond(SteamServerListRequest handle,int index){read(handle,index);}
            @Override public void refreshComplete(SteamServerListRequest handle,Response status){complete=true;}
        };
        try {
            request=browser.requestInternetServerList(480,new SteamMatchmakingKeyValuePair[]{
                    new SteamMatchmakingKeyValuePair("gamedir","e4steam"),
                    new SteamMatchmakingKeyValuePair("gametagsand",SteamDedicatedNameProtocol.MARKER+","+this.name)},response);
            if(request==null)throw new IllegalStateException("Named directory unavailable");
        } catch(RuntimeException unavailable){pending=null;cleanup();result.completeExceptionally(new IOException("Steam не выполнил поиск выделенного адреса.",unavailable));}
        return result;
    }
    private void read(SteamServerListRequest handle,int index) {
        if(pending==null||complete)return;
        try {
            SteamMatchmakingGameServerItem item=new SteamMatchmakingGameServerItem();
            if(!browser.getServerDetails(handle,index,item)||item.getAppID()!=480)return;
            SteamDedicatedNameProtocol.Target target=SteamDedicatedNameProtocol.decode(item.getGameTags(),System.currentTimeMillis());
            if(!name.equals(target.name))return;
            if(found.size()>=100){overflow=true;complete=true;return;}
            found.add(target);
        } catch(RuntimeException foreign){}
    }
    void tick(long now) {
        if(pending==null||(!complete&&now<deadline))return;
        CompletableFuture<List<SteamDedicatedNameProtocol.Target>> done=pending;pending=null;
        cleanup();
        if(overflow)done.completeExceptionally(new IOException("Слишком много серверов с этим адресом."));
        else done.complete(Collections.unmodifiableList(new ArrayList<>(found)));
        found.clear();
    }
    static SteamDedicatedNameProtocol.Target choose(List<SteamDedicatedNameProtocol.Target> candidates,String pinned,String minecraft) throws IOException {
        Map<String,SteamDedicatedNameProtocol.Target> identities=new LinkedHashMap<>();
        for(SteamDedicatedNameProtocol.Target target:candidates) {
            if(pinned!=null&&!pinned.equals(target.fingerprint))continue;
            SteamDedicatedNameProtocol.Target prior=identities.get(target.fingerprint);
            if(prior==null||target.updated>prior.updated||target.updated==prior.updated&&target.endpoint.generation()>prior.endpoint.generation())identities.put(target.fingerprint,target);
        }
        if(identities.isEmpty())return null;
        if(identities.size()!=1)throw new IOException("Найдено несколько выделенных серверов с этим именем. Выберите другое имя.");
        SteamDedicatedNameProtocol.Target target=identities.values().iterator().next();
        if(!minecraft.equals(target.minecraft))throw new IOException("Для сервера нужен Minecraft "+target.minecraft);
        return target;
    }
    boolean keepsRuntimeAlive(){return pending!=null;}
    private void cleanup(){if(request!=null){browser.cancelQuery(request);browser.releaseRequest(request);request=null;}if(response!=null){response.dispose();response=null;}}
    @Override public void close(){CompletableFuture<List<SteamDedicatedNameProtocol.Target>> done=pending;pending=null;cleanup();found.clear();if(done!=null)done.completeExceptionally(new IOException("Поиск закрыт."));}
}
