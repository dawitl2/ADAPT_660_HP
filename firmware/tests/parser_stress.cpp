#include "adapt/gatt.hpp"
#include "adapt/host.hpp"
#include "check.hpp"
#include <cstring>
#include <random>
using namespace adapt;
int main() {
    std::mt19937 random(66002); host::Backend host; Core core{host.platform(),511,"stress"};
    gatt::Ingress ingress(core); uint32_t accepted=0, rejected=0;
    std::array<uint8_t,ACP_MAX_FRAME+8> wire{};
    for (unsigned iteration=0;iteration<100000;++iteration) {
        acp_message input{}; input.sequence=static_cast<uint16_t>(random());
        switch(iteration%6) {
        case 0: input.type=ACP_PING; input.length=static_cast<uint16_t>(random()%129); break;
        case 1: input.type=ACP_SET_SETTING; input.length=5; break;
        case 2: input.type=ACP_SET_ACTION_MAPPING; input.length=3; break;
        case 3: input.type=ACP_GET_DEVICE_STATE; break;
        case 4: input.type=ACP_STANDARD_CONTROL; input.length=3; break;
        default: input.type=ACP_GET_DIAGNOSTIC; input.length=1; break;
        }
        for (unsigned i=0;i<input.length;++i) input.payload[i]=static_cast<uint8_t>(random());
        size_t n=0; CHECK(acp_encode(&input,wire.data(),wire.size(),&n)==ACP_OK);
        if (iteration%5!=0) {
            const auto position=random()%n; wire[position]^=static_cast<uint8_t>(1+(random()%255));
            if (iteration%2==0) acp_write16(wire.data()+n-2,acp_crc16(wire.data(),n-2));
            if (iteration%7==0) n=random()%wire.size();
        }
        acp_message sentinel{}; std::memset(&sentinel,0xa5,sizeof(sentinel)); acp_message decoded=sentinel;
        if (acp_decode(wire.data(),n,&decoded)==ACP_OK) {
            ++accepted; std::array<uint8_t,ACP_MAX_FRAME> canonical{}; size_t length=0;
            CHECK(acp_encode(&decoded,canonical.data(),canonical.size(),&length)==ACP_OK);
            CHECK(length==n && std::memcmp(wire.data(),canonical.data(),n)==0);
        } else { ++rejected; CHECK(std::memcmp(&decoded,&sentinel,sizeof(decoded))==0); }
        host.usb.trusted=iteration%3!=0;
        core.receive(wire.data(),n,host.usb); CHECK(core.settings().valid()); CHECK(!host.boot_pending);
        host.usb.frames.clear(); host.bt.frames.clear(); host.feedback.clear();
        std::array<uint8_t,ACP_MAX_FRAME+4> fragment{};
        for (auto& b : fragment) b=static_cast<uint8_t>(random());
        ingress.write(gatt::Characteristic::Command,fragment.data(),random()%fragment.size(),iteration%2!=0,host.usb,iteration);
        CHECK(core.settings().valid()); host.usb.frames.clear(); host.bt.frames.clear(); host.feedback.clear();
    }
    CHECK(accepted>10000 && rejected>10000); CHECK(core.diagnostics().size()<=RingLog::capacity);
    std::cout << "100000 seeded structured parser/core and GATT mutations: " << accepted << " accepted, " << rejected << " rejected\n";
}
