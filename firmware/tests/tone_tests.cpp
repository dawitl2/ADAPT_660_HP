#include "adapt/tones.hpp"
#include "check.hpp"
#include <cstdlib>
using namespace adapt;
int main() {
    const unsigned expected[]={80,170,210,360};
    for (uint16_t code=10;code<=13;++code) {
        const auto p=confirmation_tone(code); CHECK(tone_duration_ms(p)==expected[code-10]);
        const auto n=tone_duration_ms(p)*16; bool nonzero=false;
        for (uint32_t i=0;i<n;++i) { const auto x=tone_sample(p,i); CHECK(std::abs(static_cast<int>(x))<=3072); nonzero|=x!=0; }
        CHECK(nonzero); CHECK(tone_sample(p,0)==0); CHECK(tone_sample(p,n-1)==0); CHECK(tone_sample(p,n)==0);
        CHECK(tone_sample(p,12,0)==0);
    }
    CHECK(confirmation_tone(4).count==0);
    CHECK(tone_sample(confirmation_tone(11),80*16)==0); // middle silence
    std::cout << "original tone patterns, envelopes, silence and gain bounds passed\n";
}
