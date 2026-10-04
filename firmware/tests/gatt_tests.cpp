#include "adapt/gatt.hpp"
#include "adapt/host.hpp"
#include "check.hpp"
#include <algorithm>
#include <cstring>
using namespace adapt;
int main() {
    host::Backend h; Core c{h.platform(),255,"host"}; gatt::Ingress in(c);
    acp_message m{}; m.type=ACP_SET_ACTION_MAPPING; m.length=3; m.payload[0]=1; acp_write16(m.payload+1,23);
    uint8_t wire[ACP_MAX_FRAME]; size_t n=0; CHECK(acp_encode(&m,wire,sizeof(wire),&n)==ACP_OK);
    uint8_t fragment[ACP_MAX_FRAME+4]{}; acp_write16(fragment,12); fragment[2]=0; fragment[3]=static_cast<uint8_t>(n);
    std::memcpy(fragment+4,wire,n);
    CHECK(in.write(gatt::Characteristic::Configuration,fragment,n+4,false,h.usb,0)==gatt::Result::Denied);
    h.usb.trusted=false; CHECK(in.write(gatt::Characteristic::Configuration,fragment,n+4,true,h.usb,0)==gatt::Result::Denied);
    h.usb.trusted=true;
    CHECK(in.write(gatt::Characteristic::Command,fragment,n+4,true,h.usb,0)==gatt::Result::WrongCharacteristic);
    for (size_t offset=0;offset<n;offset+=5) {
        const auto count=std::min(size_t{5},n-offset); fragment[2]=static_cast<uint8_t>(offset);
        std::memcpy(fragment+4,wire+offset,count);
        const auto result=in.write(gatt::Characteristic::Configuration,fragment,count+4,true,h.usb,offset);
        CHECK(result==(offset+count==n ? gatt::Result::Accepted : gatt::Result::Incomplete));
    }
    CHECK(c.settings().mapping[0]==Action::Custom8);
    fragment[2]=0; std::memcpy(fragment+4,wire,5);
    CHECK(in.write(gatt::Characteristic::Configuration,fragment,9,true,h.usb,100)==gatt::Result::Incomplete);
    fragment[2]=5; CHECK(in.write(gatt::Characteristic::Configuration,fragment,9,true,h.usb,1101)==gatt::Result::Invalid);
    fragment[2]=0; CHECK(in.write(gatt::Characteristic::Configuration,fragment,9,true,h.usb,1200)==gatt::Result::Incomplete);
    in.disconnect(); fragment[2]=5; CHECK(in.write(gatt::Characteristic::Configuration,fragment,9,true,h.usb,1201)==gatt::Result::Invalid);
    fragment[2]=0; std::memcpy(fragment+4,wire,n); fragment[n+3]^=1;
    CHECK(in.write(gatt::Characteristic::Configuration,fragment,n+4,true,h.usb,1300)==gatt::Result::Invalid);
    fragment[3]=255; CHECK(in.write(gatt::Characteristic::Configuration,fragment,9,true,h.usb,1301)==gatt::Result::Invalid);
    CHECK(in.write(gatt::Characteristic::ActionEvent,fragment,9,true,h.usb,1302)==gatt::Result::WrongCharacteristic);
    std::cout << "GATT ingress bounded reassembly, explicit writes, authentication, timeout and routing passed\n";
}
