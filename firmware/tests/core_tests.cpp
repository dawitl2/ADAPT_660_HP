#include "adapt/core.hpp"
#include "adapt/host.hpp"
#include "check.hpp"
using namespace adapt;
struct Fixture {
    host::Backend h; Core c{h.platform(),31,"0.1.0-host"};
    acp_message command(uint8_t type, uint8_t key=0, uint32_t value=0) {
        acp_message m{}; m.type=type; m.sequence=42;
        if (type==ACP_SET_SETTING) { m.length=5; m.payload[0]=key; acp_write32(m.payload+1,value); }
        if (type==ACP_GET_SETTING) { m.length=1; m.payload[0]=key; }
        if (type==ACP_SET_ACTION_MAPPING) { m.length=3; m.payload[0]=key; acp_write16(m.payload+1,static_cast<uint16_t>(value)); }
        if (type==ACP_HELLO) { m.length=2; m.payload[1]=ACP_MINOR; }
        uint8_t wire[ACP_MAX_FRAME]; size_t n=0;
        CHECK(acp_encode(&m,wire,sizeof(wire),&n)==ACP_OK); c.receive(wire,n,h.usb);
        acp_message result{}; const auto& reply=h.usb.frames.back();
        CHECK(acp_decode(reply.data(),reply.size(),&result)==ACP_OK); CHECK(result.sequence==42); return result;
    }
    void error(uint8_t t,uint8_t key,uint32_t val,acp_error e) { auto r=command(t,key,val); CHECK(r.type==ACP_ERROR); CHECK(acp_read16(r.payload)==e); }
    void tick() { ++h.time; c.tick(); }
};
int main() {
    { Fixture f; auto r=f.command(ACP_GET_DEVICE_STATE); CHECK(r.length==26); CHECK(r.payload[0]==80); CHECK(r.payload[24]==0 && r.payload[25]==1);
      r=f.command(ACP_GET_CAPABILITIES); CHECK(acp_read32(r.payload)==31); CHECK(f.command(ACP_HELLO).type==ACP_HELLO);
      CHECK(f.command(ACP_PING).type==ACP_PONG); }
    { Fixture f; CHECK(f.command(ACP_SET_ACTION_MAPPING,1,ACP_CUSTOM_ACTION_8).type==ACP_SET_ACTION_MAPPING);
      f.c.gesture(Gesture::Short,1234567890123ULL); acp_message m{}; const auto& wire=f.h.bt.frames.back();
      CHECK(acp_decode(wire.data(),wire.size(),&m)==ACP_OK); CHECK(m.type==ACP_ACTION_EVENT);
      CHECK(acp_read16(m.payload+1)==ACP_CUSTOM_ACTION_8); CHECK(acp_read64(m.payload+3)==1234567890123ULL);
      f.error(ACP_SET_ACTION_MAPPING,4,1,ACP_ERR_INVALID); f.error(ACP_SET_ACTION_MAPPING,1,9,ACP_ERR_INVALID); }
    { Fixture f; f.c.gesture(Gesture::VeryLong,5000); CHECK(f.h.link==Link::Pairing);
      CHECK(f.h.bt.frames.size()==1); acp_message m{}; const auto& wire=f.h.bt.frames.back();
      CHECK(acp_decode(wire.data(),wire.size(),&m)==ACP_OK); CHECK(m.type==ACP_LOG_EVENT); }
    { Fixture f; f.h.usb.trusted=false; f.error(ACP_SET_SETTING,1,700,ACP_ERR_DENIED); CHECK(f.c.settings().timing.short_max_ms==650); }
    { Fixture f; f.error(ACP_SET_SETTING,3,100,ACP_ERR_INVALID); f.error(ACP_SET_SETTING,6,257,ACP_ERR_INVALID);
      f.h.save_ok=false; f.error(ACP_SET_SETTING,1,700,ACP_ERR_STORAGE); CHECK(f.c.settings().timing.short_max_ms==650);
      f.error(ACP_SET_SETTING,6,1,ACP_ERR_STORAGE); CHECK(f.h.anc==Anc::On); f.h.save_ok=true;
      f.h.down=true; f.tick(); f.error(ACP_SET_SETTING,1,700,ACP_ERR_BUSY); }
    { Fixture f; CHECK(f.command(ACP_SET_SETTING,1,700).type==ACP_SET_SETTING);
      CHECK(acp_read32(f.command(ACP_GET_SETTING,1).payload+1)==700);
      Core rebooted{f.h.platform(),31,"0.1.0-host"}; CHECK(rebooted.settings().timing.short_max_ms==700); }
    { Fixture f; f.h.jack=true; f.tick(); CHECK(f.c.state().mode==Mode::ToAnalog);
      f.tick(); CHECK(f.c.state().mode==Mode::Analog); CHECK(!f.h.wireless_enabled);
      f.h.jack=false; f.tick(); CHECK(f.c.state().mode==Mode::ToWireless);
      f.tick(); CHECK(f.c.state().mode==Mode::WirelessRecovery); f.h.recovery_ok=false; f.tick();
      CHECK(f.c.state().mode==Mode::WirelessRecovery); f.h.recovery_ok=true; f.tick(); CHECK(f.c.state().mode==Mode::Wireless); }
    { Fixture f; f.h.jack=true; f.tick(); f.tick(); f.h.jack=false; f.tick(); f.tick();
      f.h.jack=true; f.tick(); CHECK(f.c.state().mode==Mode::ToAnalog); f.tick(); CHECK(f.c.state().mode==Mode::Analog); }
    { Fixture f; f.error(ACP_REQUEST_BOOTLOADER,0,0,ACP_ERR_DENIED); f.error(ACP_REQUEST_REBOOT,0,0,ACP_ERR_DENIED);
      CHECK(!f.h.boot_pending); f.h.allow_boot=true; CHECK(f.command(ACP_REQUEST_REBOOT).type==ACP_REQUEST_REBOOT); CHECK(f.h.boot_pending); }
    { Fixture f; f.h.bt.connected=f.h.usb.connected=false; f.c.gesture(Gesture::Double,100); CHECK(f.h.feedback.back()==4); }
    { Fixture f; f.h.battery=101; CHECK(f.command(ACP_GET_DEVICE_STATE).payload[0]==255);
      f.h.anc_ok=false; f.error(ACP_SET_SETTING,6,1,ACP_ERR_HAL); }
    { host::Backend h; h.stored=true; h.persisted.timing.debounce_ms=0; Core c{h.platform(),31,"host"}; CHECK(c.settings().timing.debounce_ms==25); }
    std::cout << "core actions, configuration, persistence, authorization, recovery and HAL failures passed\n";
}
