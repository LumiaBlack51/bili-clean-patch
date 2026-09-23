package app.revanced.bilibili.clean;

import java.util.*;

/** Pure playback policy. Time units are milliseconds; no account or network dependency. */
public final class SkipEngine {
    public static final class Segment {
        public final String id;
        public final long start, end;
        public Segment(String id, long start, long end) {
            if (id == null || id.isEmpty() || start < 0 || end <= start)
                throw new IllegalArgumentException("Invalid segment");
            this.id = id; this.start = start; this.end = end;
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
    public Segment at(long position, boolean playing, boolean enabled) {
        if (!playing || !enabled) return null;
        for (Segment s : segments)
            if (s.start <= position && position < s.end && !handled.contains(s.id)) return s;
        return null;
    }
    /** Call only after successful seek. Rewinding is then allowed without a skip loop. */
    public void acknowledge(Segment segment) { handled.add(segment.id); }
}
