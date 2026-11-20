package dev.jdguo.shop.ui;

import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.bank.PayChoice;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.math.BigDecimal;
import java.util.*;

/** Server-driven vanilla menu; icons never become inventory items. */
public final class ShopChestScreen extends GenericContainerScreenHandler {
    private final ServerPlayerEntity viewer;
    private final SimpleInventory menu;
    private final List<String> shown=new ArrayList<>();
    private int page,count=1;
    private String selected;
    private boolean buying=true;
    private PayChoice pay;
    private ShopQuoteView quote;
    private ShopPayoutPreviews.Preview payoutPreview;
    private String status="选择商品查看整单报价；不会立即扣款。";
    private ShopChestScreen(int id,PlayerInventory inv,ServerPlayerEntity player,SimpleInventory menu) {
        super(ScreenHandlerType.GENERIC_9X6,id,inv,menu,6);this.viewer=player;this.menu=menu;pay=ShopChestUiHooks.payer(player);render();
    }
    public static void open(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inv,who)->new ShopChestScreen(id,inv,player,new SimpleInventory(54)),Text.literal("商店 · 银行账户结算")));
    }
    private void icon(int slot,Item item,String title,String... lines) {
        ItemStack stack=new ItemStack(item);stack.setCustomName(Text.literal(title));NbtList lore=new NbtList();
        for(String line:lines)lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(line))));stack.getOrCreateSubNbt("display").put("Lore",lore);menu.setStack(slot,stack);
    }
    private void render() {
        menu.clear();shown.clear();int size=ShopRuntime.config.catalog().size(),pages=Math.max(1,(size+26)/27);page=Math.max(0,Math.min(page,pages-1));
        for(int i=page*27;i<Math.min(size,(page+1)*27);i++) {
            var row=ShopRuntime.config.catalog().get(i);shown.add(row.itemId());Item item=Registries.ITEM.get(new Identifier(row.itemId()));
            icon(i-page*27,item==Items.AIR?Items.PAPER:item,row.name(),"点击选择，尚未交易");
        }
        boolean mixed=!buying && pay.personal();
        payoutPreview=selected!=null && mixed?ShopChestUiHooks.previewPayout(viewer,selected,count,pay):null;
        if(!mixed)ShopPayoutPreviews.cancel(viewer.getUuid());
        quote=selected==null || mixed?null:ShopChestUiHooks.quote(selected,count,buying,pay);
        long selectedStock = selected == null ? 0 : ShopRuntime.state.book().saved().stock(selected);
        icon(28, Items.PAPER, selected == null ? "未选择商品" : ShopRuntime.config.entry(selected).name(),
                selected == null ? "从上方目录选择" : selectedStock == 0 ? "缺货 · 暂停出售" : "现有库存：" + selectedStock + " 件");
        icon(29,Items.REDSTONE,"数量 -1");icon(30,Items.REDSTONE,"数量 -16");icon(31,Items.PAPER,"数量 "+count);icon(32,Items.GLOWSTONE_DUST,"数量 +16");icon(33,Items.GLOWSTONE_DUST,"数量 +1");
        icon(37,buying?Items.CHEST:Items.HOPPER,buying?"购买模式 · 点击切换出售":"出售模式 · 点击切换购买");
        String centralName=ShopRuntime.bank.currencyName();
        icon(39,Items.GOLD_INGOT,"结算货币："+pay.name(),"点击切换"+centralName+"/可用个人券",mixed?"从发行人本人挂单认购，支持小数；不足则拒绝":"不会自动换汇");
        if(selected!=null && mixed) {
            if(payoutPreview==null) icon(41,Items.BARRIER,"暂无可执行个人币报价",dev.jdguo.shop.trade.TradeDesk.privatePayoutUnavailable(ShopRuntime.session(),selected,count,pay));
            else {
                var bank=payoutPreview.plan().bankQuote();
                icon(41,Items.PAPER,"个人币收款预览",
                        "私币："+bank.get("privateAmount").getAsString()+" "+pay.name(),
                        "不使用央行券补零头",
                        "成交价："+bank.get("unitPrice").getAsString()+" "+centralName+"/币",
                        "总应付："+payoutPreview.plan().centralBudget().toPlainString()+" "+centralName,
                        "发行人储蓄入款："+bank.get("issuerProceeds").getAsString()+" "+centralName);
                icon(43,Items.EMERALD,"确认出售","确认上列商品数量与个人币实收金额","报价变化后须再次确认；准备金不变");
            }
        }
        if(quote!=null) {
            icon(41,Items.PAPER,"整单金额："+(quote.charge()==null?"暂无有效报价":quote.charge().toPlainString())+" "+pay.name(),quote.message()==null?"":quote.message());
            if(quote.charge()!=null && "ready".equals(quote.code())) icon(43,Items.EMERALD,buying?"确认购买":"确认出售","数量 "+count,"整单 "+quote.charge().toPlainString()+" "+pay.name(),"价格变动时需重新确认；从银行账户结算");
        }
        icon(45,Items.ARROW,"上一页");icon(46,Items.ARROW,"下一页");icon(48,Items.CLOCK,"刷新 · "+(page+1)+"/"+pages);icon(49,Items.PAPER,"操作提示",status);icon(53,Items.BARRIER,"关闭");sendContentUpdates();
    }
    private void nextCurrency() {
        List<PayChoice> options=new ArrayList<>();options.add(PayChoice.note(ShopRuntime.bank.currencyName()));
        for(String name:ShopRuntime.bank.personalNames()) { var ref=ShopRuntime.bank.findPersonal(name);if(ref!=null && ref.priced())options.add(PayChoice.personal(ref.id(),ref.name())); }
        int at=-1;for(int i=0;i<options.size();i++)if(options.get(i).key().equals(pay.key()))at=i;
        pay=options.get((at+1)%options.size());
    }
    @Override public void onSlotClick(int slot,int button,SlotActionType action,PlayerEntity player) {
        if(player!=viewer || viewer.currentScreenHandler!=this || !viewer.isAlive() || viewer.isSpectator() || action!=SlotActionType.PICKUP || button!=0 || slot<0 || slot>=54)return;
        if(slot==53){ShopPayoutPreviews.cancel(viewer.getUuid());viewer.closeHandledScreen();return;}
        if(slot<shown.size())selected=shown.get(slot);
        else switch(slot) {
            case 29 -> count=Math.max(1,count-1);
            case 30 -> count=Math.max(1,count-16);
            case 32 -> count=Math.min(ShopRuntime.config.maxCount(),count+16);
            case 33 -> count=Math.min(ShopRuntime.config.maxCount(),count+1);
            case 37 -> buying=!buying;
            case 39 -> nextCurrency();
            case 45 -> {page--;selected=null;}
            case 46 -> {page++;selected=null;}
            case 43 -> confirm();
            default -> { }
        }
        render();
    }
    private void confirm() {
        if(!buying && pay.personal()) {
            if(selected==null || payoutPreview==null)return;
            var reply=ShopChestUiHooks.confirmPayout(viewer,payoutPreview.id());
            status=reply.result().message();viewer.sendMessage(Text.literal(status),false);
            if(reply.replacement()==null){selected=null;payoutPreview=null;}
            return;
        }
        if(selected==null || quote==null || quote.charge()==null || !"ready".equals(quote.code()))return;
        ShopQuoteView current=ShopChestUiHooks.quote(selected,count,buying,pay);
        if(!QuoteGuard.same(quote.charge(),current.charge()) || !"ready".equals(current.code())) {status="报价已经变化或暂不可用，请查看新金额后再次确认。";return;}
        ShopTradeResult result=buying?ShopChestUiHooks.buy(viewer,selected,count,pay):ShopChestUiHooks.sell(viewer,selected,count,pay);
        status=result.message();viewer.sendMessage(Text.literal(status),false);selected=null;quote=null; // One click consumes selection, including failures.
    }
    @Override public void onClosed(PlayerEntity player) {
        ShopPayoutPreviews.cancel(player.getUuid()); super.onClosed(player);
    }
    @Override public ItemStack quickMove(PlayerEntity player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canUse(PlayerEntity player){return player==viewer && viewer.isAlive() && !viewer.isSpectator();}
}
