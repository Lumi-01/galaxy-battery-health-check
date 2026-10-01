import kr.local.galaxybattery.DumpParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class DumpParserTest {
    private static int checks;
    private static void same(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static DumpParser.Result text(String text) throws Exception {
        return DumpParser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
    public static void main(String[] args) throws Exception {
        DumpParser.Result sample = text("mSavedBatteryAsoc: [96]\r\nmSavedBatteryUsage: [11870]\nmSavedBatteryBsoh: 100\n");
        same(96, sample.asoc.health());
        same(100, sample.bsoh.health());
        same(11870L, sample.usage.single());
        same("약 118.70 회", sample.cycleText());
        same(1, sample.files);
        same(false, text("charge_counter: 4000000\nhealth: 2").hasFields());
        same(null, text("mSavedBatteryAsoc: -1").asoc.health());
        same(null, text("mSavedBatteryAsoc: [0]").asoc.health());
        same(null, text("mSavedBatteryAsoc: 101").asoc.health());
        same(null, text("mSavedBatteryAsoc: null").asoc.health());
        same(null, text("mSavedBatteryAsoc: 96.8").asoc.health());
        same(null, text("mSavedBatteryAsoc: 96invalid").asoc.health());
        same("확인 불가", text("mSavedBatteryUsage: 0").cycleText());
        same("확인 불가", text("mSavedBatteryUsage: -1").cycleText());
        same(null, text("mSavedBatteryUsage: 99999999999999999999").usage.single());
        same(97, text("  mSavedBatteryAsoc = [ 97 ]  ").asoc.health());
        same(null, text("mSavedBatteryAsoc: [96, 95]").asoc.health());
        same(null, text("mSavedBatteryAsoc: 96\nmSavedBatteryAsoc: 95").asoc.health());
        same(null, text("mSavedBatteryAsoc: -1\nmSavedBatteryAsoc: 96").asoc.health());
        same(96, text("mSavedBatteryAsoc: 96\nmSavedBatteryAsoc: [96]").asoc.health());
        same(false, text("not_mSavedBatteryAsoc: 96").hasFields());
        same(96, text("mSavedBatteryAsoc: [96] mSavedBatteryUsage: [11870]").asoc.health());
        String privateLine = "email=private@example.com mSavedBatteryAsoc: 96 device_secret=12345";
        same(false, text(privateLine).evidence().contains("private@example.com"));
        same(false, text(privateLine).evidence().contains("device_secret"));
        same(false, text("mSavedBatteryAsoc: private@example.com").evidence().contains("private@example.com"));
        byte[] utf16 = ("\ufeffmSavedBatteryAsoc: [94]").getBytes(StandardCharsets.UTF_16LE);
        same(94, DumpParser.parse(new ByteArrayInputStream(utf16)).asoc.health());
        same(93, text("\ufeffmSavedBatteryAsoc: [93]").asoc.health());
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) { gzip.write("mSavedBatteryUsage: 24560".getBytes(StandardCharsets.UTF_8)); }
        same("약 245.60 회", DumpParser.parse(new ByteArrayInputStream(compressed.toByteArray())).cycleText());
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipped)) {
            zip.putNextEntry(new ZipEntry("dumpstate_board.log"));
            zip.write("mSavedBatteryAsoc: 96".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("old/prev_dumpstate_board.log"));
            zip.write("mSavedBatteryAsoc: 95".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        DumpParser.Result conflict = DumpParser.parse(new ByteArrayInputStream(zipped.toByteArray()));
        same(2, conflict.files);
        same(null, conflict.asoc.health());
        same(true, conflict.asoc.conflicted());
        char[] longLine = new char[70000]; Arrays.fill(longLine, 'x');
        DumpParser.Result big = text(new String(longLine) + "\nmSavedBatteryAsoc: 91");
        same(true, big.skippedLongLines);
        same(91, big.asoc.health());
        boolean interrupted = false;
        Thread.currentThread().interrupt();
        try { text("mSavedBatteryAsoc: 96"); } catch (InterruptedIOException e) { interrupted = true; }
        finally { Thread.interrupted(); }
        same(true, interrupted);
        boolean corrupt = false;
        try { DumpParser.parse(new ByteArrayInputStream(new byte[]{31, (byte)139, 0})); }
        catch (IOException e) { corrupt = true; }
        same(true, corrupt);
        System.out.println("PASS: " + checks + " dump parsing checks");
    }
}
