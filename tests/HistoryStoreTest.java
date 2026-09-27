import kr.local.galaxybattery.HistoryStore;
import java.nio.file.*;
import java.util.*;

public class HistoryStoreTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("History check " + checks);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("battery-history-test");
        try {
            HistoryStore store = new HistoryStore(root.toFile());
            check(store.list().isEmpty());
            HistoryStore.Entry first = store.save(100L, "로그 분석", "ASOC 95", "한글 원본 결과\nBSOH 98");
            HistoryStore.Entry second = store.save(200L, "직접 조회", "ASOC 94", "새 조회");
            HistoryStore reopened = new HistoryStore(root.toFile());
            check(reopened.list().size() == 2);
            check(reopened.list().get(0).getId().equals(second.getId()));
            check(reopened.list().get(1).getReport().equals("한글 원본 결과\nBSOH 98"));
            Files.write(root.resolve("interrupted.tmp"), new byte[] {1, 2});
            check(reopened.list().size() == 2);
            try {
                reopened.save(300L, "test", "", String.join("", Collections.nCopies(70000, "a")));
                throw new AssertionError("Oversized write accepted");
            } catch (java.io.IOException expected) { check(reopened.list().size() == 2); }
            reopened.delete(Collections.singletonList(first.getId()));
            check(reopened.list().size() == 1 && reopened.list().get(0).getId().equals(second.getId()));
            reopened.delete(Collections.singletonList(first.getId()));
            check(reopened.list().size() == 1);
            try { reopened.delete(Collections.singletonList("../outside")); throw new AssertionError("Unsafe id"); }
            catch (IllegalArgumentException expected) { check(reopened.list().size() == 1); }
            HistoryStore.Entry third = reopened.save(400L, "test", "", "third");
            reopened.delete(Arrays.asList(second.getId(), third.getId()));
            check(new HistoryStore(root.toFile()).list().isEmpty());
            Files.write(root.resolve(UUID.randomUUID() + ".record"), new byte[] {1});
            try { reopened.list(); throw new AssertionError("Corruption ignored"); }
            catch (java.io.IOException expected) { check(true); }
            System.out.println("HistoryStoreTest: " + checks + " passed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
        }
    }
}
