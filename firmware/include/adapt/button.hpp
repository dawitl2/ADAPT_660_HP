#pragma once
#include <cstdint>

namespace adapt {
enum class Gesture : uint8_t { Short = 1, Double = 2, Long = 3, VeryLong = 4 };
struct ButtonTiming {
    uint32_t short_max_ms = 650;
    uint32_t double_window_ms = 400;
    uint32_t long_ms = 1500;
    uint32_t very_long_ms = 5000;
    uint32_t debounce_ms = 25;
    bool valid() const;
};
struct ButtonSink {
    virtual ~ButtonSink() = default;
    virtual void gesture(Gesture event, uint64_t time_ms) = 0;
};
// Call sample periodically, including while input is unchanged. Monotonic time.
class Button {
public:
    explicit Button(ButtonSink& sink, ButtonTiming timing = {});
    bool configure(ButtonTiming timing); // only while idle; pending presses retained
    bool sample(bool down, uint64_t now_ms); // false on time regression
    void reset(uint64_t now_ms);
private:
    ButtonSink& sink_;
    ButtonTiming timing_;
    bool raw_ = false, stable_ = false, pending_ = false, second_ = false;
    bool emitted_ = false;
    uint64_t changed_ = 0, pressed_ = 0, released_ = 0, last_ = 0;
    void expire(uint64_t now);
};
}
