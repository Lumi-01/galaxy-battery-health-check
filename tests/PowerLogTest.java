import kr.local.galaxybattery.ChargePower;
import kr.local.galaxybattery.PowerLogStore;
import kr.local.galaxybattery.ScreenTimeline;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public class PowerLogTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Power check " + checks); }
    private static ChargePower.Sample sample(long time, int current, int status, int plugged) {
        return new ChargePower.Sample(time, current, 4000, 70, 300, status, plugged);
    }
    public static void main(String[] args) throws Exception {
        check(ChargePower.watts(3000000, 4000).equals(12.0));
        check(ChargePower.watts(-1000000, 4000).equals(-4.0));
        check(ChargePower.watts(0, 4000).equals(0.0));
        check(ChargePower.watts(null, 4000) == null);
        check(ChargePower.watts(Integer.MIN_VALUE, 4000) == null);
        check(ChargePower.watts(3000000, null) == null);
        check(ChargePower.watts(3000000, -1) == null);
        check(ChargePower.watts(Integer.MAX_VALUE, 4000) == null);
        check(ChargePower.chip(12.3).equals("12.3W"));
        check(ChargePower.chip(null).equals("—W"));
        check(sample(1, -1000000, 2, 1).chargingWatts() == null);
        check(sample(1, 1000000, 3, 0).chargingWatts() == null);
        check(sample(1, 1000000, 4, 1).chargingWatts() == null);
        ChargePower.Stats stats = new ChargePower.Stats();
        stats.add(sample(1, Integer.MIN_VALUE, 2, 1));
        stats.add(sample(2, -1000000, 2, 1));
        check(stats.getMinimum() == null && stats.getMaximum() == null);
        stats.add(sample(3, 3000000, 2, 1));
        stats.add(sample(4, 1000000, 2, 1));
        stats.add(sample(5, 0, 5, 1));
        check(stats.getMinimum().equals(0.0) && stats.getMaximum().equals(12.0));
        check(stats.getChargingCount() == 3);
        check(stats.getChargingAverage().equals(16.0/3.0) && stats.getDischargeAverage().equals(4.0));
        ChargePower.Mean mean = new ChargePower.Mean();
        check(mean.getAverage() == null);
        mean.restore(3, 16.0/3.0); mean.add(4.0);
        check(mean.getAverage().equals(5.0));
        mean.add(Double.NaN); mean.add(-2); check(mean.getAverage().equals(5.0));
        List<ScreenTimeline.Event> events = Arrays.asList(new ScreenTimeline.Event(100,1), new ScreenTimeline.Event(105,0),new ScreenTimeline.Event(115,1));
        check(ScreenTimeline.intervals(events,100,120).equals(Arrays.asList(new ScreenTimeline.Interval(105,115))));
        check(ScreenTimeline.intervals(events,110,112).equals(Arrays.asList(new ScreenTimeline.Interval(110,112))));
        check(ScreenTimeline.intervals(Arrays.asList(new ScreenTimeline.Event(105,0)),100,120).equals(Arrays.asList(new ScreenTimeline.Interval(105,120))));
        check(ScreenTimeline.intervals(Arrays.asList(new ScreenTimeline.Event(105,0),new ScreenTimeline.Event(108,-1),new ScreenTimeline.Event(117,0)),100,120).equals(Arrays.asList(new ScreenTimeline.Interval(105,108),new ScreenTimeline.Interval(117,120))));
        check(ScreenTimeline.intervals(Collections.emptyList(),100,120).isEmpty());
        check(ScreenTimeline.intervals(events,120,100).isEmpty());
        Path root = Files.createTempDirectory("power-log-test");
        try {
            PowerLogStore store = new PowerLogStore(root.toFile());
            check(store.list().isEmpty());
            String id = store.create(100);
            store.append(id, sample(100, 5000000, 2, 1)); // 20W, outside recent graph window later
            store.append(id, sample(105, 1000000, 2, 1));
            store.append(id, sample(110, -2000000, 3, 0));
            store.append(id, sample(115, Integer.MIN_VALUE, 2, 1));
            PowerLogStore restarted = new PowerLogStore(root.toFile());
            PowerLogStore.Session session = restarted.read(id, 2);
            check(session.getCount() == 4);
            check(session.getSamples().size() == 2);
            check(session.getSamples().get(0).getTime() == 110);
            check(session.getMinimum().equals(4.0) && session.getMaximum().equals(20.0));
            check(session.getStarted() == 100 && session.getEnded() == 0);
            check(session.getSamples().get(1).watts() == null);
            check(session.getChargingCount()==2 && session.getChargingAverage().equals(12.0) && session.getDischargeAverage().equals(8.0));
            check(session.getScreenEvents().isEmpty()); // Prior sessions must not fabricate a timeline.
            for (ScreenTimeline.Event event : events) store.appendScreenEvent(id,event);
            check(restarted.read(id,0).getScreenEvents().equals(events)); // Sidecar survives reopening and graph-tail limits.
            Files.write(root.resolve(id+".screen"),new byte[]{1,2,3},StandardOpenOption.APPEND);
            check(restarted.read(id,0).getScreenEvents().equals(events));
            store.appendScreenEvent(id,new ScreenTimeline.Event(118,0));
            check(restarted.read(id,0).getScreenEvents().size()==4);
            // An incomplete final sample must not destroy previous samples or alignment.
            Files.write(root.resolve(id + ".power"), new byte[] {1, 2, 3}, StandardOpenOption.APPEND);
            check(restarted.read(id, 600).getCount() == 4);
            restarted.append(id, sample(120, 0, 5, 1));
            check(restarted.read(id, 600).getCount() == 5);
            check(restarted.read(id, 600).getMinimum().equals(0.0));
            check(restarted.list().get(0).getSamples().isEmpty());
            try { restarted.delete(Collections.singletonList(id), id); throw new AssertionError("Active delete accepted"); }
            catch (IOException expected) { check(restarted.read(id, 1).getCount() == 5); }
            restarted.finish(id, 125);
            check(restarted.read(id, 1).getEnded() == 125);
            try { restarted.appendScreenEvent(id,new ScreenTimeline.Event(126,1)); throw new AssertionError("Closed timeline append accepted"); }
            catch (IOException expected) { check(restarted.read(id,0).getScreenEvents().size()==4); }
            try { restarted.append(id, sample(130, 1000000, 2, 1)); throw new AssertionError("Closed append accepted"); }
            catch (IOException expected) { check(restarted.read(id, 1).getCount() == 5); }
            String newer = restarted.create(200);
            check(restarted.list().get(0).getId().equals(newer));
            try { restarted.delete(Collections.singletonList("../outside"), null); throw new AssertionError("Unsafe id"); }
            catch (IllegalArgumentException expected) { check(restarted.list().size() == 2); }
            restarted.delete(Arrays.asList(id, newer), null);
            check(restarted.list().isEmpty());
            check(!Files.exists(root.resolve(id+".screen")));
            System.out.println("PowerLogTest: " + checks + " passed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
        }
    }
}
