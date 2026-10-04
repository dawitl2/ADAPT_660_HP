#pragma once
#include <cstdint>
namespace adapt {
enum class LifecycleState : uint8_t {
    Off, Booting, Pairable, Connecting, Connected, AudioActive, CallActive,
    AnalogMode, UsbMode, Recovery, Error
};
struct LifecycleInput {
    bool powered=true, awake=true, analog=false, usb_audio=false;
    bool pairable=false, connecting=false, audio=false, call=false;
    bool radio_available=true, responsive=true;
    uint8_t peers=0, active_peer=255, max_peers=1;
};
struct RecoveryPolicy {
    uint32_t watchdog_ms=2000, connecting_ms=5000, retry_ms=500;
    uint8_t max_attempts=3;
};
// Monotonic, nonblocking policy; only the HAL executes a wireless restart.
class Lifecycle {
public:
    explicit Lifecycle(RecoveryPolicy policy={}) : policy_(policy) {}
    bool tick(const LifecycleInput& input, uint64_t now); // restart requested
    void request_recovery(uint64_t now);
    void restarted(bool success, uint64_t now);
    LifecycleState state() const { return state_; }
    uint8_t attempts() const { return attempts_; }
private:
    RecoveryPolicy policy_;
    LifecycleState state_=LifecycleState::Booting;
    bool running_=true, first_=true, unhealthy_=false, connecting_=false;
    bool pending_=false, failed_=false;
    uint8_t attempts_=0;
    uint64_t last_=0, unhealthy_since_=0, connecting_since_=0, next_retry_=0;
};
}
