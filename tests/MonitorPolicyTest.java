import kr.local.galaxybattery.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public class MonitorPolicyTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Monitor check " + checks); }
    private static ChargePower.Sample s(long time, int current, int plugged) {
        return new ChargePower.Sample(time, current, 4000, 50, 300, 2, plugged, 2);
    }
    public static void main(String[] args) throws Exception {
        ZeroPowerTracker tracker = new ZeroPowerTracker();
        tracker.add(s(1000, 0, 1)); tracker.add(s(2000, 1000, 1));
        check(tracker.state().getCount() == 0); // A session starting at 0W is not a dip.
        tracker.add(s(3000, 0, 1)); tracker.add(s(4000, 0, 1));
        check(tracker.state().getCount() == 0); // Recovery is required.
        ZeroPowerTracker restored = new ZeroPowerTracker(); restored.restore(tracker.state());
        restored.add(s(5000, 1000, 1));
        check(restored.state().getCount() == 1);
        ZeroPowerTracker.Event event = restored.state().getEvents().get(0);
        check(event.getStarted() == 3000 && event.getRecovered() == 5000 && event.getSamples() == 2);
        for (int invalid : new int[]{-1000, Integer.MIN_VALUE}) {
            restored.add(s(6000, 0, 1)); restored.add(s(7000, invalid, 1)); restored.add(s(8000, 1000, 1));
            check(restored.state().getCount() == 1);
        }
        tracker = new ZeroPowerTracker();
        tracker.add(s(1000,1000,1)); tracker.add(s(2000,0,1)); tracker.add(s(3000,0,0)); tracker.add(s(4000,1000,1));
        check(tracker.state().getCount() == 0);
        tracker.add(s(5000,0,1)); tracker.add(s(300000,1000,1));
        check(tracker.state().getCount() == 0); // No event across a sampling outage.
        tracker.add(s(301000,0,1)); tracker.add(s(302000,1000,2));
        check(tracker.state().getCount() == 0); // Different power source breaks continuity.
        tracker.add(new ChargePower.Sample(303000,0,4000,50,300,4,2));
        tracker.add(s(304000,1000,2));
        check(tracker.state().getCount() == 1); // A connected charging pause can report status 4.
        check(ChargePower.liveWatts(s(1,-1000000,0),false) == null);
        check(ChargePower.liveWatts(s(1,-1000000,0),true).equals(-4.0));
        check(ChargePower.liveWatts(s(1,1000000,1),false).equals(4.0));
        check(ChargePower.liveWatts(s(1,Integer.MIN_VALUE,1),true) == null);
        check(RefreshPolicy.seconds(3,5) == 5 && RefreshPolicy.seconds(60,5) == 60);
        check(RefreshPolicy.dark("system",true) && !RefreshPolicy.dark("light",true) && RefreshPolicy.dark("dark",false));
        check(RefreshPolicy.graphGapMs(Arrays.asList(0L,60000L,120000L)) == 180000);
        check(ThermalStatus.label(-1).contains("확인 불가") && ThermalStatus.label(0).contains("없음") && ThermalStatus.label(6).contains("6단계"));
        check(ThermalStatus.validHeadroom(Float.NaN) == null && ThermalStatus.validHeadroom(Float.POSITIVE_INFINITY) == null && ThermalStatus.validHeadroom(-1f) == null);
        check(ThermalStatus.validHeadroom(1.25f).equals(1.25f)); // Values above 1 are meaningful, not clamped to 100%.
        check(ThermalStatus.headroomWarning(null).contains("확인 불가") && ThermalStatus.headroomWarning(.9f).contains("가능성") && ThermalStatus.headroomWarning(1.1f).contains("기준 이상"));
        check(!ThermalStatus.label(0).contains("열 제한 없음")); // OS zero is not proof of no real throttling.
        Path dir = Files.createTempDirectory("monitor-policy");
        try {
            PowerLogStore store = new PowerLogStore(dir.toFile());
            String id = store.create(1000);
            store.append(id,s(1000,1000,1)); store.append(id,s(2000,0,1)); store.append(id,s(3000,0,1));
            PowerLogStore.Session session = store.read(id,1);
            check(session.getPeakThermal() == 2 && session.getZeroState().getCount() == 0);
            tracker = new ZeroPowerTracker(); tracker.restore(session.getZeroState()); tracker.add(s(4000,1000,1));
            check(tracker.state().getCount() == 1); // Replay covers samples outside the graph tail.
            store.append(id,s(4000,1000,1));
            check(new PowerLogStore(dir.toFile()).read(id,0).getZeroState().getEvents().get(0).getStarted() == 2000);
            for (int i=0; i<205; i++) {
                store.append(id,s(5000+i*2000,0,1)); store.append(id,s(6000+i*2000,1000,1));
            }
            session = store.read(id,0);
            check(session.getZeroState().getCount() == 206 && session.getZeroState().getEvents().size() == 200);
            // Old v0.4 fixed records stay readable and appendable; thermal is explicitly unknown.
            String old = UUID.randomUUID().toString();
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(dir.resolve(old+".power")))) {
                out.writeInt(0x47504231); out.writeLong(1); out.writeLong(0);
                out.writeLong(1000); for (int v : new int[]{1000,4000,50,300,2,1}) out.writeInt(v);
            }
            store.append(old,s(2000,0,1)); store.append(old,s(3000,1000,1));
            session = store.read(old,600);
            check(session.getCount() == 3 && session.getZeroState().getCount() == 1);
            check(session.getPeakThermal() == -1 && session.getSamples().get(0).getThermalStatus() == -1);
            store.finish(old,4000); check(store.read(old,0).getEnded() == 4000);
        } finally {
            try (java.util.stream.Stream<Path> paths=Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(IOException e){throw new RuntimeException(e);}});
            }
        }
        System.out.println("MonitorPolicyTest: " + checks + " passed");
    }
}
