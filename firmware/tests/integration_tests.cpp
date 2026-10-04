#include "adapt/core.hpp"
#include "adapt/host.hpp"
#include "check.hpp"
#include <cstring>
using namespace adapt;
struct Fixture {
    host::Backend h; Core c{h.platform(),255,"0.2.0-host"};
    acp_message request(uint8_t type, uint8_t key=0, uint32_t value=0) {
        acp_message m{}; m.type=type; m.sequence=99;
        if (type==ACP_GET_SETTING || type==ACP_GET_DIAGNOSTIC) { m.length=1; m.payload[0]=key; }
        if (type==ACP_SET_SETTING) { m.length=5; m.payload[0]=key; acp_write32(m.payload+1,value); }
        uint8_t wire[ACP_MAX_FRAME]; size_t n=0; CHECK(acp_encode(&m,wire,sizeof(wire),&n)==ACP_OK);
        c.receive(wire,n,h.usb); acp_message r{}; const auto& f=h.usb.frames.back();
        CHECK(acp_decode(f.data(),f.size(),&r)==ACP_OK); return r;
    }
    void tick(uint64_t delta=1) { h.time+=delta; c.tick(); }
};
int main() {
    { Fixture f; auto r=f.request(ACP_FIRMWARE_METADATA);
      CHECK(r.length==83 && r.payload[82]==0); CHECK(std::strcmp(reinterpret_cast<char*>(r.payload),"ADAPT 660 HP")==0);
      CHECK(f.request(ACP_GET_DIAGNOSTIC).payload[0]==1);
      f.h.usb.trusted=false; CHECK(f.request(ACP_GET_DIAGNOSTIC).type==ACP_ERROR); }
    { Fixture f; f.tick(); f.h.link=Link::Connected; f.h.peers=2; f.h.active_peer=1; f.tick();
      auto r=f.request(ACP_LIFECYCLE_STATE); CHECK(r.payload[0]==4 && r.payload[1]==2 && r.payload[2]==1);
      f.h.peers=1; f.h.active_peer=0; f.tick(); CHECK(f.c.lifecycle()==LifecycleState::Connected);
      f.h.call=true; f.tick(); CHECK(f.c.lifecycle()==LifecycleState::CallActive);
      f.h.call=false; f.h.audio=Activity::Active; f.tick(); CHECK(f.c.lifecycle()==LifecycleState::AudioActive);
      f.h.wake=false; f.tick(); CHECK(f.c.lifecycle()==LifecycleState::Off);
      CHECK(f.request(ACP_LIFECYCLE_STATE).payload[1]==0);
      f.h.wake=true; f.tick(); CHECK(f.c.lifecycle()==LifecycleState::Booting);
      f.tick(); CHECK(f.h.restart_count==1); f.tick(); CHECK(f.c.lifecycle()==LifecycleState::Connecting); }
    { Fixture f; f.tick(); f.h.wireless_ok=false; f.h.jack=true; f.tick(); f.tick();
      CHECK(f.c.state().mode==Mode::ToAnalog && f.c.lifecycle()==LifecycleState::Recovery);
      f.tick(500); f.tick(1000); CHECK(f.c.lifecycle()==LifecycleState::Error);
      const auto count=f.c.diagnostics().size(); f.tick(10000); CHECK(f.c.diagnostics().size()==count);
      f.h.wireless_ok=true; f.h.jack=false; f.tick(); f.tick(); f.tick();
      CHECK(f.c.state().mode==Mode::Wireless); }
    { Fixture f; f.tick(); f.h.responsive=false; f.h.recovery_ok=false; f.tick(); f.tick(2000);
      CHECK(f.h.restart_count==1); f.tick(499); CHECK(f.h.restart_count==1);
      f.tick(); CHECK(f.h.restart_count==2); f.tick(1000); CHECK(f.h.restart_count==3);
      CHECK(f.c.lifecycle()==LifecycleState::Error); f.tick(10000); CHECK(f.h.restart_count==3);
      f.h.recovery_ok=true; f.c.gesture(Gesture::VeryLong,f.h.time); f.tick();
      CHECK(f.h.restart_count==4 && f.h.link==Link::Pairing); f.tick(); CHECK(f.c.lifecycle()==LifecycleState::Pairable); }
    { Fixture f; CHECK(f.request(ACP_SET_SETTING,7,0).type==ACP_SET_SETTING);
      f.c.gesture(Gesture::Short,100); CHECK(f.h.feedback.empty());
      f.c.gesture(Gesture::VeryLong,5000); CHECK(f.h.feedback.back()==13);
      CHECK(f.request(ACP_SET_SETTING,7,2).type==ACP_ERROR);
      CHECK(f.request(ACP_SET_SETTING,8,65537).type==ACP_ERROR);
      CHECK(f.request(ACP_SET_SETTING,9,2).type==ACP_ERROR);
      CHECK(f.request(ACP_SET_SETTING,10,3).type==ACP_ERROR);
      CHECK(acp_read32(f.request(ACP_GET_SETTING,16).payload+1)==1);
      f.h.save_ok=false; CHECK(f.request(ACP_SET_SETTING,9,0).type==ACP_ERROR); CHECK(f.c.settings().multipoint);
      f.h.save_ok=true; CHECK(f.request(ACP_SET_SETTING,10,0).type==ACP_SET_SETTING);
      const auto size=f.c.diagnostics().size(); f.c.gesture(Gesture::Double,6000); CHECK(f.c.diagnostics().size()==size); }
    { Fixture f; f.h.bt.connected=f.h.usb.connected=false; f.c.gesture(Gesture::Long,2000);
      LogRecord r; CHECK(f.c.diagnostics().newest(0,r)); CHECK(r.code==4); CHECK(f.h.feedback.back()==4); }
    std::cout << "core lifecycle, metadata, diagnostics, config authorization, recovery and transport failure passed\n";
}
