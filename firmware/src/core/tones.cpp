#include "adapt/tones.hpp"
#include <algorithm>
#include <cmath>
namespace adapt {
TonePattern confirmation_tone(uint16_t code) {
    switch(code) {
    case 10: return {{{{660,80,2048},{},{}}},1};
    case 11: return {{{{440,60,2048},{0,50,0},{660,60,2048}}},3};
    case 12: return {{{{440,70,2048},{660,70,2048},{880,70,2048}}},3};
    case 13: return {{{{220,120,3072},{0,80,0},{880,160,3072}}},3};
    default: return {};
    }
}
uint32_t tone_duration_ms(const TonePattern& p) {
    uint32_t result=0;
    for (size_t i=0;i<std::min(p.count,p.segments.size());++i) result+=p.segments[i].duration_ms;
    return result;
}
int16_t tone_sample(const TonePattern& p, uint32_t index, uint32_t rate) {
    if (rate<8000 || rate>96000) return 0;
    uint64_t offset=index;
    for (size_t i=0;i<std::min(p.count,p.segments.size());++i) {
        const auto s=p.segments[i]; const uint64_t length=static_cast<uint64_t>(rate)*s.duration_ms/1000;
        if (offset>=length) { offset-=length; continue; }
        const double ramp=static_cast<double>(rate)/200;
        const double edge=std::min({1.0,static_cast<double>(offset)/ramp,static_cast<double>(length-1-offset)/ramp});
        const auto gain=std::min(s.amplitude,uint16_t{4096});
        return static_cast<int16_t>(std::sin(6.283185307179586*s.hz*static_cast<double>(offset)/rate)*gain*edge);
    }
    return 0;
}
}
