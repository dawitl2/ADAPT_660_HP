#pragma once
#include "adapt/hal.hpp"
#include "adapt/lifecycle.hpp"
#include "adapt/diagnostics.hpp"
#include "adapt_protocol.h"

namespace adapt {
class Core : public ButtonSink {
public:
    Core(hal::Platform platform, uint32_t capabilities, const char* version);
    void tick();
    void receive(const uint8_t* frame, size_t size, hal::ControlTransport& source);
    DeviceState state() const;
    const Settings& settings() const { return settings_; }
    LifecycleState lifecycle() const { return lifecycle_.state(); }
    const RingLog& diagnostics() const { return diagnostics_; }
    void gesture(Gesture event, uint64_t time_ms) override;
private:
    hal::Platform p_;
    Settings settings_{};
    Button button_;
    Lifecycle lifecycle_{};
    RingLog diagnostics_{};
    LifecycleInput lifecycle_input_{};
    LifecycleState last_lifecycle_=LifecycleState::Booting;
    uint8_t last_peers_=0;
    bool last_running_=true, pair_after_recovery_=false;
    Mode mode_ = Mode::Wireless;
    bool last_jack_ = false;
    uint64_t last_tick_ = 0;
    uint16_t event_sequence_ = 0;
    uint32_t capabilities_;
    std::array<uint8_t,16> version_{};
    bool send(acp_message message, hal::ControlTransport& transport);
    void event(acp_message message);
    void log(uint16_t code, uint32_t value);
    void transition(Mode next);
    void error(uint16_t sequence, uint8_t type, acp_error code, hal::ControlTransport& source);
    bool setting(uint8_t key, uint32_t& value) const;
    void lifecycle_payload(acp_message& message) const;
    bool standard_control(uint8_t operation, uint16_t value, acp_error& result);
    void touch();
};
}
