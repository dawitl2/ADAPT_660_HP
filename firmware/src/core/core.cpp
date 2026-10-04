#include "adapt/core.hpp"
#include "adapt/config.hpp"
#include "adapt/identity.hpp"
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
    if (p_.controls && (p_.controls->features() & 8) && !p_.controls->set_multipoint(settings_.multipoint)) log(6,9);
    if (settings_.diagnostic_level) diagnostics_.push(last_tick_,10,p_.runtime ? p_.runtime->boot_reason() : 0);
}
bool Core::send(acp_message m, hal::ControlTransport& t) {
    uint8_t frame[ACP_MAX_FRAME]; size_t size=0;
    return acp_encode(&m,frame,sizeof(frame),&size)==ACP_OK && t.send(frame,size);
}
void Core::event(acp_message m) {
    m.flags=ACP_EVENT; m.sequence=event_sequence_++;
    const bool bt=send(m,p_.bluetooth_control), usb=send(m,p_.usb_control);
    if (!bt && !usb) {
        p_.feedback.signal(4); // no recursive logging on transport failure
        if (settings_.diagnostic_level) diagnostics_.push(p_.clock.now_ms(),4,m.type);
    }
}
void Core::log(uint16_t code, uint32_t value) {
    if (!settings_.diagnostic_level) return;
    diagnostics_.push(p_.clock.now_ms(),code,value);
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
    const bool running=!p_.runtime || (p_.runtime->powered() && p_.runtime->awake());
    if (!running) {
        pair_after_recovery_=false;
        button_.reset(now);
        if (last_running_ && !p_.audio.wireless(false)) log(6,0);
        last_running_=false;
        lifecycle_input_.powered=false; lifecycle_input_.peers=0; lifecycle_input_.active_peer=255;
        lifecycle_.tick(lifecycle_input_,now);
        if (last_lifecycle_!=lifecycle_.state()) { last_lifecycle_=lifecycle_.state(); log(20,0);
            if (capabilities_ & ACP_CAP_LIFECYCLE) { acp_message m{}; m.type=ACP_LIFECYCLE_STATE; lifecycle_payload(m); event(m); } }
        return;
    }
    if (!last_running_) {
        last_running_=true; button_.reset(now); lifecycle_.request_recovery(now);
        if (!p_.jack.jack_inserted()) transition(Mode::WirelessRecovery);
        else { disable_attempts_=0; next_disable_=now; transition(Mode::ToAnalog); }
    }
    button_.sample(p_.button.purple_down(),now);
    touch();
    const bool jack=p_.jack.jack_inserted();
    if (jack) pair_after_recovery_=false;
    if (jack!=last_jack_) {
        disable_attempts_=0; next_disable_=now;
        last_jack_=jack; log(jack ? 14 : 15,0); transition(jack ? Mode::ToAnalog : Mode::ToWireless);
        return;
    }
    const bool defer_restart=mode_==Mode::ToWireless;
    switch(mode_) {
    case Mode::ToAnalog:
        if (disable_attempts_<3 && now>=next_disable_) {
            ++disable_attempts_;
            if (p_.audio.wireless(false)) transition(Mode::Analog);
            else { log(6,static_cast<uint32_t>(mode_)); next_disable_=now+500u*disable_attempts_; }
        }
        break;
    case Mode::ToWireless: transition(Mode::WirelessRecovery); lifecycle_.request_recovery(now); break;
    default: break;
    }
    const auto radio=p_.audio.radio_status();
    lifecycle_input_.powered=true; lifecycle_input_.awake=true;
    lifecycle_input_.analog=jack && mode_==Mode::Analog;
    lifecycle_input_.mode_transition=mode_==Mode::ToAnalog;
    lifecycle_input_.transition_failed=mode_==Mode::ToAnalog && disable_attempts_>=3;
    lifecycle_input_.usb_audio=p_.runtime && p_.runtime->usb_audio_active();
    lifecycle_input_.radio_available=radio.available || p_.audio.link_state()!=Link::Unknown;
    lifecycle_input_.responsive=radio.available ? radio.responsive : true;
    lifecycle_input_.connecting=radio.available && radio.connecting;
    lifecycle_input_.pairable=p_.audio.link_state()==Link::Pairing;
    lifecycle_input_.audio=p_.audio.audio_state()==Activity::Active;
    lifecycle_input_.call=radio.available && radio.call;
    lifecycle_input_.peers=radio.available ? radio.peers : p_.audio.link_state()==Link::Connected ? 1 : 0;
    lifecycle_input_.active_peer=radio.available ? radio.active_peer : lifecycle_input_.peers ? 0 : 255;
    lifecycle_input_.max_peers=radio.available ? radio.max_peers : 1;
    if (lifecycle_.tick(lifecycle_input_,now) && !defer_restart) {
        log(19,mode_==Mode::WirelessRecovery ? 0 : 1);
        const bool ok=p_.audio.recover_wireless(); lifecycle_.restarted(ok,now); log(13,ok ? 1 : 0);
        if (ok) {
            lifecycle_input_.peers=0; lifecycle_input_.active_peer=255;
            lifecycle_input_.audio=lifecycle_input_.call=false;
            if (mode_==Mode::WirelessRecovery) transition(Mode::Wireless);
            if (pair_after_recovery_) {
                pair_after_recovery_=false;
                if (p_.audio.pair()) { p_.feedback.signal(13); log(2,0); }
                else log(6,8);
            }
        }
    }
    const bool peers_changed=lifecycle_input_.peers!=last_peers_;
    if (peers_changed) {
        log(lifecycle_input_.peers>last_peers_ ? 11 : 12,lifecycle_input_.peers);
        last_peers_=lifecycle_input_.peers;
    }
    const bool detail_changed=lifecycle_input_.active_peer!=last_active_peer_ || lifecycle_.attempts()!=last_attempts_ ||
        lifecycle_input_.max_peers!=last_max_peers_;
    last_active_peer_=lifecycle_input_.active_peer; last_attempts_=lifecycle_.attempts(); last_max_peers_=lifecycle_input_.max_peers;
    if (lifecycle_.state()!=last_lifecycle_ || peers_changed || detail_changed) {
        last_lifecycle_=lifecycle_.state(); log(20,static_cast<uint32_t>(last_lifecycle_));
        if (capabilities_ & ACP_CAP_LIFECYCLE) { acp_message m{}; m.type=ACP_LIFECYCLE_STATE; lifecycle_payload(m); event(m); }
    }
}
void Core::lifecycle_payload(acp_message& m) const {
    m.length=5; m.payload[0]=static_cast<uint8_t>(lifecycle_.state());
    m.payload[1]=lifecycle_input_.peers; m.payload[2]=lifecycle_input_.active_peer;
    m.payload[3]=lifecycle_.attempts(); m.payload[4]=lifecycle_input_.max_peers;
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
    if (p_.runtime && (!p_.runtime->powered() || !p_.runtime->awake())) return;
    log(16,static_cast<uint32_t>(g));
    if (g==Gesture::VeryLong) {
        if (lifecycle_.state()==LifecycleState::Error || !p_.audio.pair()) {
            lifecycle_.request_recovery(time); pair_after_recovery_=true; log(6,8);
        } else { p_.feedback.signal(13); log(2,0); }
        return;
    }
    const auto id=static_cast<uint8_t>(g);
    if (id<1 || id>3) return;
    if (settings_.confirmation_tones) p_.feedback.signal(static_cast<uint16_t>(9+id));
    acp_message m{}; m.type=ACP_ACTION_EVENT; m.length=11;
    m.payload[0]=id; acp_write16(m.payload+1,static_cast<uint16_t>(settings_.mapping[id-1]));
    acp_write64(m.payload+3,time); event(m);
}
void Core::error(uint16_t seq, uint8_t type, acp_error code, hal::ControlTransport& source) {
    log(18,static_cast<uint32_t>(code));
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
    case 7: value=settings_.confirmation_tones ? 1 : 0; break;
    case 8: value=static_cast<uint32_t>(settings_.preferred_action); break;
    case 9: value=settings_.multipoint ? 1 : 0; break;
    case 10: value=settings_.diagnostic_level; break;
    case 11: if (!p_.controls) return false; value=p_.controls->volume(); break;
    case 12: if (!p_.controls) return false; value=p_.controls->ambient() ? 1 : 0; break;
    case 13: if (!p_.controls) return false; value=p_.controls->features(); break;
    case 16: case 17: case 18: value=static_cast<uint32_t>(settings_.mapping[key-16]); break;
    default: return false;
    }
    return true;
}
bool Core::standard_control(uint8_t op, uint16_t value, acp_error& result) {
    result=ACP_ERR_INVALID;
    if (op<1 || op>3 || (op==1 && value>100) || (op==2 && (value<1 || value>6)) || (op==3 && value>1)) return false;
    result=ACP_ERR_UNSUPPORTED;
    if (!p_.controls || !(p_.controls->features() & (1u << (op-1)))) return false;
    result=ACP_ERR_HAL;
    if (op==1) return p_.controls->set_volume(static_cast<uint8_t>(value));
    if (op==2) return p_.controls->media(static_cast<hal::MediaCommand>(value));
    return p_.controls->set_ambient(value!=0);
}
void Core::touch() {
    if (!p_.controls) return;
    const auto g=p_.controls->take_touch();
    if (g==hal::TouchGesture::None) return;
    const bool call=p_.audio.radio_status().call;
    uint8_t op=2; uint16_t value=1;
    switch(g) {
    case hal::TouchGesture::Tap: value=call ? 5 : 1; break;
    case hal::TouchGesture::DoubleTap: op=3; value=p_.controls->ambient() ? 0 : 1; break;
    case hal::TouchGesture::SwipeUp: op=1; value=static_cast<uint16_t>(p_.controls->volume()+5); if (value>100) value=100; break;
    case hal::TouchGesture::SwipeDown: op=1; value=p_.controls->volume()>=5 ? static_cast<uint16_t>(p_.controls->volume()-5) : 0; break;
    case hal::TouchGesture::SwipeForward: value=2; break;
    case hal::TouchGesture::SwipeBack: value=3; break;
    default: return;
    }
    acp_error error_code; if (!standard_control(op,value,error_code)) log(6,static_cast<uint32_t>(error_code));
}
void Core::receive(const uint8_t* frame, size_t size, hal::ControlTransport& source) {
    acp_message m{};
    if (acp_decode(frame,size,&m)!=ACP_OK) { error(0,0,ACP_ERR_INVALID,source); return; }
    if (m.flags!=0) { error(m.sequence,m.type,ACP_ERR_INVALID,source); return; }
    auto fail=[&](acp_error code) { error(m.sequence,m.type,code,source); };
    const bool write=m.type==ACP_SET_ACTION_MAPPING || m.type==ACP_SET_SETTING ||
        m.type==ACP_ENTER_PAIRING || m.type==ACP_REQUEST_REBOOT || m.type==ACP_REQUEST_BOOTLOADER || m.type==ACP_STANDARD_CONTROL;
    if (write && !source.authorized()) { fail(ACP_ERR_DENIED); return; }
    acp_message reply=m; reply.flags=ACP_RESPONSE;
    switch(m.type) {
    case ACP_STANDARD_CONTROL: {
        if (!(capabilities_ & ACP_CAP_STANDARD_CONTROL)) { fail(ACP_ERR_UNSUPPORTED); return; }
        acp_error e; if (!standard_control(m.payload[0],acp_read16(m.payload+1),e)) { fail(e); return; }
        break;
    }
    case ACP_GET_DIAGNOSTIC: {
        if (!(capabilities_ & ACP_CAP_DIAGNOSTICS)) { fail(ACP_ERR_UNSUPPORTED); return; }
        if (!source.authorized()) { fail(ACP_ERR_DENIED); return; }
        LogRecord r;
        if (!diagnostics_.newest(m.payload[0],r)) { fail(ACP_ERR_INVALID); return; }
        reply.length=19; reply.payload[0]=static_cast<uint8_t>(diagnostics_.size());
        acp_write32(reply.payload+1,r.serial); acp_write64(reply.payload+5,r.time_ms);
        acp_write16(reply.payload+13,r.code); acp_write32(reply.payload+15,r.value); break;
    }
    case ACP_LIFECYCLE_STATE:
        if (!(capabilities_ & ACP_CAP_LIFECYCLE)) { fail(ACP_ERR_UNSUPPORTED); return; }
        lifecycle_payload(reply); break;
    case ACP_FIRMWARE_METADATA:
        if (!(capabilities_ & ACP_CAP_METADATA)) { fail(ACP_ERR_UNSUPPORTED); return; }
        reply.length=83; std::memset(reply.payload,0,reply.length);
        std::memcpy(reply.payload,identity::product,sizeof(identity::product));
        std::memcpy(reply.payload+32,identity::firmware,sizeof(identity::firmware));
        std::memcpy(reply.payload+56,identity::protocol,sizeof(identity::protocol));
        acp_write16(reply.payload+80,config::schema); reply.payload[82]=0; break;
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
        case 7: case 9:
            if (value>1) { fail(ACP_ERR_INVALID); return; }
            if (key==7) next.confirmation_tones=value!=0; else next.multipoint=value!=0;
            break;
        case 8:
            if (value>65535 || !valid_action(static_cast<uint16_t>(value))) { fail(ACP_ERR_INVALID); return; }
            next.preferred_action=static_cast<Action>(value); break;
        case 10:
            if (value>2) { fail(ACP_ERR_INVALID); return; }
            next.diagnostic_level=static_cast<uint8_t>(value); break;
        default: fail(ACP_ERR_UNSUPPORTED); return;
        }
        if (!next.valid()) { fail(ACP_ERR_INVALID); return; }
        if (key<=5 && !button_.configure(next.timing)) { fail(ACP_ERR_BUSY); return; }
        if (key==6 && !p_.anc.set_anc(next.anc)) { fail(ACP_ERR_HAL); return; }
        const bool apply_multipoint=key==9 && p_.controls && (p_.controls->features() & 8);
        if (apply_multipoint && !p_.controls->set_multipoint(next.multipoint)) { fail(ACP_ERR_HAL); return; }
        if (!p_.settings.save(next)) {
            if (key==6 && !p_.anc.set_anc(settings_.anc)) log(6,6);
            if (key<=5) button_.configure(settings_.timing);
            if (apply_multipoint && !p_.controls->set_multipoint(settings_.multipoint)) log(6,9);
            fail(ACP_ERR_STORAGE); return;
        }
        settings_=next; if (key==6) log(17,value); break;
    }
    case ACP_ENTER_PAIRING:
        if (!p_.audio.pair()) { fail(ACP_ERR_HAL); return; }
        p_.feedback.signal(13); log(2,0); break;
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
