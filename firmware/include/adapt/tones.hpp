#pragma once
#include <array>
#include <cstddef>
#include <cstdint>
namespace adapt {
struct ToneSegment { uint16_t hz=0, duration_ms=0, amplitude=0; };
struct TonePattern { std::array<ToneSegment,3> segments{}; size_t count=0; };
TonePattern confirmation_tone(uint16_t feedback_code);
uint32_t tone_duration_ms(const TonePattern& pattern);
// Original sine synthesis with 5 ms edge ramps; normalized gain, not acoustic SPL.
int16_t tone_sample(const TonePattern& pattern, uint32_t sample_index, uint32_t rate=16000);
}
