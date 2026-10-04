#include "adapt/core.hpp"
#include "adapt/host.hpp"
#include "check.hpp"
using namespace adapt;
int main() {
    host::Backend h; Core core{h.platform(),511,"host"};
    auto command=[&](uint8_t type, uint8_t op, uint32_t value) {
        acp_message m{}; m.type=type; m.sequence=12; m.length=type==ACP_SET_SETTING ? 5 : 3;
        m.payload[0]=op; if (m.length==5) acp_write32(m.payload+1,value); else acp_write16(m.payload+1,static_cast<uint16_t>(value));
        uint8_t wire[ACP_MAX_FRAME]; size_t n=0; CHECK(acp_encode(&m,wire,sizeof(wire),&n)==ACP_OK);
        core.receive(wire,n,h.usb); acp_message reply{};
        const auto& result=h.usb.frames.back(); CHECK(acp_decode(result.data(),result.size(),&reply)==ACP_OK); return reply;
    };
    CHECK(command(ACP_STANDARD_CONTROL,1,75).type==ACP_STANDARD_CONTROL && h.volume()==75);
    CHECK(command(ACP_STANDARD_CONTROL,1,101).type==ACP_ERROR && h.volume()==75);
    CHECK(command(ACP_STANDARD_CONTROL,3,1).type==ACP_STANDARD_CONTROL && h.ambient());
    CHECK(command(ACP_STANDARD_CONTROL,3,2).type==ACP_ERROR);
    CHECK(command(ACP_STANDARD_CONTROL,2,1).type==ACP_ERROR); // no radio link
    h.link=Link::Connected;
    CHECK(command(ACP_STANDARD_CONTROL,2,1).type==ACP_STANDARD_CONTROL && h.audio==Activity::Active);
    CHECK(command(ACP_STANDARD_CONTROL,2,4).type==ACP_STANDARD_CONTROL && h.call);
    CHECK(command(ACP_STANDARD_CONTROL,2,6).type==ACP_STANDARD_CONTROL && h.muted);
    CHECK(command(ACP_STANDARD_CONTROL,2,5).type==ACP_STANDARD_CONTROL && !h.call);
    h.usb.trusted=false; CHECK(command(ACP_STANDARD_CONTROL,1,5).type==ACP_ERROR && h.volume()==75); h.usb.trusted=true;
    h.controls_ok=false; CHECK(command(ACP_STANDARD_CONTROL,1,5).type==ACP_ERROR); h.controls_ok=true;
    h.peers=2; CHECK(command(ACP_SET_SETTING,9,0).type==ACP_ERROR && h.multipoint_enabled);
    h.peers=1; h.save_ok=false; CHECK(command(ACP_SET_SETTING,9,0).type==ACP_ERROR && h.multipoint_enabled);
    h.save_ok=true; CHECK(command(ACP_SET_SETTING,9,0).type==ACP_SET_SETTING && !h.multipoint_enabled);
    h.touches={hal::TouchGesture::SwipeUp,hal::TouchGesture::SwipeDown,hal::TouchGesture::DoubleTap,hal::TouchGesture::SwipeForward};
    for (unsigned i=0;i<4;++i) { ++h.time; core.tick(); }
    CHECK(h.volume()==75 && !h.ambient()); CHECK(h.media_commands.back()==hal::MediaCommand::Next);
    h.call=true; h.touches={hal::TouchGesture::Tap}; ++h.time; core.tick(); CHECK(!h.call);
    auto platform=h.platform(); platform.controls=nullptr; Core unavailable{platform,511,"host"};
    // No physical feature implementation is implied by a capability bit.
    acp_message m{}; m.type=ACP_STANDARD_CONTROL; m.length=3; m.payload[0]=1;
    uint8_t frame[ACP_MAX_FRAME]; size_t n=0; CHECK(acp_encode(&m,frame,sizeof(frame),&n)==ACP_OK);
    unavailable.receive(frame,n,h.usb); acp_message r{}; const auto& last=h.usb.frames.back();
    CHECK(acp_decode(last.data(),last.size(),&r)==ACP_OK && acp_read16(r.payload)==ACP_ERR_UNSUPPORTED);
    std::cout << "standard volume/media/call/ambient/touch, multipoint rollback and unsupported HAL passed\n";
}
