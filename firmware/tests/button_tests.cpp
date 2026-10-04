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
    std::cout << "button smoke cases passed\n";
}
