#pragma once
#include "adapt/model.hpp"

namespace adapt::config {
constexpr uint16_t schema = 2;
constexpr size_t record_size = 46;
using Record = std::array<uint8_t,record_size>;
// No native struct serialization. Decoder changes output only after all checks.
bool encode(const Settings& settings, uint32_t generation, Record& out);
bool decode(const uint8_t* data, size_t size, Settings& out, uint32_t& generation);
}
