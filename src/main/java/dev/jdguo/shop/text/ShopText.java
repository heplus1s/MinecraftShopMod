package dev.jdguo.shop.text;

import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.MoneyScale;
import dev.jdguo.shop.ui.ShopListingView;
import dev.jdguo.shop.ui.ShopQuoteView;

import java.math.BigDecimal;
import java.util.List;

/** Player-facing Chinese. Command words stay in the command tree, not in these sentences. */
public final class ShopText {
    private ShopText() {}

    public static String help() {
        return """
                /shop buy <item> <count>
                /shop sell <item> <count>
                /shop confirm <quoteId>
                /shop cancel
                /shop price <item>
                /shop list [page]
                /shop currency
                /shop currency note
                /shop currency personal <name>
                /shop admin status
                /shop admin reload
                /shop admin spread <fraction>
                /shop admin step <fraction>
                /shop admin anchor <item> <diamonds>
                /shop admin maxcount <1–2304>
                /shop admin trading <item> <true|false>
                /shop admin reset <item>
                向服务器购买、向服务器出售、查询价格，或翻阅目录。结算货币用 currency 选择。调整配置需要管理员。""";
    }

    public static String adminHelp() {
        return """
                /shop admin status
                /shop admin reload
                /shop admin spread <fraction>
                /shop admin step <fraction>
                /shop admin anchor <item> <diamonds>
                /shop admin maxcount <1–2304>
                /shop admin trading <item> <true|false>
                /shop admin reset <item>
                查看商店和中央银行的连接，或调整价差、步长与起点。已有的成交价不会被整表清除。""";
    }

    public static String notPlayer() {
        return "该指令需要由玩家执行。";
    }

    public static String noQuote() {
        return "暂无牌价。";
    }

    public static String hardCurrency() {
        return "铜锭和钻石不能作为商店货款。请选择央行券，或选择一种个人币。一笔订单只用一种货币。";
    }

    public static String personalMissing() {
        return "没有找到这个名字的个人币。";
    }

    public static String personalUnpriced() {
        return "这张个人币暂无牌价，不能选作结算货币。";
    }

    public static String currencyNow(String name) {
        return "当前结算货币为" + name + "。货款按四位小数记账。铜锭和钻石不参与结算。";
    }

    public static String currencySet(String name) {
        return "结算货币已改为" + name + "。购买按所选币支付；出售选个人币时先预览实收金额，支持小数；挂单不足时不会自动改付央行券。";
    }

    public static String zeroCharge() {
        return "这笔货款按四位小数计算后为零，不能入账。没有改记成整数，也没有改用第二种货币。";
    }

    public static String badCount(int max) {
        return "数量必须是 1 到 " + max + " 之间的整数。";
    }

    public static String noRoom() {
        return "背包空位不足，放不下这些物品。";
    }

    public static String notEnoughItems(String name, int count, int have) {
        return "背包里可以出售的普通" + name + "不足。需要 " + count + " 个，当前有 " + have
                + " 个。带有附加数据的物品不能出售给商店。";
    }

    public static String worldNotReady() {
        return "商店的价格还没有从世界存档读出，请稍后再试。";
    }

    public static String badItem() {
        return "暂无牌价。";
    }

    public static String badPrice(String itemId) {
        return "这种物品的已保存价格无法读取，其它物品的价格没有改动。管理员可以执行 /shop admin reset "
                + itemId + "，只把这一项回到起点。";
    }

    public static String bankAbsent() {
        return "中央银行没有加载。购买和出售都要把货款记在储蓄总池的商店经营账户里，当前无法结算。";
    }

    public static String bankNotReady() {
        return "中央银行账本还没有准备好，请稍后再试。";
    }

    public static String bankHookMissing() {
        return "中央银行还没有提供商店经营账户。该账户必须是储蓄总池中的分户，并按活期存款计息，"
                + "且不得与发行准备金、个人发行金库或土地出让账户混在一起。在此之前不能买卖。";
    }

    public static String fxUnavailable() {
        return "中央银行暂时不能提供央行券对钻石的汇率，这笔交易无法换算。";
    }

    public static String insufficientFunds(String currency, BigDecimal need, BigDecimal have) {
        return "所选货币余额不足。需要" + currency + " " + MoneyScale.display(need)
                + "，当前余额 " + MoneyScale.display(have) + "。这一笔没有成交，也没有少买一部分。";
    }

    public static String insufficientShop(String currency, BigDecimal need, BigDecimal have) {
        return "商店在这种货币上的余额不足。需要" + currency + " " + MoneyScale.display(need)
                + "，当前余额 " + MoneyScale.display(have) + "。这一笔没有成交，也没有少收一部分。";
    }

    public static String bankRejected() {
        return "中央银行没有完成本次结算，物品和货款都没有改动。";
    }

    public static String refunded() {
        return "物品没有放入背包，货款已退回你的活期存款。请再试一次。";
    }

    public static String settleInconsistent() {
        return "货款已经划出，但物品没有放入背包，退回也没有成功。请管理员核对商店经营账户和玩家的活期存款。";
    }

    public static String bought(String name, int count, String currency, BigDecimal notes,
                                 BigDecimal after, BigDecimal anchor, BigDecimal balance) {
        String text = "已向服务器购入" + name + " " + count + " 个，支付" + currency + " "
                + MoneyScale.display(notes) + "。成交后参考单价：" + (after == null ? "暂无牌价" : MoneyScale.display(after) + " " + currency)
                + "；初始参考单价：" + (anchor == null ? "暂无牌价" : MoneyScale.display(anchor) + " " + currency) + "。";
        if (balance != null) {
            text += "该货币余额为" + currency + " " + MoneyScale.display(balance) + "。";
        }
        return text;
    }

    public static String sold(String name, int count, String currency, BigDecimal notes,
                               BigDecimal after, BigDecimal anchor, BigDecimal balance) {
        String text = "已向服务器出售" + name + " " + count + " 个，收入" + currency + " "
                + MoneyScale.display(notes) + "。成交后参考单价：" + (after == null ? "暂无牌价" : MoneyScale.display(after) + " " + currency)
                + "；初始参考单价：" + (anchor == null ? "暂无牌价" : MoneyScale.display(anchor) + " " + currency) + "。";
        if (balance != null) {
            text += "该货币余额为" + currency + " " + MoneyScale.display(balance) + "。";
        }
        return text;
    }

    public static String quote(ShopQuoteView view, String currency) {
        if (!view.listed()) {
            return view.message();
        }
        StringBuilder text = new StringBuilder();
        text.append(view.itemName()).append("（").append(view.itemId()).append("）：");
        if (view.diamondEach() == null || view.diamondEach().signum() <= 0) {
            text.append("暂无牌价。");
        } else {
            text.append("现价 ").append(MoneyScale.display(view.diamondEach())).append(" 钻石等值，起点 ")
                    .append(MoneyScale.display(view.anchor())).append("。");
            if (view.charge() == null) {
                text.append("所选货币暂时不能换算，没有改用第二种货币。");
            } else {
                text.append("按当前汇率，这一次合计").append(currency).append(" ")
                        .append(MoneyScale.display(view.charge())).append("。");
            }
        }
        text.append(settlementFoot(view.code()));
        return text.toString();
    }

    public static String list(int page, int pages, List<ShopListingView> rows, String currency, String settlementCode) {
        if (rows.isEmpty() && pages <= 1) {
            return "商店目录是空的。\n" + settlementFoot(settlementCode);
        }
        StringBuilder text = new StringBuilder();
        text.append("商店目录，第 ").append(page).append(" 页，共 ").append(pages).append(" 页。");
        for (ShopListingView row : rows) {
            text.append("\n").append(row.itemName()).append("  ").append(row.itemId());
            if (row.diamondEach() == null || row.diamondEach().signum() <= 0) {
                text.append("  暂无牌价");
            } else {
                text.append("  现价 ").append(MoneyScale.display(row.diamondEach())).append(" 钻石等值");
                if (row.notesEach() != null) {
                    text.append("  约合").append(currency).append(" ").append(MoneyScale.display(row.notesEach()));
                }
            }
        }
        text.append("\n").append(settlementFoot(settlementCode));
        return text.toString();
    }

    public static String noSuchPage(int page, int pages) {
        return "没有第 " + page + " 页，目前共 " + pages + " 页。";
    }

    public static String reloaded() {
        return "已重新读取配置。世界里已经形成的价格都还在。新加入目录、且还没有成交价的物品，按配置里的起点计价。";
    }

    public static String reloadFailed() {
        return "配置文件读不了，仍使用读取之前的配置。世界价格没有改动。";
    }

    public static String stepSet(BigDecimal step) {
        return "价格步长已改为 " + MoneyScale.display(step)
                + "。每成交一个物品，价格按当前价格移动这一小步。购买向上，出售向下，不会被拉回起点。已写入配置。";
    }

    public static String anchorSet(String name, BigDecimal anchor, String itemId) {
        return "已把" + name + "的起点改为 " + MoneyScale.display(anchor)
                + " 钻石等值。已经成交过的价格没有改。若要让现价回到新起点，请再执行 /shop admin reset " + itemId + "。";
    }

    public static String anchorMissing() {
        return "目录里没有这种物品。要新增物品，请写入 config/shopmod.json 后执行 /shop admin reload。已经保存的价格不会因此被清除。";
    }

    public static String badDecimal() {
        return "请写一个正的十进制数，例如 0.002。";
    }

    public static String resetDone(String name, BigDecimal anchor) {
        return "已清除" + name + "的成交价。下一次交易按起点 " + MoneyScale.display(anchor)
                + " 钻石等值计算。其它物品没有改动。";
    }

    public static String status(ShopConfig config, int storedPrices, ShopBank bank) {
        String currency = currency(bank);
        StringBuilder text = new StringBuilder();
        text.append("目录物品 ").append(config.catalog().size()).append(" 种，已保存成交价 ")
                .append(storedPrices).append(" 条。");
        text.append("\n价格步长 ").append(MoneyScale.display(config.stepFraction()))
                .append("，单次数量上限 ").append(config.maxCount()).append("。");
        text.append("\n价格下限 ").append(MoneyScale.display(config.minDiamondPrice()))
                .append(" 钻石等值，只用于防止价格变成零，不是起点。");
        text.append("\n买卖按四位小数记账。玩家选择央行券或一种个人币，一笔订单只用这一种。铜锭和钻石不能付货款。余额不够则整笔拒绝。");
        text.append("\n配置文件 config/shopmod.json。成交价写在世界存档的 data 目录，升级时保留。");
        if (!bank.modLoaded()) {
            text.append("\n中央银行没有加载，买卖不能入账。钻石等值价格仍可查询。");
        } else if (!bank.ledgerReady()) {
            text.append("\n中央银行已加载，账本还没有准备好。");
        } else {
            text.append("\n中央银行已加载，结算货币为").append(currency).append("。");
            var note = dev.jdguo.shop.bank.PayChoice.note(currency);
            if (bank.unitsPerDiamond(note) == null) {
                text.append("\n央行券对钻石的汇率暂时无法报价。");
            } else {
                text.append("\n每个钻石合").append(currency).append(" ")
                        .append(MoneyScale.display(bank.unitsPerDiamond(note))).append("。");
            }
            if (bank.copperPerDiamond() != null) {
                text.append("每个钻石合铜锭 ").append(bank.copperPerDiamond().toString()).append("。铜锭只作汇率参照，不付货款。");
            }
            if (bank.annualInterestFraction() != null) {
                text.append("\n活期存款年利率 ").append(MoneyScale.display(bank.annualInterestFraction()))
                        .append("，按 365 个游戏日计算。利息由中央银行从手续费留存里支付，商店不另行造币。");
            }
            if (bank.savingsPool() != null) {
                text.append("\n储蓄总池（").append(currency).append("）当前为 ")
                        .append(MoneyScale.display(new BigDecimal(bank.savingsPool()))).append("。");
            }
            if (bank.operatingAccountReady()) {
                text.append("\n商店经营账户（").append(currency).append("）余额为 ")
                        .append(MoneyScale.display(bank.operatingBalance(note)))
                        .append("。该余额属于储蓄总池。");
            } else if (bank.operatingHookInstalled()) {
                text.append("\n商店经营账户的接口已经存在，但账户还没有准备好。");
            } else {
                text.append("\n商店经营账户还没有接入。接入后其余额应计入储蓄总池，并按活期存款计息。");
            }
        }
        text.append("\n本商店不使用发行准备金、个人发行金库和土地出让账户。土地出让账户属于服务器土地出售。");
        text.append("\n央行券的发行、兑现和票据往来仍由中央银行处理，商店不另行办理。");
        return text.toString();
    }

    public static String settlementFoot(String code) {
        if (code == null || "ok".equals(code) || "ready".equals(code)) {
            return "结算只使用当前选定的一种货币，按四位小数记账。";
        }
        return switch (code) {
            case "bank_absent" -> "当前不能买卖，因为中央银行没有加载。";
            case "bank_not_ready", "world_not_ready" -> "当前不能买卖，因为账本还没有准备好。";
            case "bank_hook_missing" -> "当前不能买卖，因为商店经营账户还没有接入。";
            case "fx_unavailable" -> "当前不能换算成所选货币。";
            case "hard_currency" -> "铜锭和钻石不能作为商店货款。";
            default -> "";
        };
    }

    public static String currency(ShopBank bank) {
        if (bank == null || bank.currencyName() == null || bank.currencyName().isBlank()) {
            return "央行券";
        }
        return bank.currencyName();
    }

    public static String settlementCode(ShopBank bank) {
        if (bank == null || !bank.modLoaded()) {
            return "bank_absent";
        }
        if (!bank.ledgerReady()) {
            return "bank_not_ready";
        }
        var note = dev.jdguo.shop.bank.PayChoice.note(currency(bank));
        if (bank.unitsPerDiamond(note) == null || bank.unitsPerDiamond(note).signum() <= 0) {
            return "fx_unavailable";
        }
        if (!bank.operatingAccountReady()) {
            return "bank_hook_missing";
        }
        return "ready";
    }
}
