import kr.local.galaxybattery.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public class MonitorPolicyTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Monitor check " + checks); }
    private static ChargePower.Sample s(long time, int current, int plugged) {
        return new ChargePower.Sample(time, current, 4000, 50, 300, current < 0 ? 4 : 2, plugged, 2);
    }
    public static void main(String[] args) throws Exception {
        ChargeInterruptionTracker tracker = new ChargeInterruptionTracker();
        tracker.add(s(1000,-1000000,1)); tracker.add(s(2000,1000000,1));
        check(tracker.state().getCount()==0); // Starting in discharge does not establish a charging interruption.
        tracker.add(s(3000,0,1));tracker.add(s(4000,1000000,1));
        check(tracker.state().getCount()==0); // A dip to zero without discharge is explicitly excluded.
        tracker.add(s(5000,-500000,1));tracker.add(s(6000,-1500000,1));
        check(tracker.state().getCount()==0); // Count only on recovery, not each negative sample.
        ChargeInterruptionTracker restored=new ChargeInterruptionTracker();restored.restore(tracker.state());
        restored.add(s(7000,0,1));restored.add(s(8000,1000000,1));
        check(restored.state().getCount()==1);
        ChargeInterruptionTracker.Event event=restored.state().getEvents().get(0);
        check(event.getStarted()==5000 && event.getRecovered()==8000 && event.getLowestWatts()==-6.0);
        restored.add(s(9000,-1000000,1));restored.add(s(10000,1000000,1));check(restored.state().getCount()==2);
        for(int invalid : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            restored.add(s(11000,-1000,1));restored.add(s(12000,invalid,1));restored.add(s(13000,1000,1));
            check(restored.state().getCount()==2);
        }
        tracker=new ChargeInterruptionTracker();
        tracker.add(s(1000,1000,1));tracker.add(s(2000,-1000,0));tracker.add(s(3000,1000,1));
        check(tracker.state().getCount()==0); // Unplugging/replugging is not an interruption.
        tracker.add(s(4000,-1000,1));tracker.add(s(5000,1000,2));check(tracker.state().getCount()==0);
        tracker.add(s(6000,-1000,2));tracker.add(s(200000,1000,2));check(tracker.state().getCount()==0);
        tracker.add(s(201000,-1000,2));tracker.add(s(201000,-2000,2));tracker.add(s(202000,1000,2));
        check(tracker.state().getCount()==0); // Duplicate/backwards timestamps break continuity.
        tracker.add(s(203000,0,2));tracker.add(s(204000,-1000,2));tracker.add(s(205000,1000,2));
        check(tracker.state().getCount()==1); // No exact 0W reading is required; an intermediate zero is allowed.
        tracker.add(s(206000,-1000,2));tracker.add(new ChargePower.Sample(207000,1000,4000,50,300,4,2));tracker.add(s(208000,1000,2));
        check(tracker.state().getCount()==1); // Positive NOT_CHARGING must not claim recovery.

        for(boolean charging : new boolean[]{false,true})for(boolean discharge : new boolean[]{false,true}) {
            check(Objects.equals(ChargePower.liveWatts(s(1,1000000,1),charging,discharge),charging?4.0:null));
            check(Objects.equals(ChargePower.liveWatts(s(1,-1000000,1),charging,discharge),discharge?-4.0:null));
            check(Objects.equals(ChargePower.liveWatts(s(1,0,1),charging,discharge),charging?0.0:null));
            check(ChargePower.liveWatts(null,charging,discharge)==null);
        }
        check(ChargePower.liveWatts(s(1,1000000,0),true,true)==null);
        check(ChargePower.liveWatts(s(1,Integer.MIN_VALUE,1),true,true)==null);
        check(RefreshPolicy.seconds(3,5)==5 && RefreshPolicy.seconds(60,5)==60);
        check(RefreshPolicy.dark("system",true) && !RefreshPolicy.dark("light",true) && RefreshPolicy.dark("dark",false));
        check(RefreshPolicy.graphGapMs(Arrays.asList(0L,60000L,120000L))==180000);
        check(ThermalStatus.label(-1).contains("확인 불가") && ThermalStatus.label(0).equals("쓰로틀링 0단계") && ThermalStatus.label(6).equals("쓰로틀링 6단계"));
        check(ThermalStatus.validHeadroom(Float.NaN)==null && ThermalStatus.validHeadroom(Float.POSITIVE_INFINITY)==null && ThermalStatus.validHeadroom(-1f)==null);
        check(ThermalStatus.validHeadroom(1.25f).equals(1.25f));
        check(ThermalStatus.headroomWarning(null).contains("읽을 수 없어요") && ThermalStatus.headroomWarning(.9f).contains("가능성") && ThermalStatus.headroomWarning(1.1f).contains("기준 이상"));
        Path dir=Files.createTempDirectory("monitor-policy");
        try {
            PowerLogStore store=new PowerLogStore(dir.toFile());String id=store.create(1000);
            store.append(id,s(1000,1000,1));store.append(id,s(2000,-1000,1));store.append(id,s(3000,-2000,1));
            PowerLogStore.Session session=store.read(id,1);
            check(session.getPeakThermal()==2 && session.getInterruptionState().getCount()==0);
            tracker=new ChargeInterruptionTracker();tracker.restore(session.getInterruptionState());tracker.add(s(4000,1000,1));
            check(tracker.state().getCount()==1); // Replay/restore covers samples beyond the graph tail.
            store.append(id,s(4000,1000,1));
            check(new PowerLogStore(dir.toFile()).read(id,0).getInterruptionState().getEvents().get(0).getStarted()==2000);
            for(int i=0;i<205;i++) { store.append(id,s(5000+i*2000,-1000,1));store.append(id,s(6000+i*2000,1000,1)); }
            session=store.read(id,0);check(session.getInterruptionState().getCount()==206 && session.getInterruptionState().getEvents().size()==200);
            // Existing 32-byte logs stay readable and are recomputed using the new transition rule.
            String old=UUID.randomUUID().toString();
            try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(dir.resolve(old+".power")))) {
                out.writeInt(0x47504231);out.writeLong(1);out.writeLong(0);out.writeLong(1000);
                for(int v:new int[]{1000,4000,50,300,2,1})out.writeInt(v);
            }
            store.append(old,s(2000,-1000,1));store.append(old,s(3000,1000,1));session=store.read(old,600);
            check(session.getCount()==3 && session.getInterruptionState().getCount()==1);
            check(session.getPeakThermal()==-1 && session.getSamples().get(0).getThermalStatus()==-1);
            store.finish(old,4000);check(store.read(old,0).getEnded()==4000);
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(IOException e){throw new RuntimeException(e);}});
            }
        }
        System.out.println("MonitorPolicyTest: "+checks+" passed");
    }
}
