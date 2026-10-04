#pragma once
#include "adapt/button.hpp"
#include <array>

namespace adapt {
enum class Anc : uint8_t { Unknown, Off, On, Adaptive };
enum class Link : uint8_t { Unknown, Disconnected, Connected, Pairing };
enum class Activity : uint8_t { Unknown, Inactive, Active };
enum class Charging : uint8_t { Unknown, No, Yes };
enum class Mode : uint8_t { Wireless, Analog, ToAnalog, ToWireless, WirelessRecovery };
enum class Action : uint16_t {
    VoiceAssistant = 1, VoiceNote, StudyCompanion, PhoneAction, PcAction,
    CombinedAction, Custom1 = 16, Custom2, Custom3, Custom4, Custom5, Custom6, Custom7, Custom8
};
inline bool valid_action(uint16_t id) { return (id >= 1 && id <= 6) || (id >= 16 && id <= 23); }
struct Settings {
    ButtonTiming timing{};
    std::array<Action, 3> mapping{Action::VoiceAssistant, Action::VoiceNote, Action::StudyCompanion};
    Anc anc = Anc::On;
    bool valid() const {
        if (!timing.valid() || anc < Anc::Off || anc > Anc::Adaptive) return false;
        for (const auto action : mapping) if (!valid_action(static_cast<uint16_t>(action))) return false;
        return true;
    }
};
struct DeviceState {
    uint8_t battery = 255; // 255 = unavailable, never fabricate zero
    Charging charging = Charging::Unknown;
    Anc anc = Anc::Unknown;
    Link link = Link::Unknown;
    Activity audio = Activity::Unknown, microphone = Activity::Unknown, wear = Activity::Unknown;
    Mode mode = Mode::Wireless;
};
}
