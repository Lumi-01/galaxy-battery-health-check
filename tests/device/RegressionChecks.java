package kr.local.galaxybattery.visualtests;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.local.galaxybattery.*;

/** Device regression checks, never included in the production APK. */
public class RegressionChecks extends Instrumentation {
    Bundle args; int checks;
    void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    Object field(Object target,String name) throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    @Override public void onCreate(Bundle a){args=a;start();}
    int trackPixel(OneUiToggle toggle) {
        int[] location=new int[2];runOnMainSync(()->toggle.getControl().getLocationOnScreen(location));
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        float density=getTargetContext().getResources().getDisplayMetrics().density;
        int color=bitmap.getPixel(location[0]+toggle.getControl().getWidth()/2,location[1]+toggle.getControl().getHeight()/2-(int)(12*density));bitmap.recycle(); Bundle detail=new Bundle();detail.putString("pixel",Integer.toHexString(color)+" @ "+location[0]+","+location[1]+" size "+toggle.getControl().getWidth()+","+toggle.getControl().getHeight());sendStatus(2,detail);return color;
    }
    void idle(){waitForIdleSync();SystemClock.sleep(300);}
    @Override public void onStart(){
        Activity activity=null;
        try{
            String mode=args.getString("mode","switch");
            if(mode.equals("thermal"))new AppSettings(getTargetContext()).setHardwareSeconds(60);
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if(mode.equals("switch")){
                final OneUiToggle[] row=new OneUiToggle[1];final Dialog[] dialog=new Dialog[1];final int[] callbacks={0};final Activity a=activity;
                runOnMainSync(()->{
                    row[0]=new OneUiToggle(a,AppPalette.Companion.forDark(false),"토글 검증","Switch redraw verification",false,value->{callbacks[0]++;return kotlin.Unit.INSTANCE;});
                    dialog[0]=new Dialog(a);dialog[0].setContentView(row[0]);dialog[0].show();dialog[0].getWindow().setLayout(-1,-2);
                });idle();
                check(trackPixel(row[0])==Color.rgb(166,168,173),"Initial off track");
                runOnMainSync(()->row[0].performClick());idle();
                check(row[0].isChecked() && trackPixel(row[0])==Color.rgb(56,122,255),"Row tap must repaint ON track");
                runOnMainSync(()->row[0].getControl().performClick());idle();
                check(!row[0].isChecked() && trackPixel(row[0])==Color.rgb(166,168,173),"Switch tap must repaint OFF track");
                runOnMainSync(()->row[0].setChecked(true));idle();
                check(trackPixel(row[0])==Color.rgb(56,122,255),"Programmatic restored ON must repaint");
                check(callbacks[0]==3,"One callback per transition");runOnMainSync(()->dialog[0].dismiss());
                HardwareMonitorView monitor=(HardwareMonitorView)field(activity,"hardwareMonitor");
                DashboardScaffold dashboard=(DashboardScaffold)field(activity,"dashboard");
                AppSettings settings=new AppSettings(activity);
                runOnMainSync(()->{
                    dashboard.select(1); settings.setCpuCores(false);settings.setTemperatureSensors(false);

                });
                // Locate toggles by class/label rather than depending on a card's child indices.
                LinearLayout cpu=(LinearLayout)monitor.getChildAt(0);OneUiToggle cpuToggle=null;
                for(int i=0;i<cpu.getChildCount();i++)if(cpu.getChildAt(i) instanceof OneUiToggle)cpuToggle=(OneUiToggle)cpu.getChildAt(i);
                final OneUiToggle toggle=cpuToggle;final LinearLayout details=(LinearLayout)field(monitor,"cpuDetails");
                runOnMainSync(()->toggle.setChecked(false));idle();
                for(boolean value:new boolean[]{true,false,true,false}){
                    runOnMainSync(()->toggle.setChecked(value));idle();
                    check(toggle.isChecked()==value && settings.getCpuCores()==value && (details.getVisibility()==View.VISIBLE)==value,"CPU state, preferences and graph expansion must agree");
                }
                IRemoteBattery.Stub fake=new IRemoteBattery.Stub(){public String readBattery(){return "test";}public String readHardware(){return "gpu|1";}public String readThermal(){return "cool|cpu0|2|5|cooling_device0\n";}public void destroy(){}};
                Binder transport=new Binder(){@Override public IInterface queryLocalInterface(String n){return null;}@Override protected boolean onTransact(int c,Parcel d,Parcel r,int f)throws RemoteException{return fake.asBinder().transact(c,d,r,f);}};
                String raw=IRemoteBattery.Stub.asInterface(transport).readThermal();
                check(raw.contains("cpu0|2|5"),"New thermal Binder transaction must round-trip");
                HardwareTelemetry telemetry=new HardwareTelemetry();telemetry.describe(raw,"test");
                final ThermalStatusView[] thermal=new ThermalStatusView[1];
                runOnMainSync(()->{thermal[0]=new ThermalStatusView(a,AppPalette.Companion.forDark(false));thermal[0].setStatus(0,1.1f,telemetry.getLatest().getCooling(),1,"Shizuku",1,1);});
                check(thermal[0].getContentDescription().toString().contains("1개 작동") && thermal[0].getContentDescription().toString().contains("OS 제한 보고 없음"),"OS zero must not hide active kernel cooling signals");
            }else if(mode.equals("thermal")){
                DashboardScaffold dashboard=(DashboardScaffold)field(activity,"dashboard");
                runOnMainSync(()->dashboard.select(1));SystemClock.sleep(700);
                Method getter=activity.getClass().getDeclaredMethod("getThermalMonitor");getter.setAccessible(true);
                ThermalMonitor monitor=(ThermalMonitor)getter.invoke(activity);
                long cooling=monitor.getCoolingUpdatedAt();long status=monitor.getStatusUpdatedAt();
                check(cooling>0 && status>0,"Thermal sources initialized");
                HardwareMonitorView hardware=(HardwareMonitorView)field(activity,"hardwareMonitor");
                long chartTime=((HardwareTelemetry.Frame)field(hardware,"latest")).getTime();
                SystemClock.sleep(10500);
                check(monitor.getCoolingUpdatedAt()-cooling>=9000,"Kernel signals refresh on their own 10s interval");
                check(monitor.getStatusUpdatedAt()-status>=9000,"OS fallback refreshes on the 10s interval");
                check(((HardwareTelemetry.Frame)field(hardware,"latest")).getTime()==chartTime,"Thermal polling does not pollute a 60s CPU/GPU graph interval");
                new AppSettings(getTargetContext()).setHardwareSeconds(2);
            }else if(mode.equals("screen")){
                final Activity a=activity;
                runOnMainSync(()->{new AppSettings(a).setPowerSeconds(60);a.startForegroundService(new Intent(a,ChargeMonitorService.class));});
                long deadline=SystemClock.elapsedRealtime()+5000;
                while((ChargeMonitorService.Companion.getSnapshot().getId()==null || ChargeMonitorService.Companion.getSnapshot().getCount()==0) && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
                check(ChargeMonitorService.Companion.getSnapshot().getId()!=null,"Recording initialized");
                AtomicBoolean done=new AtomicBoolean(false);BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){done.set(true);}};
                getTargetContext().registerReceiver(receiver,new IntentFilter("kr.local.galaxybattery.REGRESSION_DONE"),Context.RECEIVER_EXPORTED);
                Bundle ready=new Bundle();ready.putString("ready","screen");sendStatus(1,ready);
                deadline=SystemClock.elapsedRealtime()+45000;while(!done.get()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
                getTargetContext().unregisterReceiver(receiver);SystemClock.sleep(500);
                String id=ChargeMonitorService.Companion.getSnapshot().getId();
                PowerLogStore store=new PowerLogStore(new File(getTargetContext().getNoBackupFilesDir(),"power-history"));
                PowerLogStore.Session session=store.read(id,600);
                List<ScreenTimeline.Interval> off=ScreenTimeline.intervals(session.getScreenEvents(),session.getStarted(),System.currentTimeMillis());
                check(off.size()==1,"Screen-off interval captured independently of 60s power sampling");
                check(off.get(0).getEnd()-off.get(0).getStart()>=1000,"Screen-off timestamps persisted");
                check(session.getCount()==1,"A short OFF/ON period did not require additional scheduled samples");
                runOnMainSync(()->a.startService(new Intent(a,ChargeMonitorService.class).setAction(ChargeMonitorService.STOP)));SystemClock.sleep(600);
                check(!ChargeMonitorService.Companion.getSnapshot().getActive(),"Recording stopped cleanly");
                runOnMainSync(()->new AppSettings(a).setPowerSeconds(5));
            }else if(mode.equals("history")){
                PowerLogStore store=new PowerLogStore(new File(getTargetContext().getNoBackupFilesDir(),"power-history"));
                long time=System.currentTimeMillis()-300000;String id=store.create(time);
                for(int i=0;i<60;i++){int current=i<40 ? 1500000+(int)(800000*Math.sin(i*.3)) : -500000-(int)(200000*Math.sin(i*.3));store.append(id,new ChargePower.Sample(time+i*5000,current,4000,70,320,i<40?2:3,i<40?1:0,0));}
                store.appendScreenEvent(id,new ScreenTimeline.Event(time,1));store.appendScreenEvent(id,new ScreenTimeline.Event(time+60000,0));store.appendScreenEvent(id,new ScreenTimeline.Event(time+180000,1));store.finish(id,time+300000);
                final Activity a=activity;final String sessionId=id;
                runOnMainSync(()->{try{Method m=a.getClass().getDeclaredMethod("loadPowerSession",String.class);m.setAccessible(true);m.invoke(a,sessionId);}catch(Exception e){throw new RuntimeException(e);}});
                SystemClock.sleep(800);waitForIdleSync();Bundle ready=new Bundle();ready.putString("ready","history");sendStatus(1,ready);
                SystemClock.sleep(10000);store.delete(Collections.singletonList(id),null);
            }
            Bundle result=new Bundle();result.putString("result","PASS: "+mode+", "+checks+" regression checks");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){Bundle result=new Bundle();result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
