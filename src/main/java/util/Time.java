package util;

import lombok.Getter;

public class Time {
    private int minutes;
    private int seconds;
    @Getter
    private int frames;

    // Constructor that accepts frames.
    public Time(int frames) {
        this.frames = frames;
        this.minutes = frames / 1440;
        this.seconds = frames / 24 % 60;
    }

    // Constructor that accepts minutes and seconds.
    public Time(int minutes, int seconds) {
        this.minutes = minutes;
        this.seconds = seconds;
        this.frames = 24 * ((minutes * 60) + seconds);
    }

    // Returns a string in "minutes:seconds" format, padding seconds if needed.
    @Override
    public String toString() {
        return minutes + ":" + (seconds < 10 ? "0" + seconds : seconds);
    }

    public boolean lessThanOrEqual(Time t2) {
        return (this.minutes < t2.minutes) ||
                (this.minutes == t2.minutes && this.seconds <= t2.seconds);
    }

    public boolean greaterThan(Time t2) {
        return (this.minutes > t2.minutes) ||
                (this.minutes == t2.minutes && this.seconds > t2.seconds);
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