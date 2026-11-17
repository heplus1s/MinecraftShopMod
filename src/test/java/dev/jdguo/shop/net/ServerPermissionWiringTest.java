package dev.jdguo.shop.net;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Structural regression for Minecraft-dependent wiring, not an in-game test. */
class ServerPermissionWiringTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/jdguo/shop").resolve(relative));
    }
    @Test void managementEntrypointsRetainServerSideChecks() throws Exception {
        String commands = source("commands/ShopCommands.java");
        assertTrue(commands.contains("literal(\"admin\").requires(ShopCommands::op)"));
        assertTrue(commands.contains("return source.hasPermissionLevel(2)"));
        assertTrue(source("outlet/ShopOutlets.java").contains("requires(source -> source.hasPermissionLevel(2))"));
    }
    @Test void bothQueuedHandshakeAndActionsRejectOldConnection() throws Exception {
        String text=source("net/ShopUiNetwork.java");
        String guard="server.getPlayerManager().getPlayer(player.getUuid()) != player || player.networkHandler != handler";
        assertEquals(2, text.split(java.util.regex.Pattern.quote(guard), -1).length - 1);
    }
}
