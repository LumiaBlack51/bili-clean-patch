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
        long c = e.select("categories");
        SkipEngine.Segment intro = new SkipEngine.Segment("intro", 4000, 6000, "intro", "skip");
        SkipEngine.Segment full = new SkipEngine.Segment("full", 0, 0, "sponsor", "full");
        SkipEngine.Segment poi = new SkipEngine.Segment("poi", 8000, 8000, "poi_highlight", "poi");
        SkipEngine.Segment mute = new SkipEngine.Segment("mute", 6000, 7000, "sponsor", "mute");
        check(e.load(c, Arrays.asList(ad, intro, full, poi, mute), 10000));
        check(e.markers().size() == 5);
        for (SkipEngine.Mode mode : Arrays.asList(SkipEngine.Mode.MANUAL, SkipEngine.Mode.SHOW, SkipEngine.Mode.DISABLED))
            check(e.at(1500, true, true, s -> mode) == null);
        e.acknowledge(ad);
        check(e.at(1500, true, true, s -> SkipEngine.Mode.ONCE) == null);
        check(e.at(1500, true, true, s -> SkipEngine.Mode.ALWAYS) == ad);
        check(e.at(1500, false, true, s -> SkipEngine.Mode.ALWAYS) == null);
        check(e.at(4500, true, true, s -> s.category.equals("intro") ? SkipEngine.Mode.ALWAYS : SkipEngine.Mode.DISABLED) == intro);
        check(e.at(0, true, true, s -> SkipEngine.Mode.ALWAYS) == null);
        check(e.at(6500, true, true, s -> SkipEngine.Mode.ALWAYS) == null);
        check(e.at(8000, true, true, s -> SkipEngine.Mode.ALWAYS) == null);
        System.out.println("PASS: per-category modes, always/once rewind, manual/show/disabled, full/poi/mute safety");
        System.out.println("PASS: boundaries, paused/disabled, rewind, stale replies, duration, reset, invalid ranges");
    }
}
