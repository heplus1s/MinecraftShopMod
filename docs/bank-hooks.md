# 商店需要中央银行补上的账户

商店已经接上中央银行现有的两处公开接口：

- `dev.jdguo.bank.market.ShopQuote.install`：按物品标识报告当前的钻石等值。标识形如 `minecraft:diamond`。破产清偿如果以后要按商店价格估价，可以调用 `ShopQuote.installed()`。当前的 `IssuerLevy` 还没有调用它，商店不会为此改银行。
- `BankBooks.unitsPer(noteId, "diamond")` 和 `copperPerDiamond()`：把钻石等值换成银行券。玩家活期存款用 `balance`，储蓄总池用 `savingsPool`，活期年利率用 `interestFraction`。这些都只读。

商店没有、也不应该自己造一种货币。买卖要等下面这个账户出现。

## 还缺的账户

中央银行请增加类 `dev.jdguo.bank.market.ShopOperatingAccount`。商店用反射调用它，所以银行不必依赖商店。

```java
package dev.jdguo.bank.market;

import dev.jdguo.bank.ledger.TxResult;
import java.math.BigInteger;
import java.util.UUID;

public final class ShopOperatingAccount {
    /** 世界账本还没装好时返回 null。 */
    public static ShopOperatingAccount get() { ... }

    /** 中央虚拟货币，通常是 note。 */
    public String currencyId() { ... }

    /** 商店经营账户余额。 */
    public BigInteger balance() { ... }

    /** 玩家在同一种货币上的活期存款。 */
    public BigInteger playerBalance(UUID player) { ... }

    /**
     * playerPaysShop 为 true：玩家购买，借记玩家活期存款，贷记商店经营账户。
     * 为 false：玩家出售，方向相反。
     * 成功时 TxResult.success() 为 true。失败时不要改任何余额。
     * 失败代码可用 insufficient_funds、insufficient_shop、bad_amount。
     */
    public TxResult transfer(UUID player, BigInteger amount, boolean playerPaysShop) { ... }
}
```

商店这边的同义说明是 `dev.jdguo.shop.bank.ShopOperatingLedger`。银行实现自己的类即可，不必实现商店里的接口。

这个账户要满足三条：

1. 它是储蓄总池里的分户，和玩家活期存款在同一笔总账下。玩家之间的分户之和，加上商店经营账户，再加上手续费留存，构成储蓄总池。`savingsPool` 必须把经营账户算进去。
2. `transfer` 只在总池内部过户。发行准备金、个人发行金库、纸币、土地出让账户都不变。过户本身不改变银行券发行额。土地出让账户属于服务器卖地，不属于商店。
3. 经营账户按活期存款计息，和玩家存款用同一个年利率、同一个 365 个游戏日，从手续费留存里支付。`accrueInterest` 现在只遍历玩家账户，需要把这个分户加进去。商店自己不计息，避免重复造币。

在这个类出现之前，`/shop buy` 和 `/shop sell` 会拒绝，并在聊天里说明原因。`/shop price` 仍可显示钻石等值；汇率读得到时，也会显示换成银行券后的金额。

# What the central bank still needs to add

The shop already uses two public bank hooks:

- `ShopQuote.install` reports the live diamond-equivalent for an item id such as `minecraft:diamond`. `IssuerLevy` does not read that quote yet. The shop does not change the bank to make it do so.
- `BankBooks.unitsPer`, `copperPerDiamond`, `balance`, `savingsPool`, and `interestFraction` are read-only.

Purchases and sales wait for one missing account. Please add `dev.jdguo.bank.market.ShopOperatingAccount` with `get`, `currencyId`, `balance`, `playerBalance`, and `transfer(UUID, BigInteger, boolean)`. The shop looks that class up by reflection. The bank does not need a dependency on the shop. The same contract is described by `dev.jdguo.shop.bank.ShopOperatingLedger`.

The account is a sub-ledger of the savings pool, beside personal demand deposits. A transfer stays inside that pool. It must not touch issuance reserves, personal issuance vaults, paper money, or the land-grant account. The pool total includes the operating balance. The operating balance earns demand-deposit interest from the retained-fee slice on the same daily pass as personal savings. The shop does not pay that interest itself.

Until the class exists, buy and sell are refused in chat. Price queries still work.
