package link.e4steam.api.testkit;

import link.e4steam.api.ApiValidation;
import link.e4steam.api.event.ApiEvent;
import link.e4steam.api.event.EventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thread-safe bounded typed event recorder for deterministic addon tests. */
public final class EventRecorder<T extends ApiEvent> implements EventListener<T> {
    private final int maximum;
    private final List<T> events = new ArrayList<>();
    private int dropped;

    /** Creates a recorder with a bounded history. */ public EventRecorder(int maximum) { if (maximum < 1 || maximum > 10_000) throw new IllegalArgumentException("invalid maximum"); this.maximum = maximum; }
    @Override public synchronized void onEvent(T event) { if (event == null) throw new NullPointerException("event"); if (events.size() == maximum) { events.remove(0); dropped++; } events.add(event); }
    /** Returns immutable recorded events. */ public synchronized List<T> events() { return Collections.unmodifiableList(new ArrayList<>(events)); }
    /** Returns number evicted by bounds. */ public synchronized int dropped() { return dropped; }
}
