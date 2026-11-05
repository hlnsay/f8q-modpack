package com.velorise.simplemap.client.gui;

import com.velorise.simplemap.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local-only edits. The network's original place and other players' maps stay independent. */
public final class PlaceEditorScreen extends Screen {
    private final MapScreen parent;private final PlaceEntry entry;
    private EditBox name,color;private Button save;private int x,y,w,h;private String error="";
    public PlaceEditorScreen(MapScreen parent,PlaceEntry entry){super(Component.literal("Изменить место"));this.parent=parent;this.entry=entry;}
    @Override protected void init(){
        w=Math.min(236,width-52);h=Math.min(288,height-20);x=42;y=(height-h)/2;
        name=new EditBox(font,x+12,y+48,w-24,20,Component.literal("Название"));name.setMaxLength(64);name.setValue(entry.title());addRenderableWidget(name);
        color=new EditBox(font,x+12,y+96,w-24,20,Component.literal("Цвет Minecraft"));color.setMaxLength(24);color.setValue(String.format("#%06X",entry.color()));color.setResponder(s->validate());addRenderableWidget(color);
        int cell=Math.max(18,(w-24)/8),size=Math.min(21,cell-3);
        for(int i=0;i<16;i++){final int index=i;addRenderableWidget(new ColorSwatch(x+12+(i%8)*cell,y+130+(i/8)*27,size,LocalPlaces.COLORS[i],()->color.setValue(LocalPlaces.COLOR_IDS[index]))).setTooltip(Tooltip.create(Component.literal(LocalPlaces.COLOR_IDS[i]+" · §"+Integer.toHexString(i))));}
        save=new MapActionButton(Component.literal("Сохранить"),x+12,y+h-30,(w-30)/2,20,b->{Integer rgb=LocalPlaces.parseColor(color.getValue());if(rgb!=null){LocalPlaces.edit(entry.id(),name.getValue(),rgb);parent.refreshPlaces();onClose();}});addRenderableWidget(save);
        addRenderableWidget(new MapActionButton(Component.literal("Отмена"),x+18+(w-30)/2,y+h-30,(w-30)/2,20,b->onClose()));validate();setInitialFocus(name);
    }
    private void validate(){boolean valid=color==null||LocalPlaces.parseColor(color.getValue())!=null;error=valid?"":"Неизвестный ID цвета";if(save!=null)save.active=valid;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        parent.editingPreview=true;try{parent.render(g,mx,my,dt);}finally{parent.editingPreview=false;}
        g.pose().pushPose();g.pose().translate(0,0,200);MapPanels.editor(g,x,y,w,h,28,36);
        Integer preview=LocalPlaces.parseColor(color.getValue());
        if(entry.location()!=null)TeamMarks.icon(g,TeamMarks.placeIcon(entry.location().kind()),x+17,y+14,9,preview==null?entry.color():preview);else entry.icon(g,x+17,y+14,9);g.drawString(font,"Изменить место",x+33,y+10,0xFFE9DEC0,false);
        g.drawString(font,"Название",x+12,y+35,0xFFD7D0BC,false);g.drawString(font,"ID цвета Minecraft",x+12,y+83,0xFFD7D0BC,false);
        g.drawString(font,"red, gold, &c, 0–15, #RRGGBB",x+12,y+119,0xFFAAA38F,false);
        g.drawString(font,"Сохраняется только у тебя",x+12,y+194,0xFFAAA38F,false);
        if(!error.isEmpty())g.drawString(font,error,x+12,y+h-46,0xFFFF7777,false);
        super.render(g,mx,my,dt);g.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(mx>=x&&mx<=x+w&&my>=y&&my<=y+h)return super.mouseClicked(mx,my,button)||true;if(button==0)return parent.mouseClicked(mx,my,button);return true;}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return super.mouseDragged(mx,my,button,dx,dy)||parent.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){parent.mouseReleased(mx,my,button);return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(mx>=x&&mx<=x+w&&my>=y&&my<=y+h)return true;return parent.mouseScrolled(mx,my,sx,sy);}
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
    private static final class ColorSwatch extends AbstractButton {
        private final int color;private final Runnable click;
        ColorSwatch(int x,int y,int size,int color,Runnable click){super(x,y,size,size,Component.empty());this.color=color;this.click=click;}
        @Override public void onPress(){click.run();}
        @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){g.fill(getX()+2,getY()+2,getX()+width-2,getY()+height-2,0xff000000|color);if(isHoveredOrFocused())g.renderOutline(getX(),getY(),width,height,0xFFE9DEC0);}
    }
}
