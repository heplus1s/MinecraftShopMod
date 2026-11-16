# MinecraftShopMod

Fabric 1.20.1 商店模组：玩家和服务器买卖物品，货款记在央行储蓄里的商店经营账户。价格先按获取难度对照钻石定一个起点，再随买卖小幅波动，长期可以离起点较远。

A Fabric 1.20.1 shop where players buy from and sell to the server. Payments go to a shop operating account under central-bank savings. Prices start from gather difficulty relative to diamonds, then drift with supply and demand.

支持指令、实体柜台和原版箱子交易界面。增强客户端界面已内置于本主模组，不安装客户端模组也可以进服。

Commands, physical counters and vanilla chest screens are available. The optional enhanced UI is embedded in this jar. Vanilla clients can still join.

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

内部价格曲线以 34 位有效数字保存，显示和支付余额为四位小数。每件物品先换算货币，玩家买入向上舍入、卖出向下舍入，再相加；拆成多笔不会减少相同成交序列的舍入费用。整数零显示成 `0`。不到一个银行券也可以买，只要选定货币的余额够付这四位小数。余额不够就整笔拒绝，不会只成交一部分。没有起点、也没有已经成交的价格时，显示「暂无牌价」，不会临时编一个价。

每成交一个物品，价格按当前价格小幅移动。玩家购买，价格上升。玩家出售，价格下降。没有把价格拉回起点的力量。买卖多了，价格可以离起点很远，以便靠近游戏里真实的供求。

一步的默认大小是 0.002，也就是当前价格的千分之二。一次购买多个时，价格沿着这一小步往上走，购买先按当前档报价再乘以 1.002；出售先除以 1.002 返回上一档再报价。反向交易沿同一曲线，不再按上涨后的价格立即回购。

Prices start from gathering difficulty relative to one diamond. That anchor is only the opening price. At the moment of a trade it is converted, at the bank's current rate, into the one currency the player selected: the central bank note, or one personal note. Copper and diamond are not payment currencies, and one order does not use a second currency.

Curve state keeps 34 significant digits across saves. Payable amounts use four decimals per item: ceiling for player buys and floor for player sells, then sum. This makes batch and split orders follow the same rounding. Integer zero is shown as `0`. An order below one unit is allowed when the selected balance covers that four-decimal total. A short balance refuses the whole order. A missing anchor or live quote is shown as 暂无牌价 and is not replaced with a made-up price.

Each item in a trade takes one small step off the current price. Buying steps up. Selling steps down. Nothing pulls the price back to the anchor, so a long run of trades can travel far from the start. The default step is 0.002 of the current price.

### 固定双向价差

`config/shopmod.json` 的 `spreadFraction` 默认 `0.04`，可调整为 `0.0001` 至 `1`。定义为同一执行档位的 `(买价−卖价)/中心价`：4% 即买方支付中心价 ×1.02、卖方收到 ×0.98。该价差无论市场模组是否提供参考价都生效；不是只在有市场成交记录时才生效。不同方向的价格冲击、四位小数舍入可能扩大最终看到的差距，不会将其缩窄为负。

旧配置没有该字段时采用 4%；保存配置或执行 `/shop admin spread 0.04` 后写入文件。旧存档价格保留为新的曲线起点，后续保存保留内部精度，不重置已交易价格。

价差不是消除所有套利的保证：跨商品配方、外部市场操纵、不同货币实时汇率变化仍需独立风控。本修复保证同一报价来源和不变汇率下的即时往返不获利，且批量与拆单一致。

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
- `/shop admin spread <fraction>` 持久化买卖价差，例如 `0.04` 表示相对中心价 4%（买 +2%、卖 −2%）。
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

柜台和 `/shop ui` 已提供原版箱子界面，调用 `dev.jdguo.shop.ui.ShopChestUiHooks`，用它报价、购买和出售。计价和入账仍留在商店里，不必重写。

可选的客户端界面已内置于主模组。它如果要同步，使用服务器上的 `shopmod:hello`、`shopmod:action` 和 `shopmod:sync`。没有安装这个界面的原版客户端可以照常进服，服务器不会向它发送这个频道。

The vanilla chest screen calls `dev.jdguo.shop.ui.ShopChestUiHooks`. Optional client UI is embedded in this jar and may use the `shopmod` hello, action, and sync payloads. Vanilla clients can join without that pack.

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


## 内置客户端界面

本主模组 JAR 同时包含服务端逻辑与可选客户端界面。服务器安装后，原版客户端无需安装本模组即可使用原有命令/原版菜单。客户端安装同一 JAR 可启用增强界面；界面只在客户端入口加载，服务端保持交易权威。

升级时请移除旧 `shopmod-ui` 独立 JAR；同时安装会由 Fabric Loader 明确拒绝，以避免重复接收器、快捷键和类冲突。不要保留同一主模组的两个版本。只安装本地客户端不会改变远端服务器数据；不支持相关网络通道的服务器不会接收本 UI 操作。

兼容性验收仍需分别执行：专用服务器+原版客户端、Fabric空客户端、完整客户端、单人世界切换远程服务器、旧 UI 包并存的拒绝提示、协议不匹配时的基础入口。构建通过不等同于实际联机验收。

### 可靠结算与中断恢复

商店要求新版银行引用接口 `transferReferenced / rollback / receiptStatus / durablePayments`。旧接口不再降级执行付款。每笔订单使用唯一引用：买货的个人币按银行实际抵押链即时清算；卖货按所选币种的真实可支付能力执行，不足时拒绝，玩家可重新选择央行券，商店不自动换币成交。交货明确失败时撤销同一引用，不把退款当成新的一笔卖货或反向发行。

订单意图、银行状态和最终价格写入 `data/shopmod-prices.json` 的 `trades`；版本 2 与 `journalRevision` 防止恢复时较旧 NBT 覆盖已保存订单。JSON 使用临时文件、文件强制落盘、原子替换及完整读回；文件保存失败时停止交易，不吞掉错误。目录 fsync 若平台不支持，或 Windows 拒绝打开目录，则记录警告但不阻止交易；断电目录项持久性依赖操作系统，不承诺所有硬件故障下绝对耐久。库存交付不再把装不下的物品扔在地上，普通异常会恢复整份库存。

服务器在修改库存后把订单标记放进原版玩家 `Tags`，与 `Inventory` 同一玩家 NBT 保存；读回标记并核实内存标记、强制文件落盘成功后才将订单完成。不要手动编辑 `shopmod_tx_` 标记或分别恢复银行/商店/玩家存档。当前实现调用 `saveAllPlayerData()`，每笔需要玩家文件 I/O，在线玩家很多时有性能成本。单机存档若玩家当前状态与玩家文件标记不一致，会停止自动恢复而不是猜测发货。

正常订单完成后清理内存标记，在下一次保存随库存一起清理；重连时也只清理日志中已确认完成的旧标记。中断订单会核对银行回执与玩家标记，已给货不重复给、已扣款不重复扣。无法判明时锁定相关玩家和商品，显示订单号供管理员核对，不自动退款或重发。不确定状态仍可能需要人工处理；这不是把多个独立文件变成同一个数据库事务。
