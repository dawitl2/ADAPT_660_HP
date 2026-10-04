#pragma once
#include "adapt/model.hpp"
#include <cstddef>

namespace adapt::hal {
struct ButtonInput { virtual ~ButtonInput() = default; virtual bool purple_down() const = 0; };
struct TouchSample { bool available = false; int16_t x = 0, y = 0; bool touched = false; };
struct TouchSurface { virtual ~TouchSurface() = default; virtual TouchSample touch() const = 0; };
// One complete v0.1 frame per send; transport adapters perform fragmentation.
struct ControlTransport {
    virtual ~ControlTransport() = default;
    virtual bool send(const uint8_t* data, size_t size) = 0;
    virtual bool authorized() const = 0; // adapter must authenticate physical peers
};
struct BluetoothAudio {
    virtual ~BluetoothAudio() = default;
    virtual Link link_state() const = 0;
    virtual Activity audio_state() const = 0;
    virtual bool pair() = 0;
    virtual bool wireless(bool enabled) = 0;
    virtual bool recover_wireless() = 0;
    struct Status {
        bool available=false, responsive=true, connecting=false, call=false;
        uint8_t peers=0, active_peer=255, max_peers=0;
    };
    virtual Status radio_status() const { return {}; }
};
struct Runtime {
    virtual ~Runtime() = default;
    virtual bool powered() const = 0;
    virtual bool awake() const = 0;
    virtual bool usb_audio_active() const = 0;
    virtual uint32_t boot_reason() const = 0; // 0 unknown, 1 power, 2 software, 3 watchdog
};
struct AncControl { virtual ~AncControl() = default; virtual Anc anc_state() const = 0; virtual bool set_anc(Anc mode) = 0; };
struct Microphones { virtual ~Microphones() = default; virtual Activity microphone_state() const = 0; };
struct Speakers { virtual ~Speakers() = default; virtual Activity speaker_state() const = 0; };
struct Battery { virtual ~Battery() = default; virtual uint8_t battery_percent() const = 0; };
struct Charger { virtual ~Charger() = default; virtual Charging charging_state() const = 0; };
struct AnalogJack { virtual ~AnalogJack() = default; virtual bool jack_inserted() const = 0; };
struct WearSensor { virtual ~WearSensor() = default; virtual Activity wear_state() const = 0; };
struct PersistentSettings {
    virtual ~PersistentSettings() = default;
    virtual bool load(Settings& result) const = 0;
    virtual bool save(const Settings& value) = 0; // atomic all-or-nothing transaction
};
struct Feedback { virtual ~Feedback() = default; virtual void signal(uint16_t code) = 0; };
struct Clock { virtual ~Clock() = default; virtual uint64_t now_ms() const = 0; };
enum class BootRequest : uint8_t { Reboot, Bootloader };
struct BootRecovery {
    virtual ~BootRecovery() = default;
    virtual bool permitted(BootRequest request) const = 0;
    virtual bool request_boot(BootRequest request) = 0;
};
struct Platform {
    ButtonInput& button; TouchSurface& touch;
    ControlTransport& bluetooth_control; ControlTransport& usb_control;
    BluetoothAudio& audio; AncControl& anc; Microphones& microphones; Speakers& speakers;
    Battery& battery; Charger& charger; AnalogJack& jack; WearSensor& wear;
    PersistentSettings& settings; Feedback& feedback; Clock& clock; BootRecovery& boot;
    Runtime* runtime=nullptr; // optional on legacy Phase 1 adapters
};
}
