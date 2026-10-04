#include "adapt/button.hpp"

namespace adapt {
bool ButtonTiming::valid() const {
    return debounce_ms > 0 && debounce_ms < short_max_ms &&
        double_window_ms >= debounce_ms && double_window_ms <= 60000 && short_max_ms < long_ms &&
        long_ms < very_long_ms && very_long_ms <= 60000;
}
Button::Button(ButtonSink& sink, ButtonTiming timing) : sink_(sink), timing_(timing) {
    if (!timing_.valid()) timing_ = ButtonTiming{};
}
bool Button::configure(ButtonTiming timing) {
    if (!timing.valid() || raw_ || stable_ || pending_) return false;
    timing_ = timing;
    return true;
}
void Button::reset(uint64_t now) {
    raw_ = stable_ = pending_ = second_ = emitted_ = false;
    changed_ = pressed_ = released_ = last_ = now;
}
void Button::expire(uint64_t now) {
    if (pending_ && !second_ && now - released_ > timing_.double_window_ms) {
        pending_ = false;
        sink_.gesture(Gesture::Short, now);
    }
}
bool Button::sample(bool down, uint64_t now) {
    if (now < last_) return false;
    last_ = now;
    if (down != raw_) { raw_ = down; changed_ = now; }
    if (raw_ != stable_ && now - changed_ >= timing_.debounce_ms) {
        stable_ = raw_;
        if (stable_) {
            // Edge time is the first sample of a level that survives debounce.
            expire(changed_);
            second_ = pending_ && changed_ - released_ <= timing_.double_window_ms;
            pressed_ = changed_;
            emitted_ = false;
        } else {
            const auto held = changed_ - pressed_;
            if (!emitted_ && held >= timing_.very_long_ms) {
                if (pending_) { pending_ = false; sink_.gesture(Gesture::Short, now); }
                sink_.gesture(Gesture::VeryLong, now);
            } else if (!emitted_ && held >= timing_.long_ms) {
                if (pending_) { pending_ = false; sink_.gesture(Gesture::Short, now); }
                sink_.gesture(Gesture::Long, now);
            } else if (!emitted_ && held <= timing_.short_max_ms) {
                if (second_) { pending_ = false; sink_.gesture(Gesture::Double, now); }
                else { pending_ = true; released_ = changed_; }
            } else if (pending_) {
                pending_ = false; sink_.gesture(Gesture::Short, now);
            }
            second_ = false;
        }
    }
    // Very long fires while held; long waits for release so recovery does not
    // launch an ordinary action first. Ignore unvalidated release bounce.
    if (stable_ && raw_ && !emitted_ && now - pressed_ >= timing_.very_long_ms) {
        if (pending_) { pending_ = false; sink_.gesture(Gesture::Short, now); }
        sink_.gesture(Gesture::VeryLong, now);
        emitted_ = true;
    }
    // Don't expire a candidate second edge that arrived within the window.
    if (!stable_ && !(raw_ && pending_ && changed_ - released_ <= timing_.double_window_ms))
        expire(now);
    return true;
}
}
