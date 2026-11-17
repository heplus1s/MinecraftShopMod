package dev.jdguo.shop.outlet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShopOutletTest {
    @Test void coordinatesAreDimensionScoped() {
        ShopOutlet o=new ShopOutlet("spawn","minecraft:overworld",1,64,2);
        assertTrue(o.at("minecraft:overworld",1,64,2));
        assertFalse(o.at("minecraft:the_nether",1,64,2));
        assertFalse(o.at("minecraft:overworld",1,65,2));
    }
    @Test void namesCannotInjectEntityTagsOrPaths() {
        for(String bad:new String[]{"", " leading", "../data", "a:b", "a".repeat(33)})
            assertThrows(IllegalArgumentException.class,()->new ShopOutlet(bad,"minecraft:overworld",0,64,0));
    }
    @Test void invalidDimensionRejected() {
        assertThrows(IllegalArgumentException.class,()->new ShopOutlet("shop","invalid dimension",0,64,0));
    }
}
