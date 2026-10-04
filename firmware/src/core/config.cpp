#include "adapt/config.hpp"
#include <cstring>
namespace adapt::config {
bool encode(const Settings& s, uint32_t generation, Record& out) {
    if (!s.valid()) return false;
    Record r{};
    std::memcpy(r.data(),"ACFG",4); acp_write16(r.data()+4,schema);
    acp_write16(r.data()+6,32); acp_write32(r.data()+8,generation);
    const uint32_t timing[]={s.timing.short_max_ms,s.timing.double_window_ms,
        s.timing.long_ms,s.timing.very_long_ms,s.timing.debounce_ms};
    for (size_t i=0;i<5;++i) acp_write32(r.data()+12+4*i,timing[i]);
    for (size_t i=0;i<3;++i) acp_write16(r.data()+32+2*i,static_cast<uint16_t>(s.mapping[i]));
    r[38]=static_cast<uint8_t>(s.anc); r[39]=s.confirmation_tones ? 1 : 0;
    acp_write16(r.data()+40,static_cast<uint16_t>(s.preferred_action));
    r[42]=s.multipoint ? 1 : 0; r[43]=s.diagnostic_level;
    acp_write16(r.data()+44,acp_crc16(r.data(),44)); out=r; return true;
}
bool decode(const uint8_t* p, size_t n, Settings& out, uint32_t& generation) {
    if (!p || n<14 || std::memcmp(p,"ACFG",4)) return false;
    const auto version=acp_read16(p+4), length=acp_read16(p+6);
    // Schema 1 is the explicit legacy record: timing, mappings, ANC only.
    if (!((version==1 && length==27) || (version==schema && length==32)) ||
        n!=static_cast<size_t>(length)+14 || acp_crc16(p,n-2)!=acp_read16(p+n-2)) return false;
    Settings s{};
    s.timing={acp_read32(p+12),acp_read32(p+16),acp_read32(p+20),acp_read32(p+24),acp_read32(p+28)};
    for (size_t i=0;i<3;++i) s.mapping[i]=static_cast<Action>(acp_read16(p+32+2*i));
    s.anc=static_cast<Anc>(p[38]);
    if (version==schema) {
        if (p[39]>1 || p[42]>1) return false;
        s.confirmation_tones=p[39]!=0; s.preferred_action=static_cast<Action>(acp_read16(p+40));
        s.multipoint=p[42]!=0; s.diagnostic_level=p[43];
    }
    if (!s.valid()) return false;
    out=s; generation=acp_read32(p+8); return true;
}
}
