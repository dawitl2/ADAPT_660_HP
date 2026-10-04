#include "adapt/lifecycle.hpp"
namespace adapt {
void Lifecycle::request_recovery(uint64_t now) {
    pending_=true; failed_=false; attempts_=0; next_retry_=now;
}
void Lifecycle::restarted(bool success, uint64_t now) {
    if (!pending_) return;
    ++attempts_;
    if (success) {
        pending_=unhealthy_=connecting_=false; state_=LifecycleState::Connecting;
    } else if (attempts_>=policy_.max_attempts) {
        pending_=false; failed_=true; state_=LifecycleState::Error;
    } else next_retry_=now + static_cast<uint64_t>(policy_.retry_ms)*attempts_;
}
bool Lifecycle::tick(const LifecycleInput& i, uint64_t now) {
    if (now<last_) return false;
    last_=now;
    if (!i.powered || !i.awake) {
        state_=LifecycleState::Off; running_=false;
        pending_=failed_=unhealthy_=connecting_=false; attempts_=0; return false;
    }
    if (!running_ || first_) {
        running_=true; first_=false; state_=LifecycleState::Booting; return false;
    }
    if (i.analog) {
        state_=LifecycleState::AnalogMode; pending_=failed_=unhealthy_=connecting_=false;
        attempts_=0; return false;
    }
    if (!i.radio_available || i.peers>i.max_peers ||
        (i.active_peer!=255 && i.active_peer>=i.peers)) {
        state_=LifecycleState::Error; return false;
    }
    if (!i.responsive && !unhealthy_) { unhealthy_=true; unhealthy_since_=now; }
    if (i.responsive) unhealthy_=false;
    if (i.connecting && !connecting_) { connecting_=true; connecting_since_=now; }
    if (!i.connecting) connecting_=false;
    if (!pending_ && !failed_ && ((unhealthy_ && now-unhealthy_since_>=policy_.watchdog_ms) ||
        (connecting_ && now-connecting_since_>=policy_.connecting_ms))) request_recovery(now);
    if (failed_) { state_=LifecycleState::Error; return false; }
    if (pending_) { state_=LifecycleState::Recovery; return now>=next_retry_; }
    if (i.usb_audio) state_=LifecycleState::UsbMode;
    else if (i.call && i.peers) state_=LifecycleState::CallActive;
    else if (i.audio && i.peers) state_=LifecycleState::AudioActive;
    else if (i.peers) state_=LifecycleState::Connected;
    else if (i.connecting) state_=LifecycleState::Connecting;
    else if (i.pairable) state_=LifecycleState::Pairable;
    else state_=LifecycleState::Connecting; // reconnect policy idle, not a fabricated link
    return false;
}
}
