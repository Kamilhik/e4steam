package link.e4steam.worldtransfer;

import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import link.e4steam.lan.LanScreenGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The same UI is present in free and Premium clients; only the sender needs Premium. */
public final class WorldTransferScreen extends Screen {
    private final Screen parent;
    private final IntegratedServer server;
    private final int page;
    private Runnable exitAction;
    private long revision;
    private TransferEngine.State displayed;
    private int left,top,panelWidth,panelHeight;
    private final List<String> labels=new ArrayList<>();
    public WorldTransferScreen(Screen parent,IntegratedServer server,int page) {
        super(text("title"));this.parent=parent;this.server=server;this.page=Math.max(0,page);
    }
    public static WorldTransferScreen exit(Screen parent,IntegratedServer server,Runnable action) {
        WorldTransferScreen result=new WorldTransferScreen(parent,server,0);result.exitAction=action;return result;
    }
    public static Component text(String key) { return Mirror.translatable("text.e4steam_minecraft.transfer."+key); }
    @Override protected void init() {
        revision=WorldTransferClient.revision();
        TransferEngine engine=WorldTransferClient.engine();
        displayed=engine==null?null:engine.view().state;
        panelWidth=Math.min(360,width-16);panelHeight=Math.min(260,height-12);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int bottom=top+panelHeight-26;
        labels.clear();
        if (exitAction!=null) {
            labels.add(text("exitHelp").getString());
            Button send=button(text("sendFriend"),left+12,top+panelHeight/2-22,panelWidth-24,()-> {
                MinecraftUiCompat.setScreen(minecraft,new WorldTransferScreen(parent,server,0));
            });send.active=!WorldTransferClient.participants().isEmpty() && engine!=null && !engine.busy();
            if (WorldTransferClient.participants().isEmpty()) labels.add(text("noPlayers").getString());
            button(text("exitOnly"),left+12,top+panelHeight/2+2,panelWidth-24,exitAction);
            button(text("back"),left+12,bottom,panelWidth-24,this::onClose);return;
        }
        if (WorldTransferClient.housekeeping()) {labels.add(text("cleanup").getString());return;}
        if (engine==null) {
            labels.add(text(WorldTransferClient.preparationError().isEmpty()?"checking":"checkFailed").getString());
            button(text("back"),left+12,bottom,panelWidth-24,this::onClose);return;
        }
        TransferEngine.View view=engine.view();
        boolean terminal=displayed==TransferEngine.State.COMPLETE || displayed==TransferEngine.State.FAILED || displayed==TransferEngine.State.CANCELLED;
        if (engine.busy() || terminal || displayed==TransferEngine.State.LISTENING) {
            if (view.offer!=null) labels.add(view.offer.name);
            if (view.offer!=null && server!=null && view.offer.origin==view.peer) labels.add(text("returnHelp").getString());
            String friend=WorldTransferClient.friends().get(view.peer);
            if (friend!=null) labels.add(friend);
            if (displayed==TransferEngine.State.OFFERED) labels.add(Long.toUnsignedString(view.peer));
            labels.add(text("state."+displayed.name().toLowerCase(Locale.ROOT)).getString());
            if ("SOURCE_BACKUP_FAILED".equals(WorldTransferClient.preparationError())) labels.add(text("cleanupFailed").getString());
            if ("PLAYER_IDENTITY_UNAVAILABLE".equals(WorldTransferClient.preparationError())) labels.add(text("playerIdentityUnavailable").getString());
            if (!view.error.isEmpty()) labels.add(text(errorKey(view.error)).getString());
            if (displayed==TransferEngine.State.OFFERED) {
                labels.add(text("receiveHelp").getString());
                int half=(panelWidth-32)/2;
                button(text("accept"),left+12,bottom,half,WorldTransferClient::acceptIncoming);
                button(text("decline"),left+20+half,bottom,half,engine::cancel);
            } else if (displayed==TransferEngine.State.COMPLETE && view.installed!=null) {
                labels.add(text("openingWorld").getString());
                if (!WorldTransferClient.preparationError().isEmpty()) {
                    labels.add(text("autoOpenFailed").getString());
                    button(text("back"),left+12,bottom,panelWidth-24,this::onClose);
                }
            } else {
                button(text(terminal?"back":"cancel"),left+12,bottom,panelWidth-24,()-> {
                    if (terminal) {engine.clear();onClose();} else engine.cancel();
                });
            }
            return;
        }
        boolean sending=server!=null && minecraft.getSingleplayerServer()==server && WorldTransferClient.canSend(server);
        labels.add(text(sending?"sendHelp":"receiveHelp").getString());
        Map<Long,String> friends=WorldTransferClient.participants();
        if (!WorldTransferClient.preparationError().isEmpty()) labels.add(text("steamUnavailable").getString());
        else if (friends.isEmpty()) labels.add(text("noPlayers").getString());
        int labelLines=0;
        for (String label:labels) labelLines+=wrap(label,panelWidth-24).size();
        int listTop=top+30+labelLines*11+8;
        int rows=Math.max(1,Math.min(6,(bottom-listTop-8)/24));
        int offset=page*rows;
        List<Map.Entry<Long,String>> entries=new ArrayList<>(friends.entrySet());
        if (sending) entries.removeIf(friend -> !WorldTransferClient.canSendTo(server,friend.getKey()));
        for (int i=0;i<rows && offset+i<entries.size();i++) {
            Map.Entry<Long,String> friend=entries.get(offset+i);
            String label=font.plainSubstrByWidth(friend.getValue(),Math.max(20,panelWidth-50));
            button(Mirror.literal(label),left+12,listTop+i*24,panelWidth-24,()-> {
                if (sending) WorldTransferClient.send(server,friend.getKey());
            });
        }
        int gap=6,quarter=(panelWidth-42)/4;
        Button previous=button(Mirror.literal("←"),left+12,bottom,quarter,()->navigate(page-1));previous.active=page>0;
        Button next=button(Mirror.literal("→"),left+18+quarter,bottom,quarter,()->navigate(page+1));next.active=offset+rows<entries.size();
        button(text("refresh"),left+24+quarter*2,bottom,quarter,()->navigate(page));
        button(text("back"),left+30+quarter*3,bottom,quarter,this::onClose);
    }
    private Button button(Component label,int x,int y,int width,Runnable action) {
        return addRenderableWidget(MinecraftUiCompat.button(label,b -> action.run(),x,y,width,20));
    }
    private void navigate(int page) { WorldTransferScreen next=new WorldTransferScreen(parent,server,page);next.exitAction=exitAction;MinecraftUiCompat.setScreen(minecraft,next); }
    public void advance() {
        TransferEngine engine=WorldTransferClient.engine();
        TransferEngine.State current=engine==null?null:engine.view().state;
        if (current!=displayed || revision!=WorldTransferClient.revision()) navigate(page);
    }
    public void background(Object context) {
        LanScreenGraphics graphics=new LanScreenGraphics(context);
        graphics.fill(0,0,width,height,0xD90C131C);
        graphics.fill(left,top,left+panelWidth,top+panelHeight,0xFF16222F);
        graphics.fill(left,top,left+panelWidth,top+2,0xFF75D7BA);
    }
    public void labels(Object context) {
        LanScreenGraphics graphics=new LanScreenGraphics(context);
        graphics.drawCenteredString(font,title,width/2,top+12,0xFFFFFF);
        int y=top+30;
        for (String label:labels) {
            for (String line:wrap(label,panelWidth-24)) {
                if (y>top+panelHeight-56) break;
                graphics.drawCenteredString(font,Mirror.literal(line),width/2,y,0xBCCBDA);y+=11;
            }
        }
        TransferEngine engine=WorldTransferClient.engine();
        if (engine!=null) {
            TransferEngine.View view=engine.view();
            if (WorldTransferClient.housekeeping() || engine.busy() && view.state!=TransferEngine.State.OFFERED) {
                long done=view.done,total=view.total;
                String phase="network";
                if (WorldTransferClient.housekeeping()) {phase="cleanup";total=0;}
                else if (view.state==TransferEngine.State.SAVING) {
                    WorldTransferClient.PreparationProgress preparing=WorldTransferClient.preparationProgress();
                    if (preparing!=null && view.offer!=null && preparing.transfer.equals(view.offer.id)) {
                        phase=preparing.stage;done=preparing.done;total=preparing.total;
                    } else {phase="waitingSave";total=0;}
                } else if (view.state==TransferEngine.State.VERIFYING) {phase="verifying";total=0;}
                else if (view.state==TransferEngine.State.OFFERING) {phase="waitingConsent";total=0;}
                int barY=top+panelHeight-49,barWidth=panelWidth-24;
                graphics.fill(left+12,barY,left+panelWidth-12,barY+5,0xFF304457);
                String amount;
                if (total>0) {
                    double fraction=Math.max(0,Math.min(1,(double)done/total));
                    graphics.fill(left+12,barY,left+12+(int)(barWidth*fraction),barY+5,0xFF75D7BA);
                    amount=String.format(Locale.ROOT,"%d%% · %.1f / %.1f MiB",(int)(fraction*100),done/1048576.0,total/1048576.0);
                } else {
                    int segment=Math.max(16,barWidth/5);
                    int position=(int)((System.currentTimeMillis()%1800)*(barWidth-segment)/1800);
                    graphics.fill(left+12+position,barY,left+12+position+segment,barY+5,0xFF75D7BA);
                    amount=text("working").getString();
                }
                graphics.drawCenteredString(font,text("progress."+phase),width/2,barY-25,0xFFFFFF);
                graphics.drawCenteredString(font,Mirror.literal(amount),width/2,barY-12,0xBCCBDA);
            }
        }
    }
    private List<String> wrap(String text,int maximum) {
        List<String> lines=new ArrayList<>();String line="";
        for (String word:text.split(" ")) {
            if (!line.isEmpty() && font.width(line+" "+word)>maximum) {lines.add(line);line=word;}
            else line=line.isEmpty()?word:line+" "+word;
        }
        if (!line.isEmpty()) lines.add(line);return lines;
    }
    private static String errorKey(String code) {
        switch (code) {
            case "MODPACK_MISMATCH": return "modpackMismatch";
            case "STEAM_UNAVAILABLE": case "STEAM_DISCONNECTED": case "STEAM_ACCOUNT_CHANGED": return "steamUnavailable";
            case "NOT_ENOUGH_SPACE": return "diskFull";
            case "TRANSFER_TIMEOUT": return "timeout";
            case "WORLD_SAVE_FAILED": case "WORLD_STILL_OPEN": return "saveFailed";
            case "TRANSFER_CANCELLED": case "PEER_DECLINED": return "cancelled";
            default:return "failed";
        }
    }
    @Override public void onClose() {
        if (WorldTransferClient.housekeeping()) return;
        TransferEngine engine=WorldTransferClient.engine();
        if (engine!=null && engine.view().state==TransferEngine.State.OFFERED) engine.cancel();
        Screen next=server!=null && minecraft.getSingleplayerServer()!=server?new TitleScreen():parent;
        MinecraftUiCompat.setScreen(minecraft,next);
    }
    @Override public boolean isPauseScreen() { return false; }
}
