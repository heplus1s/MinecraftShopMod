/**
 * Attachment point for a later chest screen.
 *
 * <p>This package has no screen handler and no chest menu. A future server screen should
 * call {@link dev.jdguo.shop.ui.ShopChestUiHooks} for a quote, a purchase, or a sale, then
 * draw the returned numbers. Pricing, the price step, and bank settlement stay behind that
 * class. Do not copy them into the screen.
 *
 * <p>The optional client in MinecraftUIMod should not call these methods. It sends a custom
 * payload through {@link dev.jdguo.shop.net.ShopUiNetwork}. The server runs the same hooks
 * and answers with a snapshot. A vanilla client never registers that channel, so it can
 * join a dedicated server with no resource pack and no client mod.
 */
package dev.jdguo.shop.ui;
