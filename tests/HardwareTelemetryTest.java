import kr.local.galaxybattery.HardwareTelemetry;
import kr.local.galaxybattery.ChargePower;
import kr.local.galaxybattery.PowerLogStore;
import java.nio.file.Files;
import java.io.File;

public class HardwareTelemetryTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        HardwareTelemetry telemetry = new HardwareTelemetry();
        String first = "stat|cpu0 100 0 20 80 0 0 0 0 30 0\ncore|0|1\ncore|1|0\ntemp|soc|42\ntemp|gpu|44\ngpu|25\n";
        String result = telemetry.describe(first, "Shizuku");
        check(result.contains("다음 측정 대기"), "first sample must not invent CPU usage");
        check(result.contains("CPU 1   오프라인"), "offline cores");
        check(result.contains("CPU 0") && result.contains("온도 확인 불가"), "SoC temperature must not be mapped to core");
        result = telemetry.describe("stat|cpu0 130 0 30 140 0 0 0 0 60 0\ntemp|cpu0|41\ngpu|NaN\n", "Shizuku");
        check(result.contains("40.0%"), "CPU delta without double-counting guest time");
        check(result.contains("41.0°C"), "explicit core sensor");
        check(result.contains("GPU 전체 사용률   확인 불가"), "invalid GPU percentage");
        result = telemetry.describe("stat|cpu0 1 0 0 1 0 0 0 0\n", "Shizuku");
        check(result.contains("확인 불가"), "counter reset cannot be a percentage");
        result = telemetry.describe(first, "앱 직접 읽기");
        check(result.contains("다음 측정 대기"), "source switch resets baseline");
        telemetry.reset();
        check(telemetry.describe(first, "앱 직접 읽기").contains("다음 측정 대기"), "foreground restart resets baseline");
        check(telemetry.describe("stat|cpu0 9223372036854775807 0 0 0\ngpu|101\ntemp|gpu|999\n", "test").contains("확인 불가"), "reject malformed and out of range values");
        File directory = Files.createTempDirectory("discharge-test").toFile();
        PowerLogStore store = new PowerLogStore(directory);
        String id = store.create(1000);
        store.append(id, new ChargePower.Sample(2000, 2000000, 4000, 80, 320, 2, 1));
        for (int i = 0; i < 605; i++) {
            store.append(id, new ChargePower.Sample(3000 + i * 1000L, i == 0 ? -3000000 : -1000000, 4000, 79, 330, 3, 0));
        }
        store.append(id, new ChargePower.Sample(700000, Integer.MIN_VALUE, 4000, 78, 340, 3, 0));
        store.finish(id, 710000);
        PowerLogStore.Session session = new PowerLogStore(directory).read(id, 600);
        check(session.getCount() == 607, "charge, discharge and missing samples retained");
        check(session.getDischargeCount() == 605, "all discharge samples, not just displayed tail");
        check(session.getDischargeMaximum() == 12.0, "discharge peak survives outside graph tail");
        check(session.getDischargeMinimum() == 4.0, "discharge magnitude minimum");
        check(session.getMaximum() == 8.0 && session.getMinimum() == 8.0, "charging extrema remain independent");
        check(session.getSamples().size() == 600, "graph display limit preserved");
        check(store.list().get(0).getDischargeCount() == 605, "history summary counts discharge without loading graph");
        check(session.getSamples().get(0).getTemperature() == 330, "battery temperature persists");
        store.delete(java.util.Collections.singletonList(id), null);
        directory.delete();
        System.out.println("Hardware telemetry and discharge: " + checks + " checks passed");
    }
}
