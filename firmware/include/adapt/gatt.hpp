#pragma once
#include "adapt/core.hpp"
#include "adapt/gatt_ids.hpp"
namespace adapt::gatt {
enum class Characteristic { DeviceState, ActionEvent, Command, Configuration, Diagnostics, Metadata };
enum class Result { Incomplete, Accepted, Denied, Invalid, WrongCharacteristic };
// One instance per authenticated peer. Caller serializes SDK callbacks.
class Ingress {
public:
    explicit Ingress(Core& core) : core_(core) {}
    Result write(Characteristic characteristic, const uint8_t* fragment, size_t size,
                 bool with_response, hal::ControlTransport& peer, uint64_t now);
    void disconnect() { used_=total_=0; }
private:
    Core& core_;
    std::array<uint8_t,ACP_MAX_FRAME> frame_{};
    size_t used_=0, total_=0;
    uint16_t id_=0;
    uint64_t last_=0;
    Characteristic destination_=Characteristic::Command;
};
}
