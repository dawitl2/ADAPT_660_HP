#include "adapt/config.hpp"
#include "check.hpp"
#include <algorithm>
using namespace adapt;
int main() {
    Settings s; s.mapping[1]=Action::Custom8; s.timing.long_ms=1700;
    s.confirmation_tones=false; s.multipoint=false; s.diagnostic_level=2;
    s.preferred_action=Action::StudyCompanion;
    config::Record record{}; CHECK(config::encode(s,42,record));
    Settings loaded; uint32_t generation=0;
    CHECK(config::decode(record.data(),record.size(),loaded,generation));
    CHECK(generation==42 && loaded.mapping[1]==Action::Custom8 && loaded.timing.long_ms==1700);
    CHECK(!loaded.confirmation_tones && !loaded.multipoint && loaded.diagnostic_level==2);
    CHECK(loaded.preferred_action==Action::StudyCompanion);
    for (size_t n=0;n<record.size();++n) CHECK(!config::decode(record.data(),n,loaded,generation));
    for (size_t i=0;i<record.size();++i) {
        auto corrupt=record; corrupt[i]^=1; generation=123;
        CHECK(!config::decode(corrupt.data(),corrupt.size(),loaded,generation)); CHECK(generation==123);
        CHECK(loaded.mapping[1]==Action::Custom8);
    }
    auto legacy=record; acp_write16(legacy.data()+4,1); acp_write16(legacy.data()+6,27);
    acp_write16(legacy.data()+39,acp_crc16(legacy.data(),39));
    CHECK(config::decode(legacy.data(),41,loaded,generation));
    CHECK(loaded.mapping[1]==Action::Custom8 && loaded.confirmation_tones && loaded.multipoint);
    CHECK(loaded.preferred_action==Action::VoiceAssistant && loaded.diagnostic_level==1);
    auto unknown=record; acp_write16(unknown.data()+4,3);
    acp_write16(unknown.data()+44,acp_crc16(unknown.data(),44));
    CHECK(!config::decode(unknown.data(),unknown.size(),loaded,generation));
    record[39]=2; acp_write16(record.data()+44,acp_crc16(record.data(),44));
    CHECK(!config::decode(record.data(),record.size(),loaded,generation));
    s.timing.very_long_ms=s.timing.long_ms; CHECK(!config::encode(s,0,record));
    std::cout << "config v2 roundtrip, migration, corruption, truncation and semantic rejection passed\n";
}
