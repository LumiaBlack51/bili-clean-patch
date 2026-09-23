import app.revanced.bilibili.clean.SkipEngine;
import java.util.*;

public final class SkipEngineTest {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        SkipEngine e = new SkipEngine();
        long a = e.select("BV1:test:1");
        SkipEngine.Segment ad = new SkipEngine.Segment("ad", 1000, 3000);
        check(e.load(a, Arrays.asList(ad), 10000));
        check(e.at(999, true, true) == null);
        check(e.at(1000, false, true) == null);
        check(e.at(1000, true, false) == null);
        check(e.at(1000, true, true) == ad);
        check(e.at(3000, true, true) == null);
        e.acknowledge(ad);
        check(e.at(1500, true, true) == null); // deliberate rewind is allowed
        long b = e.select("BV1:test:2");
        check(b != a && !e.load(a, Arrays.asList(ad), 10000)); // stale network reply
        check(e.markers().isEmpty());
        check(e.load(b, Arrays.asList(ad), 2000) && e.markers().isEmpty());
        e.reset();
        check(!e.load(b, Arrays.asList(ad), 10000));
        check(e.at(1500, true, true) == null);
        boolean rejected = false;
        try { new SkipEngine.Segment("bad", 100, 99); } catch (IllegalArgumentException ex) { rejected = true; }
        check(rejected);
        System.out.println("PASS: boundaries, paused/disabled, rewind, stale replies, duration, reset, invalid ranges");
    }
}
