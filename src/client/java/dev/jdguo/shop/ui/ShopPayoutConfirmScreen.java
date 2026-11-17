package dev.jdguo.shop.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import java.util.List;

/** Read-only composition preview; only the bound quote token is sent back. */
final class ShopPayoutConfirmScreen extends Screen {
    private final ShopViewState state;
    private final JsonObject snapshot;
    private final JsonObject preview;
    private List<OrderedText> lines=List.of();
    private int left, panelWidth, scroll, top=46, bottom;
    private boolean submitted;
    ShopPayoutConfirmScreen(JsonObject data, ShopViewState state) {
        super(Text.literal("确认个人币收款")); this.state=state; snapshot=data; preview=data.getAsJsonObject("payoutPreview");
    }
    ShopViewState viewState() { return state; }
    @Override protected void init() {
        panelWidth=Math.max(80,Math.min(440,width-16)); left=(width-panelWidth)/2;
        bottom=Math.max(top+9,height-54);
        String message=ShopQuotes.text(snapshot,"message");
        String description=preview.get("description").getAsString();
        lines=textRenderer.wrapLines(Text.literal((message==null?"":message+"\n\n")+description
                +"\n\n以上实收为服务器逐件计价后的实际报价，不是商品卡静态估算。\n报价有效期 30 秒。收款金额或价格变化会要求重新确认。"),panelWidth-16);
        int buttonWidth=Math.max(28,(panelWidth-8)/2);
        addDrawableChild(ButtonWidget.builder(Text.literal("确认收款"),b->{
            if(submitted)return; submitted=true; b.active=false;
            ShopUiClient.confirmPayout(preview.get("quoteId").getAsString());
        }).dimensions(left,height-30,buttonWidth,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"),b->close())
                .dimensions(left+buttonWidth+8,height-30,buttonWidth,20).build());
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        renderBackground(context);
        context.drawText(textRenderer,title,left,18,0xFFE8D090,false);
        context.fill(left,top-4,left+panelWidth,bottom+4,0xD020252B);
        context.enableScissor(left,top,left+panelWidth,bottom);
        int y=top-scroll;
        for(OrderedText line:lines){context.drawText(textRenderer,line,left+8,y,0xFFE8E8E8,false);y+=12;}
        context.disableScissor();
        super.render(context,mouseX,mouseY,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double amount) {
        scroll=Math.max(0,Math.min(Math.max(0,lines.size()*12-(bottom-top)),scroll-(int)(amount*24)));
        return true;
    }
    @Override public void close() {
        // If confirmation was already sent, cancellation cannot undo that accepted order.
        if(!submitted)ShopUiClient.cancelPayout();
        client.setScreen(ShopScreen.restore(snapshot, state));
    }
}
