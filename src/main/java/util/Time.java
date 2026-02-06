package util;

import lombok.Getter;

/**
 * Represents game time in StarCraft.
 * StarCraft runs at 24 frames per second (fps).
 * Conversion: 1 second = 24 frames, 1 minute = 1440 frames.
 */
public class Time {

    static final private int FRAMES_PER_MINUTE = 1440;
    static final private int FRAMES_PER_SECOND = 24;
    static final private int SECONDS_PER_MINUTE = 60;
    static final private int SECONDS_PADDING_THRESHOLD = 10;

    private int minutes;
    private int seconds;
    @Getter
    private int frames;

    // Constructor that accepts frames.
    public Time(int frames) {
        this.frames = frames;
        this.minutes = frames / FRAMES_PER_MINUTE;
        this.seconds = frames / FRAMES_PER_SECOND % SECONDS_PER_MINUTE;
    }

    // Constructor that accepts minutes and seconds.
    public Time(int minutes, int seconds) {
        this.minutes = minutes;
        this.seconds = seconds;
        this.frames = FRAMES_PER_SECOND * (SECONDS_PER_MINUTE * minutes + seconds);
    }

    // Returns a string in "minutes:seconds" format, padding seconds if needed.
    @Override
    public String toString() {
        return minutes + ":" + (seconds < SECONDS_PADDING_THRESHOLD ? "0" + seconds : seconds);
    }

    public boolean lessThanOrEqual(Time t2) {
        return this.frames <= t2.frames;
    }

    public boolean greaterThan(Time t2) {
        return this.frames > t2.frames;
    }

    public Time add(Time other) {
        return new Time(this.frames + other.frames);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Time)) return false;
        Time t2 = (Time) o;
        return this.frames == t2.frames;
    }

    @Override
    public int hashCode() {
        return frames;
    }
}