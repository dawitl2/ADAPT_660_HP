#include "adapt/core.hpp"
#include <cstring>
namespace adapt {
Core::Core(hal::Platform platform, uint32_t caps, const char* version)
    : p_(platform), button_(*this), capabilities_(caps) {
    for (size_t i=0; version && version[i] && i<version_.size()-1; ++i)
        version_[i]=static_cast<uint8_t>(version[i]);
    Settings saved;
    if (p_.settings.load(saved)) {
        if (saved.valid()) settings_=saved;
        else log(5,0);
    }
    button_.configure(settings_.timing);
    button_.reset(p_.clock.now_ms());
    last_tick_=p_.clock.now_ms();
    if (!p_.anc.set_anc(settings_.anc)) log(6,6);
}
bool Core::send(acp_message m, hal::ControlTransport& t) {
    uint8_t frame[ACP_MAX_FRAME]; size_t size=0;
    return acp_encode(&m,frame,sizeof(frame),&size)==ACP_OK && t.send(frame,size);
}
void Core::event(acp_message m) {
    m.flags=ACP_EVENT; m.sequence=event_sequence_++;
    const bool bt=send(m,p_.bluetooth_control), usb=send(m,p_.usb_control);
    if (!bt && !usb) p_.feedback.signal(4); // no recursive logging on transport failure
}
void Core::log(uint16_t code, uint32_t value) {
    acp_message m{}; m.type=ACP_LOG_EVENT; m.length=6;
    acp_write16(m.payload,code); acp_write32(m.payload+2,value); event(m);
}
void Core::transition(Mode next) {
    mode_=next; log(1,static_cast<uint32_t>(next));
}
void Core::tick() {
    const auto now=p_.clock.now_ms();
    if (now<last_tick_) return;
    last_tick_=now;
    button_.sample(p_.button.purple_down(),now);
    const bool jack=p_.jack.jack_inserted();
    if (jack!=last_jack_) {
        last_jack_=jack; transition(jack ? Mode::ToAnalog : Mode::ToWireless);
        return;
    }
    switch(mode_) {
    case Mode::ToAnalog:
        if (p_.audio.wireless(false)) transition(Mode::Analog);
        else log(6,static_cast<uint32_t>(mode_));
        break;
    case Mode::ToWireless: transition(Mode::WirelessRecovery); break;
    case Mode::WirelessRecovery:
        if (p_.audio.recover_wireless()) transition(Mode::Wireless);
        else log(6,static_cast<uint32_t>(mode_));
        break;
    default: break;
    }
}
DeviceState Core::state() const {
    DeviceState s; s.battery=p_.battery.battery_percent();
    if (s.battery>100) s.battery=255;
    s.charging=p_.charger.charging_state(); s.anc=p_.anc.anc_state();
    s.link=p_.audio.link_state(); s.audio=p_.audio.audio_state();
    s.microphone=p_.microphones.microphone_state(); s.wear=p_.wear.wear_state(); s.mode=mode_;
    return s;
}
void Core::gesture(Gesture g, uint64_t time) {
    if (g==Gesture::VeryLong) {
        if (p_.audio.pair()) { p_.feedback.signal(2); log(2,0); }
        else log(6,8);
        return;
    }
    const auto id=static_cast<uint8_t>(g);
    if (id<1 || id>3) return;
    acp_message m{}; m.type=ACP_ACTION_EVENT; m.length=11;
    m.payload[0]=id; acp_write16(m.payload+1,static_cast<uint16_t>(settings_.mapping[id-1]));
    acp_write64(m.payload+3,time); event(m);
}
void Core::error(uint16_t seq, uint8_t type, acp_error code, hal::ControlTransport& source) {
    acp_message m{}; m.type=ACP_ERROR; m.flags=ACP_RESPONSE; m.sequence=seq; m.length=3;
    acp_write16(m.payload,static_cast<uint16_t>(code)); m.payload[2]=type;
    if (!send(m,source)) p_.feedback.signal(4);
}
bool Core::setting(uint8_t key, uint32_t& value) const {
    switch(key) {
    case 1: value=settings_.timing.short_max_ms; break;
    case 2: value=settings_.timing.double_window_ms; break;
    case 3: value=settings_.timing.long_ms; break;
    case 4: value=settings_.timing.very_long_ms; break;
    case 5: value=settings_.timing.debounce_ms; break;
    case 6: value=static_cast<uint32_t>(settings_.anc); break;
    default: return false;
    }
    return true;
}
void Core::receive(const uint8_t* frame, size_t size, hal::ControlTransport& source) {
    acp_message m{};
    if (acp_decode(frame,size,&m)!=ACP_OK) { error(0,0,ACP_ERR_INVALID,source); return; }
    if (m.flags!=0) { error(m.sequence,m.type,ACP_ERR_INVALID,source); return; }
    auto fail=[&](acp_error code) { error(m.sequence,m.type,code,source); };
    const bool write=m.type==ACP_SET_ACTION_MAPPING || m.type==ACP_SET_SETTING ||
        m.type==ACP_ENTER_PAIRING || m.type==ACP_REQUEST_REBOOT || m.type==ACP_REQUEST_BOOTLOADER;
    if (write && !source.authorized()) { fail(ACP_ERR_DENIED); return; }
    acp_message reply=m; reply.flags=ACP_RESPONSE;
    switch(m.type) {
    case ACP_HELLO:
        if (m.payload[0]!=ACP_MAJOR || m.payload[1]!=ACP_MINOR) { fail(ACP_ERR_UNSUPPORTED); return; }
        break;
    case ACP_GET_CAPABILITIES: reply.length=4; acp_write32(reply.payload,capabilities_); break;
    case ACP_GET_DEVICE_STATE: {
        const auto s=state(); reply.length=26;
        reply.payload[0]=s.battery; reply.payload[1]=static_cast<uint8_t>(s.charging);
        reply.payload[2]=static_cast<uint8_t>(s.anc); reply.payload[3]=static_cast<uint8_t>(s.link);
        reply.payload[4]=static_cast<uint8_t>(s.audio); reply.payload[5]=static_cast<uint8_t>(s.microphone);
        reply.payload[6]=static_cast<uint8_t>(s.wear); reply.payload[7]=static_cast<uint8_t>(s.mode);
        std::memcpy(reply.payload+8,version_.data(),16); reply.payload[24]=ACP_MAJOR; reply.payload[25]=ACP_MINOR;
        break;
    }
    case ACP_SET_ACTION_MAPPING: {
        const auto g=m.payload[0]; const auto action=acp_read16(m.payload+1);
        if (g<1 || g>3 || !valid_action(action)) { fail(ACP_ERR_INVALID); return; }
        auto next=settings_; next.mapping[g-1]=static_cast<Action>(action);
        if (!p_.settings.save(next)) { fail(ACP_ERR_STORAGE); return; }
        settings_=next; break;
    }
    case ACP_GET_SETTING: {
        uint32_t value=0;
        if (!setting(m.payload[0],value)) { fail(ACP_ERR_UNSUPPORTED); return; }
        reply.length=5; acp_write32(reply.payload+1,value); break;
    }
    case ACP_SET_SETTING: {
        auto next=settings_; const auto value=acp_read32(m.payload+1); const auto key=m.payload[0];
        switch(key) {
        case 1: next.timing.short_max_ms=value; break;
        case 2: next.timing.double_window_ms=value; break;
        case 3: next.timing.long_ms=value; break;
        case 4: next.timing.very_long_ms=value; break;
        case 5: next.timing.debounce_ms=value; break;
        case 6:
            if (value<1 || value>3) { fail(ACP_ERR_INVALID); return; }
            next.anc=static_cast<Anc>(value); break;
        default: fail(ACP_ERR_UNSUPPORTED); return;
        }
        if (!next.valid()) { fail(ACP_ERR_INVALID); return; }
        if (key!=6 && !button_.configure(next.timing)) { fail(ACP_ERR_BUSY); return; }
        if (key==6 && !p_.anc.set_anc(next.anc)) { fail(ACP_ERR_HAL); return; }
        if (!p_.settings.save(next)) {
            if (key==6 && !p_.anc.set_anc(settings_.anc)) log(6,6);
            if (key!=6) button_.configure(settings_.timing);
            fail(ACP_ERR_STORAGE); return;
        }
        settings_=next; break;
    }
    case ACP_ENTER_PAIRING:
        if (!p_.audio.pair()) { fail(ACP_ERR_HAL); return; }
        log(2,0); break;
    case ACP_REQUEST_REBOOT: case ACP_REQUEST_BOOTLOADER: {
        const auto request=m.type==ACP_REQUEST_REBOOT ? hal::BootRequest::Reboot : hal::BootRequest::Bootloader;
        if (!p_.boot.permitted(request)) { fail(ACP_ERR_DENIED); return; }
        if (!p_.boot.request_boot(request)) { fail(ACP_ERR_HAL); return; }
        log(3,static_cast<uint32_t>(request)); break;
    }
    case ACP_PING: reply.type=ACP_PONG; break;
    default: fail(ACP_ERR_UNSUPPORTED); return;
    }
    if (!send(reply,source)) p_.feedback.signal(4);
}
}
