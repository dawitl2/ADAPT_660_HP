#pragma once
#include "adapt/hal.hpp"
#include <optional>

#if defined(ADAPT_ENABLE_PHYSICAL_WRITES)
#error "ADAPT 660 hardware, toolchain and stock restoration route are unverified"
#endif
namespace adapt::pending {
// TODO replace only with reviewed evidence for the exact board revision.
struct TargetDefinition {
    static constexpr bool hardware_verified=false;
    static constexpr const char* soc="UNKNOWN";
    static constexpr const char* sdk="UNKNOWN";
    static constexpr std::optional<unsigned> purple_gpio=std::nullopt;
    static constexpr std::optional<unsigned> flash_bytes=std::nullopt;
    static constexpr std::optional<unsigned> debug_millivolts=std::nullopt;
};
static_assert(!TargetDefinition::hardware_verified,"Pending backend must never masquerade as a verified target");
struct Transport : hal::ControlTransport {
    bool send(const uint8_t*,size_t) override { return false; }
    bool authorized() const override { return false; }
};
// Compile-time integration skeleton. No SDK calls, registers, fabricated sensors,
// radio stack, GPIO IO, USB IO or boot/write path. All unsupported commands fail.
struct Backend : hal::ButtonInput, hal::TouchSurface, hal::BluetoothAudio,
    hal::AncControl, hal::Microphones, hal::Speakers, hal::Battery, hal::Charger,
    hal::AnalogJack, hal::WearSensor, hal::PersistentSettings, hal::Feedback,
    hal::Clock, hal::BootRecovery {
    Transport bt,usb;
    hal::Platform platform() { return {*this,*this,bt,usb,*this,*this,*this,*this,*this,*this,*this,*this,*this,*this,*this,*this}; }
    bool purple_down() const override { return false; } // TODO verified input adapter
    hal::TouchSample touch() const override { return {}; }
    Link link_state() const override { return Link::Unknown; }
    Activity audio_state() const override { return Activity::Unknown; }
    bool pair() override { return false; }
    bool wireless(bool) override { return false; }
    bool recover_wireless() override { return false; } // TODO SDK subsystem restart
    Anc anc_state() const override { return Anc::Unknown; }
    bool set_anc(Anc) override { return false; }
    Activity microphone_state() const override { return Activity::Unknown; }
    Activity speaker_state() const override { return Activity::Unknown; }
    uint8_t battery_percent() const override { return 255; }
    Charging charging_state() const override { return Charging::Unknown; }
    bool jack_inserted() const override { return false; } // TODO verified detection
    Activity wear_state() const override { return Activity::Unknown; }
    bool load(Settings&) const override { return false; }
    bool save(const Settings&) override { return false; } // TODO vendor NVM transaction
    void signal(uint16_t) override {} // TODO bounded vendor tone playback task
    uint64_t now_ms() const override { return 0; } // TODO monotonic vendor clock
    bool permitted(hal::BootRequest) const override { return false; }
    bool request_boot(hal::BootRequest) override { return false; }
};
}
