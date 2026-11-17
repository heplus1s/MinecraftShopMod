package dev.jdguo.shop.outlet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CounterRotationTest {
 @Test void frontAndTranslationRotateTogether() {
  assertEquals(1.0, CounterRotation.x(1,0,-1)); assertEquals(0.0,CounterRotation.z(1,0,-1));
  assertEquals(1.0, CounterRotation.z(2,0,-1)); assertEquals(-1.0,CounterRotation.x(3,0,-1));
  for(int i=0;i<4;i++) assertEquals(5.0, Math.pow(CounterRotation.x(i,1,2),2)+Math.pow(CounterRotation.z(i,1,2),2));
 }
 @Test void oldConstructorAndLocationRemainCompatible() {
  var p=new ShopOutlet("北京 总店","minecraft:overworld",1,2,3);
  assertEquals(0,p.facing()); var q=p.rotated(5); assertEquals(1,q.facing());
  assertTrue(q.at("minecraft:overworld",1,2,3)); assertEquals(p.name(),q.name());
 }
}
