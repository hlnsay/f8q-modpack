package com.velorise.simplemap.client.gui;
import com.velorise.simplemap.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;

/** User-facing controls for the new overlays, with access to all original map settings. */
public final class MapStyleScreen extends Screen {
    private final MapScreen parent;private int x,y,w,h;
    public MapStyleScreen(MapScreen parent){super(Component.literal("Настройки карты"));this.parent=parent;}
    @Override protected void init(){
        w=Math.min(300,width-40);h=Math.min(244,height-20);x=(width-w)/2;y=(height-h)/2;
        addRenderableWidget(new MapActionButton(poiLabel(),x+12,y+40,w-24,22,b->{LocalPlaces.showPois=!LocalPlaces.showPois;b.setMessage(poiLabel());LocalPlaces.savePreferences();parent.refreshPlaces();}));
        addRenderableWidget(new ScaleSlider(x+12,y+78,w-24,"Метки",LocalPlaces.markerScale,v->LocalPlaces.markerScale=(float)v));
        addRenderableWidget(new ScaleSlider(x+12,y+113,w-24,"Маршрут",LocalPlaces.routeScale,v->LocalPlaces.routeScale=(float)v));
        addRenderableWidget(new MapActionButton(Component.literal("Остальные настройки карты"),x+12,y+151,w-24,22,b->minecraft.setScreen(new MapScreen.MapConfigScreen(this))));
        addRenderableWidget(new MapActionButton(Component.literal("Готово"),x+12,y+h-32,w-24,22,b->onClose()));
    }
    private Component poiLabel(){return Component.literal("Зоны интереса: "+(LocalPlaces.showPois?"Вкл":"Выкл"));}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){parent.editingPreview=true;try{parent.render(g,-1000,-1000,dt);}finally{parent.editingPreview=false;}g.pose().pushPose();g.pose().translate(0,0,200);MapPanels.editor(g,x,y,w,h,28,38);g.drawString(font,title,x+12,y+10,0xFFE9DEC0,false);super.render(g,mx,my,dt);g.pose().popPose();}
    @Override public void onClose(){LocalPlaces.savePreferences();if(minecraft!=null)minecraft.setScreen(parent);}
    private static final class ScaleSlider extends AbstractSliderButton {
        private final String label;private final DoubleConsumer change;
        ScaleSlider(int x,int y,int w,String label,float scale,DoubleConsumer change){super(x,y,w,22,Component.empty(),(LocalPlaces.clamp(scale)-.75)/2.25);this.label=label;this.change=change;updateMessage();}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float dt){
            g.fill(getX(),getY(),getX()+width,getY()+height,0xFF171813);g.renderOutline(getX(),getY(),width,height,0xFF706D59);
            int handle=getX()+2+(int)((width-8)*value);g.fill(handle,getY()+2,handle+4,getY()+height-2,0xFFB7AD8E);
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,getMessage(),getX()+width/2,getY()+7,0xFFE9DEC0);
        }
        @Override protected void updateMessage(){setMessage(Component.literal(label+" на мини-карте: "+String.format(java.util.Locale.ROOT,"%.2f×",.75+value*2.25)));}
        @Override protected void applyValue(){change.accept(.75+value*2.25);LocalPlaces.savePreferences();}
    }
}
