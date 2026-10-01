import kr.local.galaxybattery.BatteryValues;

public class BatteryValuesTest {
    private static int checks;
    private static void same(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        same(null, BatteryValues.cycle(null));
        same(null, BatteryValues.cycle(0));
        same(null, BatteryValues.cycle(-1));
        same(450, BatteryValues.cycle(450));
        same(null, BatteryValues.stateOfHealth(Integer.MIN_VALUE));
        same(null, BatteryValues.stateOfHealth(null));
        same(null, BatteryValues.stateOfHealth(0));
        same(null, BatteryValues.stateOfHealth(-1));
        same(null, BatteryValues.stateOfHealth(101));
        same(100, BatteryValues.stateOfHealth(100));
        same(87, BatteryValues.stateOfHealth(87));
        same(0, BatteryValues.percent(0, 100));
        same(50, BatteryValues.percent(100, 200));
        same(null, BatteryValues.percent(null, 100));
        same(null, BatteryValues.percent(100, 0));
        same(null, BatteryValues.percent(-1, 100));
        same(null, BatteryValues.percent(101, 100));
        same(100, BatteryValues.percent(Integer.MAX_VALUE, Integer.MAX_VALUE));
        same("정상", BatteryValues.condition(2));
        same("확인 불가", BatteryValues.condition(null));
        same("충전 완료", BatteryValues.charging(5));
        System.out.println("PASS: " + checks + " battery validation checks");
    }
}
