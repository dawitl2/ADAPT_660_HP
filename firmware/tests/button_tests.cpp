#include "adapt/button.hpp"
#include "check.hpp"
#include <vector>
using namespace adapt;
struct Sink : ButtonSink {
    std::vector<Gesture> events;
    void gesture(Gesture g, uint64_t) override { events.push_back(g); }
};
struct Fixture {
    Sink sink; Button button{sink}; uint64_t time = 0;
    void level(bool down, uint64_t at) { CHECK(button.sample(down, at)); CHECK(button.sample(down, at + 25)); time = at + 25; }
    void press(uint64_t at, uint64_t duration) { level(true, at); level(false, at + duration); }
    void settle() { CHECK(button.sample(false, time + 401)); }
};
int main() {
    { Fixture f; f.press(100, 100); CHECK(f.sink.events.empty()); f.settle(); CHECK(f.sink.events == std::vector<Gesture>{Gesture::Short}); }
    { Fixture f; f.press(100, 100); f.press(400, 100); f.settle(); CHECK(f.sink.events == std::vector<Gesture>{Gesture::Double}); }
    { Fixture f; f.press(100, 2000); f.settle(); CHECK(f.sink.events == std::vector<Gesture>{Gesture::Long}); }
    { Fixture f; f.level(true, 100); CHECK(f.button.sample(true, 5100)); CHECK(f.button.sample(true, 6000)); f.level(false, 6100); f.settle(); CHECK(f.sink.events == std::vector<Gesture>{Gesture::VeryLong}); }
    // A failed double becomes one short, then the late press is independent.
    { Fixture f; f.press(100, 100); f.press(601, 100); f.settle(); CHECK(f.sink.events == (std::vector<Gesture>{Gesture::Short, Gesture::Short})); }
    // Inclusive window uses the physical edge, not debounce completion time.
    { Fixture f; f.press(100, 100); f.press(600, 100); f.settle(); CHECK(f.sink.events == std::vector<Gesture>{Gesture::Double}); }
    for (auto duration : {649u, 650u, 651u, 1499u, 1500u, 4999u, 5000u}) {
        Fixture f; f.press(100, duration); f.settle();
        if (duration <= 650) CHECK(f.sink.events == std::vector<Gesture>{Gesture::Short});
        else if (duration < 1500) CHECK(f.sink.events.empty()); // intentional dead band
        else if (duration < 5000) CHECK(f.sink.events == std::vector<Gesture>{Gesture::Long});
        else CHECK(f.sink.events == std::vector<Gesture>{Gesture::VeryLong});
    }
    { Fixture f; CHECK(f.button.sample(true, 10)); CHECK(f.button.sample(false, 20));
      CHECK(f.button.sample(true, 30)); CHECK(f.button.sample(false, 40)); f.settle(); CHECK(f.sink.events.empty()); }
    { Fixture f; f.level(true, 100); CHECK(f.button.sample(false, 200)); CHECK(f.button.sample(true, 210));
      f.level(false, 300); f.settle(); CHECK(f.sink.events == std::vector<Gesture>{Gesture::Short}); }
    { Fixture f; for (unsigned i = 0; i < 6; ++i) f.press(100 + i * 150, 60);
      f.settle(); CHECK(f.sink.events == (std::vector<Gesture>{Gesture::Double, Gesture::Double, Gesture::Double})); }
    { Fixture f; f.press(100, 100); f.press(300, 2000); f.settle();
      CHECK(f.sink.events == (std::vector<Gesture>{Gesture::Short, Gesture::Long})); }
    { Fixture f; f.press(100, 100); f.press(300, 700); f.settle(); CHECK(f.sink.events == std::vector<Gesture>{Gesture::Short}); }
    { Fixture f; CHECK(f.button.sample(false, 100)); CHECK(!f.button.sample(true, 99)); CHECK(f.button.sample(false, 101)); }
    { Fixture f; ButtonTiming timing; timing.long_ms = timing.short_max_ms; CHECK(!f.button.configure(timing));
      timing = {}; timing.double_window_ms = 600; CHECK(f.button.configure(timing));
      f.press(100, 100); CHECK(!f.button.configure({})); f.settle(); CHECK(f.sink.events.empty());
      CHECK(f.button.sample(false, 801)); CHECK(f.button.configure({})); }
    { Fixture f; f.level(true, 100); f.button.reset(200); CHECK(f.button.sample(false, 201)); CHECK(f.sink.events.empty()); }
    { Fixture f; const uint64_t base = 0x100000000ULL; f.press(base, 100); f.settle(); CHECK(f.sink.events == std::vector<Gesture>{Gesture::Short}); }
    { ButtonTiming timing; timing.double_window_ms=60000; CHECK(timing.valid());
      timing.double_window_ms=60001; CHECK(!timing.valid()); timing.double_window_ms=UINT32_MAX; CHECK(!timing.valid()); }
    std::cout << "button timing, bounce, rapid presses and configuration cases passed\n";
}
