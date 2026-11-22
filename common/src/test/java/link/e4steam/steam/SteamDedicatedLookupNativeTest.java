package link.e4steam.steam;

import com.codedisaster.steamworks.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in read-only diagnosis of public master-server discovery. */
@EnabledIfEnvironmentVariable(named="E4STEAM_NATIVE_DEDICATED_LOOKUP",matches="true")
class SteamDedicatedLookupNativeTest {
    @Test void inspectDedicatedFilters() throws Exception {
        try(SteamLifecycle lifecycle=new SteamLifecycle(new SteamworksApi())) {
            lifecycle.start();
            inspect(lifecycle,"name",new SteamMatchmakingKeyValuePair[]{new SteamMatchmakingKeyValuePair("gamedir","e4steam"),new SteamMatchmakingKeyValuePair("gametagsand","e4s2,xuy")});
            inspect(lifecycle,"directory",new SteamMatchmakingKeyValuePair[]{new SteamMatchmakingKeyValuePair("gamedir","e4steam")});
            try(SteamDedicatedNameDirectory names=new SteamDedicatedNameDirectory()) {
                var future=names.search("xuy");long deadline=System.currentTimeMillis()+25_000;
                while(!future.isDone()&&System.currentTimeMillis()<deadline){lifecycle.runCallbacks();names.tick(System.currentTimeMillis());Thread.sleep(20);}
                assertTrue(future.isDone());
                var selected=SteamDedicatedNameDirectory.choose(future.get(),null,"1.21.1");
                assertNotNull(selected,"The actual resolver must find the live test server through cached Steam metadata");
                System.out.println("ACTUAL_NAMED_RESOLVER=PASS");
            }


        }
    }
    private void inspect(SteamLifecycle lifecycle,String label,SteamMatchmakingKeyValuePair[] filters) throws Exception {
        SteamMatchmakingServers browser=new SteamMatchmakingServers();
        AtomicBoolean done=new AtomicBoolean();AtomicInteger answered=new AtomicInteger(),failed=new AtomicInteger(),named=new AtomicInteger();
        AtomicReference<String> status=new AtomicReference<>("timeout");
        SteamMatchmakingServerListResponse response=new SteamMatchmakingServerListResponse(){
            private void read(SteamServerListRequest request,int index){
                SteamMatchmakingGameServerItem item=new SteamMatchmakingGameServerItem();
                if(browser.getServerDetails(request,index,item)&&item.getGameTags().startsWith("e4s2,")) {
                    named.incrementAndGet();
                    try {
                        SteamDedicatedNameProtocol.Target target=SteamDedicatedNameProtocol.decode(item.getGameTags(),System.currentTimeMillis());
                        System.out.println("NAMED_ITEM app="+item.getAppID()+" decoded=true name="+target.name+" version="+target.minecraft+" age="+(System.currentTimeMillis()-target.updated)+" fingerprint="+target.fingerprint);
                    } catch(RuntimeException invalid){System.out.println("NAMED_ITEM app="+item.getAppID()+" error="+invalid.getMessage()+" tags="+item.getGameTags());}
                }
            }
            @Override public void serverResponded(SteamServerListRequest r,int i){answered.incrementAndGet();read(r,i);}
            @Override public void serverFailedToRespond(SteamServerListRequest r,int i){failed.incrementAndGet();read(r,i);}
            @Override public void refreshComplete(SteamServerListRequest r,Response s){status.set(s.name());done.set(true);}
        };
        SteamServerListRequest request=browser.requestInternetServerList(480,filters,response);
        assertNotNull(request);
        try {
            long deadline=System.currentTimeMillis()+12_000;
            while(!done.get()&&System.currentTimeMillis()<deadline){lifecycle.runCallbacks();Thread.sleep(20);}
            System.out.println("DEDICATED_LOOKUP "+label+" status="+status.get()+" answered="+answered.get()+" failed="+failed.get()+" named="+named.get());
        } finally {browser.cancelQuery(request);browser.releaseRequest(request);response.dispose();}
    }
}
