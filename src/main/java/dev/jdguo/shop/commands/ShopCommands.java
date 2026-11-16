package dev.jdguo.shop.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.catalog.CatalogEntry;
import dev.jdguo.shop.catalog.ConfigStore;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.PriceBook;
import dev.jdguo.shop.text.ShopText;
import dev.jdguo.shop.ui.ShopChestUiHooks;
import dev.jdguo.shop.ui.ShopListingView;
import dev.jdguo.shop.ui.ShopQuoteView;
import dev.jdguo.shop.ui.ShopTradeResult;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ShopCommands {
    private static final SuggestionProvider<ServerCommandSource> PERSONAL = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String name : ShopRuntime.bank.personalNames()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> ITEMS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (CatalogEntry entry : ShopRuntime.config.catalog()) {
            String id = entry.itemId();
            int colon = id.indexOf(':');
            String path = colon < 0 ? id : id.substring(colon + 1);
            if (id.startsWith(remaining) || path.startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    private ShopCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("shop")
                        .executes(ctx -> info(ctx.getSource(), ShopText.help()))
                        .then(literal("buy")
                                .then(argument("item", IdentifierArgumentType.identifier()).suggests(ITEMS)
                                        .then(argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> trade(ctx, true)))))
                        .then(literal("sell")
                                .then(argument("item", IdentifierArgumentType.identifier()).suggests(ITEMS)
                                        .then(argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> trade(ctx, false)))))
                        .then(literal("confirm").then(argument("quoteId", StringArgumentType.word())
                                .executes(ctx -> confirmPayout(ctx.getSource(),StringArgumentType.getString(ctx,"quoteId")))))
                        .then(literal("cancel").executes(ctx -> {
                            ServerPlayerEntity who=player(ctx.getSource());
                            if(who==null)return problem(ctx.getSource(),ShopText.notPlayer());
                            dev.jdguo.shop.ui.ShopPayoutPreviews.cancel(who.getUuid());
                            return info(ctx.getSource(),"已取消组合付款预览，未执行交易。");
                        }))
                        .then(literal("price")
                                .then(argument("item", IdentifierArgumentType.identifier()).suggests(ITEMS)
                                        .executes(ctx -> price(ctx))))
                        .then(literal("list")
                                .executes(ctx -> list(ctx.getSource(), 1))
                                .then(argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> list(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(literal("currency")
                                .executes(ctx -> currency(ctx.getSource()))
                                .then(literal("note").executes(ctx -> currencyNote(ctx.getSource())))
                                .then(literal("personal")
                                        .then(argument("name", StringArgumentType.string()).suggests(PERSONAL)
                                                .executes(ctx -> currencyPersonal(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))
                        .then(literal("admin").requires(ShopCommands::op)
                                .executes(ctx -> info(ctx.getSource(), ShopText.adminHelp()))
                                .then(literal("status").executes(ctx -> status(ctx.getSource())))
                                .then(literal("reload").executes(ctx -> reload(ctx.getSource())))
                                .then(literal("spread")
                                        .then(argument("fraction", StringArgumentType.word())
                                                .executes(ctx -> spread(ctx.getSource(), StringArgumentType.getString(ctx, "fraction")))))
                                .then(literal("step")
                                        .then(argument("fraction", StringArgumentType.word())
                                                .executes(ctx -> step(ctx.getSource(), StringArgumentType.getString(ctx, "fraction")))))
                                .then(literal("anchor")
                                        .then(argument("item", IdentifierArgumentType.identifier()).suggests(ITEMS)
                                                .then(argument("diamonds", StringArgumentType.word())
                                                        .executes(ctx -> anchor(ctx,
                                                                StringArgumentType.getString(ctx, "diamonds"))))))
                                .then(literal("reset")
                                        .then(argument("item", IdentifierArgumentType.identifier()).suggests(ITEMS)
                                                .executes(ctx -> reset(ctx)))))
        ));
    }

    private static int trade(CommandContext<ServerCommandSource> ctx, boolean buying) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = player(source);
        if (player == null) {
            return problem(source, ShopText.notPlayer());
        }
        String itemId = item(ctx);
        int count = IntegerArgumentType.getInteger(ctx, "count");
        PayChoice selected=ShopChestUiHooks.payer(player);
        if(!buying && selected.personal()) {
            var preview=ShopChestUiHooks.previewPayout(player,itemId,count,selected);
            return preview==null ? problem(source,"无法生成组合付款预览，请检查央行券资金或发行人挂单。") : info(source,preview.message());
        }
        ShopTradeResult result = buying
                ? ShopChestUiHooks.buy(player, itemId, count)
                : ShopChestUiHooks.sell(player, itemId, count);
        return result.success() ? okay(source, result.message()) : problem(source, result.message());
    }

    private static int confirmPayout(ServerCommandSource source,String quoteId) {
        ServerPlayerEntity player=player(source);
        if(player==null)return problem(source,ShopText.notPlayer());
        var reply=ShopChestUiHooks.confirmPayout(player,quoteId);
        if(reply.replacement()!=null)return info(source,reply.result().message()+"\n"+reply.replacement().message());
        return reply.result().success()?okay(source,reply.result().message()):problem(source,reply.result().message());
    }

    private static int price(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = player(source);
        ShopQuoteView view = player == null
                ? ShopChestUiHooks.quote(item(ctx), 1, true)
                : ShopChestUiHooks.quote(player, item(ctx), 1, true);
        return "ok".equals(view.code()) || "ready".equals(view.code()) || view.listed()
                ? info(source, view.message())
                : problem(source, view.message());
    }

    private static int list(ServerCommandSource source, int page) {
        int pages = ShopChestUiHooks.pages();
        if (page > pages) {
            return problem(source, ShopText.noSuchPage(page, pages));
        }
        ServerPlayerEntity player = player(source);
        PayChoice pay = player == null ? PayChoice.note(ShopRuntime.bank.currencyName()) : ShopChestUiHooks.payer(player);
        List<ShopListingView> rows = ShopChestUiHooks.listings(page, pay);
        String code = ShopText.settlementCode(ShopRuntime.bank);
        return info(source, ShopText.list(page, pages, rows, pay.name(), code));
    }

    private static int currency(ServerCommandSource source) {
        ServerPlayerEntity player = player(source);
        if (player == null) {
            return problem(source, ShopText.notPlayer());
        }
        if (ShopRuntime.state == null) {
            return problem(source, ShopText.worldNotReady());
        }
        return info(source, ShopText.currencyNow(ShopChestUiHooks.payer(player).name()));
    }

    private static int currencyNote(ServerCommandSource source) {
        ServerPlayerEntity player = player(source);
        if (player == null) {
            return problem(source, ShopText.notPlayer());
        }
        if (ShopRuntime.state == null) {
            return problem(source, ShopText.worldNotReady());
        }
        PayChoice choice = PayChoice.note(ShopRuntime.bank.currencyName());
        ShopRuntime.state.book().rememberPayer(player.getUuid(), choice);
        ShopRuntime.save();
        return info(source, ShopText.currencySet(choice.name()));
    }

    private static int currencyPersonal(ServerCommandSource source, String name) {
        ServerPlayerEntity player = player(source);
        if (player == null) {
            return problem(source, ShopText.notPlayer());
        }
        if (ShopRuntime.state == null) {
            return problem(source, ShopText.worldNotReady());
        }
        if (PayChoice.hardKey(name)) {
            return problem(source, ShopText.hardCurrency());
        }
        ShopBank.PersonalNoteRef found = ShopRuntime.bank.findPersonal(name);
        if (found == null) {
            return problem(source, ShopText.personalMissing());
        }
        if (!found.priced()) {
            return problem(source, ShopText.personalUnpriced());
        }
        PayChoice choice = PayChoice.personal(found.id(), found.name());
        ShopRuntime.state.book().rememberPayer(player.getUuid(), choice);
        ShopRuntime.save();
        return info(source, ShopText.currencySet(choice.name()));
    }

    private static int status(ServerCommandSource source) {
        int stored = ShopRuntime.state == null ? 0 : ShopRuntime.state.book().storedCount();
        return info(source, ShopText.status(ShopRuntime.config, stored, ShopRuntime.bank));
    }

    private static int reload(ServerCommandSource source) {
        try {
            ShopRuntime.config = ConfigStore.read();
            return info(source, ShopText.reloaded());
        } catch (IOException | RuntimeException error) {
            ShopRuntime.LOGGER.error("商店配置读不了", error);
            return problem(source, ShopText.reloadFailed());
        }
    }

    private static int spread(ServerCommandSource source, String raw) {
        BigDecimal spread = decimal(raw);
        if (spread == null) return problem(source, ShopText.badDecimal());
        try {
            ShopConfig next = ShopRuntime.config.withSpread(spread);
            ConfigStore.write(next);
            ShopRuntime.config = next;
            return info(source, "买卖价差已设为中心价的 " + spread.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()
                    + "%（买价加一半，卖价减一半；数量冲击和舍入另计）。");
        } catch (IllegalArgumentException error) {
            return problem(source, "价差比例必须在 0.0001 到 1 之间，例如 0.04 表示 4%。");
        } catch (IOException error) {
            ShopRuntime.LOGGER.error("商店价差配置写不了", error);
            return problem(source, "配置没有保存，价差保持原值。");
        }
    }

    private static int step(ServerCommandSource source, String raw) {
        BigDecimal step = decimal(raw);
        if (step == null) {
            return problem(source, ShopText.badDecimal());
        }
        try {
            ShopConfig next = ShopRuntime.config.withStep(step);
            ConfigStore.write(next);
            ShopRuntime.config = next;
            return info(source, ShopText.stepSet(next.stepFraction()));
        } catch (IllegalArgumentException error) {
            return problem(source, "步长必须大于 0，并且不能超过 0.2。已有的成交价没有改动。");
        } catch (IOException error) {
            ShopRuntime.LOGGER.error("商店配置写不了", error);
            return problem(source, "配置没有写进 config/shopmod.json。步长仍是原来的数值。");
        }
    }

    private static int anchor(CommandContext<ServerCommandSource> ctx, String raw) {
        ServerCommandSource source = ctx.getSource();
        String itemId = item(ctx);
        CatalogEntry entry = ShopRuntime.config.entry(itemId);
        if (entry == null) {
            return problem(source, ShopText.anchorMissing());
        }
        BigDecimal anchor = decimal(raw);
        if (anchor == null) {
            return problem(source, ShopText.badDecimal());
        }
        try {
            ShopConfig next = ShopRuntime.config.withAnchor(itemId, anchor);
            ConfigStore.write(next);
            ShopRuntime.config = next;
            return info(source, ShopText.anchorSet(entry.name(), anchor, itemId));
        } catch (IllegalArgumentException error) {
            return problem(source, "起点必须是大于 0 的钻石等值，并且不能超过 1000000。成交价没有改动。");
        } catch (IOException error) {
            ShopRuntime.LOGGER.error("商店配置写不了", error);
            return problem(source, "配置没有写进 config/shopmod.json。起点仍是原来的数值。");
        }
    }

    private static int reset(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        if (ShopRuntime.state == null) {
            return problem(source, ShopText.worldNotReady());
        }
        String itemId = item(ctx);
        CatalogEntry entry = ShopRuntime.config.entry(itemId);
        if (entry == null) {
            return problem(source, ShopText.anchorMissing());
        }
        PriceBook book = ShopRuntime.state.book();
        book.reset(itemId);
        ShopRuntime.save();
        return info(source, ShopText.resetDone(entry.name(), entry.anchorDiamonds()));
    }

    private static ServerPlayerEntity player(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return player;
        }
        return null;
    }

    private static String item(CommandContext<ServerCommandSource> ctx) {
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "item");
        return id.toString();
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw);
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static boolean op(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    private static int info(ServerCommandSource source, String body) {
        source.sendMessage(prefixed(body, Formatting.WHITE));
        return 1;
    }

    private static int okay(ServerCommandSource source, String body) {
        source.sendMessage(prefixed(body, Formatting.GREEN));
        return 1;
    }

    private static int problem(ServerCommandSource source, String body) {
        source.sendError(prefixed(body, Formatting.RED));
        return 0;
    }

    private static Text prefixed(String body, Formatting color) {
        return Text.literal("[商店] ").formatted(Formatting.GOLD)
                .append(Text.literal(body).formatted(color));
    }
}
