package dev.jdguo.shop.price;
import java.math.BigDecimal;
import dev.jdguo.shop.catalog.ShopConfig;
/** Run standalone or from MarketSwingRegressionTest. */
public final class MarketSwingRegression {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        checks=0;
        var step=new BigDecimal("0.002"); var floor=new BigDecimal("0.000000001");
        for(String start:new String[]{"0.000000001","0.0001","1","100","999999"})
        for(String s:new String[]{"0.0001","0.04","0.10","0.5","1"})
        for(String rate:new String[]{"0.00001","1","2.7182818","1000"})
        for(int n:new int[]{1,2,64,2304}) {
            var p=new BigDecimal(start);var spread=new BigDecimal(s);var units=new BigDecimal(rate);
            java.util.function.UnaryOperator<BigDecimal> low=b->MarketInfluence.blend(b,BigDecimal.ZERO,spread);
            java.util.function.UnaryOperator<BigDecimal> high=b->MarketInfluence.blend(b,b.multiply(BigDecimal.TEN),spread);
            var buy=PriceMath.execution(p,step,floor,n,true,spread,low,units);
            var sell=PriceMath.execution(buy.path().end(),step,floor,n,false,spread,high,units);
            check(sell.charge().compareTo(buy.charge())<=0,"market swing buy/sell profit: "+start+" "+s+" "+rate+" "+n);
            var sold=PriceMath.execution(p,step,floor,n,false,spread,high,units);
            var bought=PriceMath.execution(sold.path().end(),step,floor,n,true,spread,low,units);
            check(sold.charge().compareTo(bought.charge())<=0,"market swing sell/buy profit");
            var state=buy.path().end();var total=BigDecimal.ZERO;
            for(int i=0;i<n;i++){var one=PriceMath.execution(state,step,floor,1,false,spread,high,units);state=one.path().end();total=total.add(one.charge());}
            check(total.compareTo(sell.charge())==0,"split sale changes rounding");
        }
        for(String s:new String[]{"0","-0.1","1.01","NaN","Infinity"}) {
            boolean rejected=false;
            try{ShopConfig.defaults().withSpread(new BigDecimal(s));}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"invalid configured spread accepted "+s);
        }
        for(BigDecimal s:new BigDecimal[]{null,BigDecimal.ZERO,BigDecimal.ONE.negate(),new BigDecimal("1.01")}) {
            boolean rejected=false;
            try{MarketInfluence.blend(BigDecimal.ONE,BigDecimal.TEN,s);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"invalid spread accepted by blend");
        }
        System.out.println("PASS: "+checks+" market swing/rounding/split/config checks");
    }
}
