#pragma once
#include "adapt/hal.hpp"
#include <vector>

namespace adapt::host {
struct Transport : hal::ControlTransport {
    bool trusted=true, connected=true;
    std::vector<std::vector<uint8_t>> frames;
    bool send(const uint8_t* data, size_t size) override {
        if (!connected) return false;
        frames.emplace_back(data,data+size); return true;
    }
    bool authorized() const override { return trusted; }
};
// Host-only synthetic state. No hardware IO or vendor commands.
struct Backend : hal::ButtonInput, hal::TouchSurface, hal::BluetoothAudio,
    hal::AncControl, hal::Microphones, hal::Speakers, hal::Battery, hal::Charger,
    hal::AnalogJack, hal::WearSensor, hal::PersistentSettings, hal::Feedback,
    hal::Clock, hal::BootRecovery, hal::Runtime, hal::StandardControls {
    Transport bt, usb;
    uint64_t time=0;
    bool down=false, jack=false, wireless_enabled=true, stored=false, save_ok=true;
    bool recovery_ok=true, anc_ok=true, pairing_ok=true, allow_boot=false, boot_pending=false;
    hal::BootRequest boot_kind=hal::BootRequest::Reboot;
    Link link=Link::Disconnected;
    Activity audio=Activity::Inactive, microphone=Activity::Inactive, speaker=Activity::Inactive, wear=Activity::Unknown;
    uint8_t battery=80;
    Charging charging=Charging::No;
    Anc anc=Anc::On;
    Settings persisted{};
    hal::PersistentSettings* durable=nullptr;
    bool power=true, wake=true, usb_audio=false, responsive=true, connecting=false, call=false;
    uint8_t peers=0, active_peer=255;
    uint32_t restart_count=0;
    bool ambient_enabled=false, multipoint_enabled=true, controls_ok=true, muted=false;
    uint8_t volume_percent=50;
    std::vector<hal::TouchGesture> touches;
    std::vector<hal::MediaCommand> media_commands;
    std::vector<uint16_t> feedback;
    hal::Platform platform() { return {*this,*this,bt,usb,*this,*this,*this,*this,*this,*this,*this,*this,*this,*this,*this,*this,this,this}; }
    uint32_t features() const override { return 15; }
    uint8_t volume() const override { return volume_percent; }
    bool ambient() const override { return ambient_enabled; }
    bool set_volume(uint8_t v) override { if (!controls_ok || v>100) return false; volume_percent=v; return true; }
    bool set_ambient(bool enabled) override { if (!controls_ok) return false; ambient_enabled=enabled; return true; }
    bool set_multipoint(bool enabled) override { if (!controls_ok || (!enabled && peers>1)) return false; multipoint_enabled=enabled; return true; }
    bool media(hal::MediaCommand cmd) override {
        if (!controls_ok || !wireless_enabled || link!=Link::Connected) return false;
        media_commands.push_back(cmd);
        switch(cmd) {
        case hal::MediaCommand::PlayPause: audio=audio==Activity::Active ? Activity::Inactive : Activity::Active; break;
        case hal::MediaCommand::AnswerCall: call=true; microphone=Activity::Active; break;
        case hal::MediaCommand::EndCall: call=false; microphone=Activity::Inactive; break;
        case hal::MediaCommand::ToggleMute: muted=!muted; break;
        default: break;
        }
        return true;
    }
    hal::TouchGesture take_touch() override {
        if (touches.empty()) return hal::TouchGesture::None;
        const auto g=touches.front(); touches.erase(touches.begin()); return g;
    }
    bool powered() const override { return power; }
    bool awake() const override { return wake; }
    bool usb_audio_active() const override { return usb_audio; }
    uint32_t boot_reason() const override { return 1; }
    Status radio_status() const override {
        const uint8_t count=link==Link::Connected ? (peers ? peers : uint8_t{1}) : uint8_t{0};
        return {true,responsive,connecting,call,count,count ? (active_peer==255 ? uint8_t{0} : active_peer) : uint8_t{255},
            multipoint_enabled ? uint8_t{2} : uint8_t{1}};
    }
    bool purple_down() const override { return down; }
    hal::TouchSample touch() const override { return {}; }
    Link link_state() const override { return link; }
    Activity audio_state() const override { return audio; }
    bool pair() override { if (!pairing_ok || !wireless_enabled) return false; link=Link::Pairing; return true; }
    bool wireless(bool enabled) override {
        wireless_enabled=enabled;
        if (!enabled) { link=Link::Disconnected; peers=0; active_peer=255; connecting=call=false; audio=microphone=speaker=Activity::Inactive; }
        return true;
    }
    bool recover_wireless() override { ++restart_count; if (!recovery_ok) return false;
        wireless(false); wireless(true); responsive=true; return true; }
    Anc anc_state() const override { return anc; }
    bool set_anc(Anc value) override { if (!anc_ok) return false; anc=value; return true; }
    Activity microphone_state() const override { return microphone; }
    Activity speaker_state() const override { return speaker; }
    uint8_t battery_percent() const override { return battery; }
    Charging charging_state() const override { return charging; }
    bool jack_inserted() const override { return jack; }
    Activity wear_state() const override { return wear; }
    bool load(Settings& out) const override { if (durable) return durable->load(out); if (!stored) return false; out=persisted; return true; }
    bool save(const Settings& value) override { if (!save_ok || (durable && !durable->save(value))) return false;
        persisted=value; stored=true; return true; }
    void signal(uint16_t code) override { feedback.push_back(code); }
    uint64_t now_ms() const override { return time; }
    bool permitted(hal::BootRequest) const override { return allow_boot; }
    bool request_boot(hal::BootRequest kind) override { if (!allow_boot) return false; boot_pending=true; boot_kind=kind; return true; }
};
}
