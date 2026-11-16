# MinecraftShopMod

Fabric 1.20.1 商店模组：玩家和服务器买卖物品，货款记在央行储蓄里的商店经营账户。价格先按获取难度对照钻石定一个起点，再随买卖小幅波动，长期可以离起点较远。

A Fabric 1.20.1 shop where players buy from and sell to the server. Payments go to a shop operating account under central-bank savings. Prices start from gather difficulty relative to diamonds, then drift with supply and demand.

支持指令、实体柜台和原版箱子交易界面。可选的独立客户端界面放在 MinecraftUIMod。不装界面包也可以进服。

Commands come first. A chest GUI is not included yet, but the hook is reserved. Optional client UI will live in MinecraftUIMod. Vanilla clients can still join.

## 向谁买卖

玩家向服务器购买，也可以把物品卖回服务器。这不是玩家之间在领地上开的铺子，也不是服务器出售土地。土地出让账户留给卖地，商店不用它。

Players buy from the server and sell back to the server. This is not a player stall on claimed land, and it is not a land sale. The land-grant account stays with land sales.

## 货款记在哪里

货款走中央银行的账面，不另造一种货币。玩家的活期存款和商店经营账户都是储蓄总池里的分户：每人一条分户，总池是这些分户加在一起。购买时，钱从玩家的活期存款划进商店经营账户。出售时，钱从商店经营账户划回玩家。经营账户可以按活期存款获得利息，利息由中央银行从手续费留存里支付。

这个账户不是发行准备金，也不是个人发行用的金库。银行券的发行、兑现和票据仍由中央银行办理，商店不重复做。

服务器没装中央银行时，商店仍能加载。价格可以按钻石等值查看。真正买卖时，聊天里会说明现在不能入账。

中央银行目前还没有开出商店经营账户。在它开出之前，买卖会被拒绝，不会把货款记到别的账上。给银行作者的说明在 `docs/bank-hooks.md`。

Payments are book entries in the central bank. There is no new currency. Personal demand deposits and the shop operating account are sub-ledgers of the same savings pool. A purchase moves money from the player into the operating account. A sale moves it back. The operating account can earn demand-deposit interest, paid by the bank from retained fees.

The account is not an issuance reserve and not a personal issuance vault. Bank-note issue, redemption, and paper stay in the bank mod.

Without the bank, the shop still loads. Diamond-equivalent prices can be read. Buy and sell say in chat that nothing can be posted. The bank does not expose the operating account yet, so trades are refused until it does. The note for the bank is `docs/bank-hooks.md`.

## 价格怎么走

每种物品有一个起点，表示它相对于钻石有多难获得。钻石的起点是 1。这只是开张时的价格，不表示以后就该停在这里。

成交时按中央银行当时的汇率，把钻石等值换成玩家选定的一种货币。玩家可以选银行券，也可以选一种个人银行券。一笔订单只用这一种，不找铜锭，也不找钻石。铜锭和钻石本身不能当货款。

单价、这一笔的合计、记下的新价格，以及用来比较的余额，都算到小数点后四位。整数零显示成 `0`。不到一个银行券也可以买，只要选定货币的余额够付这四位小数。余额不够就整笔拒绝，不会只成交一部分。没有起点、也没有已经成交的价格时，显示「暂无牌价」，不会临时编一个价。

每成交一个物品，价格按当前价格小幅移动。玩家购买，价格上升。玩家出售，价格下降。没有把价格拉回起点的力量。买卖多了，价格可以离起点很远，以便靠近游戏里真实的供求。

一步的默认大小是 0.002，也就是当前价格的千分之二。一次购买多个时，价格沿着这一小步往上走，货款按走过的价格相加，再折成选定货币的四位小数。

Prices start from gathering difficulty relative to one diamond. That anchor is only the opening price. At the moment of a trade it is converted, at the bank's current rate, into the one currency the player selected: the central bank note, or one personal note. Copper and diamond are not payment currencies, and one order does not use a second currency.

Unit prices, order totals, posted steps, and the balances compared for a trade are kept to four decimal places. Integer zero is shown as `0`. An order below one unit is allowed when the selected balance covers that four-decimal total. A short balance refuses the whole order. A missing anchor or live quote is shown as 暂无牌价 and is not replaced with a made-up price.

Each item in a trade takes one small step off the current price. Buying steps up. Selling steps down. Nothing pulls the price back to the anchor, so a long run of trades can travel far from the start. The default step is 0.002 of the current price.

## 指令

指令的写法是英文。聊天里的说明是中文。

Command words are English. Chat replies are Chinese.

```
/shop
/shop buy <item> <count>
/shop sell <item> <count>
/shop price <item>
/shop list [page]
/shop currency
/shop currency note
/shop currency personal <name>
/shop admin status
/shop admin reload
/shop admin step <fraction>
/shop admin anchor <item> <diamonds>
/shop admin reset <item>
```

- `/shop` 列出上面的写法。
- `/shop buy <item> <count>` 向服务器购买。`<item>` 用物品标识，例如 `minecraft:diamond`。`<count>` 是数量。
- `/shop sell <item> <count>` 把背包里的普通物品卖给服务器。带有附加数据的物品不会被收走。
- `/shop price <item>` 查看现价、起点，以及按当前汇率换成所选货币后的四位小数金额。没有牌价时显示「暂无牌价」。
- `/shop list [page]` 翻阅目录。页码从 1 开始。
- `/shop currency` 查看当前结算货币。
- `/shop currency note` 改用银行券结算。
- `/shop currency personal <name>` 改用一种个人银行券结算。`<name>` 是这张券已登记的名字。
- `/shop admin status` 查看步长、汇率、储蓄总池和商店经营账户是否已经接上。需要管理员。
- `/shop admin reload` 重新读取配置。已经成交的价格还在。
- `/shop admin step <fraction>` 修改每成交一个物品的步长，例如 `0.002`。不能超过 `0.2`。
- `/shop admin anchor <item> <diamonds>` 修改起点。已经成交过的价格不变。
- `/shop admin reset <item>` 只清除这一种物品的成交价，让它回到当前起点。其它物品不动。

新增目录物品时，把一行写进配置，再执行 `/shop admin reload`。不要靠删存档来改价格。

`/shop` prints the commands. `buy` and `sell` trade with the server. `price` and `list` only look. Admin commands need operator permission. `step` and `anchor` change the configuration. `reset` clears one saved price. Reload keeps prices that have already traded.

商店刚开张时，经营账户里没有钱，所以要先有人向服务器购买，之后服务器才付得起收购。余额不足时，聊天里会说明。

The operating account starts empty. Someone has to buy from the server before the server can pay for a sale.

## 没有中央银行的时候

模组仍然加载。`/shop price` 和 `/shop list` 可以看钻石等值。`/shop buy` 和 `/shop sell` 会说明中央银行没有加载，或者经营账户还没有接入，并且不会改动物品和价格。

The mod still loads. Price and list keep working in diamond equivalents. Buy and sell explain that the bank is missing or that the operating account is not connected, and they leave items and prices alone.

## 箱子界面和独立客户端

柜台和 `/shop ui` 打开原版箱子界面，调用 `dev.jdguo.shop.ui.ShopChestUiHooks` 报价、购买和出售，计价和入账仍由既有交易服务处理。

可选的客户端以后放在 [MinecraftUIMod](https://github.com/heplus1s/MinecraftUIMod)。它如果要同步，使用服务器上的 `shopmod:hello`、`shopmod:action` 和 `shopmod:sync`。没有安装这个界面的原版客户端可以照常进服，服务器不会向它发送这个频道。

The counter and `/shop ui` open a vanilla chest screen using `dev.jdguo.shop.ui.ShopChestUiHooks`. Optional client UI belongs in MinecraftUIMod and may use the `shopmod` hello, action, and sync payloads. Vanilla clients can join without that pack.

## 配置和存档

配置在 `config/shopmod.json`。第一次运行会写出一份默认目录。目录里的 `anchorDiamonds` 是起点，只对还没有成交价的物品生效。

成交价写在世界存档里：`data/shopmod.dat`，旁边还有一份 `data/shopmod-prices.json`。升级时保留这些文件。读不出来时，服务器会停下来，而不是改用空价格。

Settings live in `config/shopmod.json`. Traded prices live in the world folder, in `data/shopmod.dat` and `data/shopmod-prices.json`. Upgrades keep that data. A save that cannot be read stops the server instead of starting from empty prices.

## 构建

需要 Java 17。在仓库根目录执行：

```
./gradlew build
```

给服务器用的 jar 在 `build/libs/shopmod-1.0.0.jar`。把它和 Fabric Loader、Fabric API 一起放进服务器的 `mods`。原版客户端不用安装。单人游戏要在带 Fabric 的客户端里装同一份 jar，因为单人世界的服务器跑在那个客户端里。

中央银行是可选的。装上以后，汇率和报价会接上；经营账户要等银行补上 `docs/bank-hooks.md` 里的类。

Java 17 is required. `./gradlew build` writes `build/libs/shopmod-1.0.0.jar`. Put that jar, Fabric Loader, and Fabric API on the server. Vanilla clients do not install it. Singleplayer needs the same jar on a Fabric client, because that client runs the world. The bank is optional.

许可证为 MIT，版权归 heplus1s。

The license is MIT. Copyright heplus1s.

## 实体商店柜台（原版客户端可见）

管理员看准五格内实心方块，确保上方两格空气，执行：

- `/shop outlet set spawn`：建立或移动名为 `spawn` 的商店柜台。
- `/shop outlet list`：列出当前世界的网点。
- `/shop outlet remove spawn`：删除网点及其悬浮标志，不破坏底座。
- `/shop ui`：保留远程打开原版箱子界面的入口；原有买卖命令不变。

所有网点配置命令需要 OP 2。柜台是原版 Interaction/BlockDisplay 实体，显示木质柜台、浅色台面与绿色边饰；悬浮 TextDisplay 显示“商店”和网点名。无需资源包或客户端模组。网点保存在世界 `data/shopmod_outlets.dat`，区块加载后自动恢复标志，重设/删除会清理本模组对应标志；不强制加载远方区块。底座被移除时柜台停用，恢复底座后重新显示。网点最多 128 个，名字限 1–32 位字母、数字、短横线和下划线。

右键柜台或底座打开目录，可选择商品、数量、购买/出售模式和银行实际提供的结算货币。再点击确认才结算；确认时重新验证整单报价，变动会要求重新确认。交易仍由已有 TradeDesk 和银行接口处理，柜台不直接改余额。菜单展示物不能取走，快捷移动、拖动、交换和双击不触发交易。一次确认后清空选中商品，避免重复点击意外连续成交。

新柜台是所有网点共享的服务器商店，不是玩家私人箱子商店或独立库存；不改变既有定价和远程交易规则。仍需验证实际游戏的柜台显示、跨区块重载及原版客户端联机交互。
