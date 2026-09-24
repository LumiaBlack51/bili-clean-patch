package app.revanced.bilibili.clean;

import java.util.*;

/** Pure playback policy. Time units are milliseconds; no account or network dependency. */
public final class SkipEngine {
    public enum Mode { ALWAYS, ONCE, MANUAL, SHOW, DISABLED }
    public static final class Segment {
        public final String id;
        public final String category, action;
        public final long start, end;
        public Segment(String id, long start, long end) {
            this(id, start, end, "sponsor", "skip");
        }
        public Segment(String id, long start, long end, String category, String action) {
            if (id == null || id.isEmpty() || start < 0 || end < start ||
                (end == start && !"full".equals(action) && !"poi".equals(action)))
                throw new IllegalArgumentException("Invalid segment");
            this.id = id; this.start = start; this.end = end;
            this.category = category; this.action = action;
        }
    }
    private String video = "";
    private long generation;
    private List<Segment> segments = Collections.emptyList();
    private final Set<String> handled = new HashSet<>();

    public long select(String key) {
        if (!video.equals(key)) {
            video = key; generation++; segments = Collections.emptyList(); handled.clear();
        }
        return generation;
    }
    public void reset() { video = ""; generation++; segments = Collections.emptyList(); handled.clear(); }
    public boolean load(long requestGeneration, List<Segment> values, long duration) {
        if (requestGeneration != generation) return false;
        List<Segment> valid = new ArrayList<>();
        for (Segment s : values) if (s.end <= duration) valid.add(s);
        valid.sort(Comparator.comparingLong(s -> s.start));
        segments = Collections.unmodifiableList(valid);
        return true;
    }
    public List<Segment> markers() { return segments; }
    /** Manual mode applies to every timed interval, including a community mute annotation.
     * Full-video labels and zero-length points cannot be skipped as intervals. */
    public Segment manualAt(long position, java.util.function.Function<Segment, Mode> policy) {
        for (Segment s : segments) {
            if (("skip".equals(s.action) || "mute".equals(s.action)) && s.start <= position && position < s.end &&
                policy.apply(s) == Mode.MANUAL) return s;
        }
        return null;
    }
    public Segment at(long position, boolean playing, boolean enabled) {
        return at(position, playing, enabled, s -> Mode.ONCE);
    }
    public Segment at(long position, boolean playing, boolean enabled,
                      java.util.function.Function<Segment, Mode> policy) {
        if (!playing || !enabled) return null;
        for (Segment s : segments) {
            Mode mode = policy.apply(s);
            if ("skip".equals(s.action) && s.start <= position && position < s.end &&
                (mode == Mode.ALWAYS || (mode == Mode.ONCE && !handled.contains(s.id)))) return s;
        }
        return null;
    }
    /** Call after an accepted seek request. ONCE permits rewind; ALWAYS deliberately ignores this set. */
    public void acknowledge(Segment segment) { handled.add(segment.id); }
}
