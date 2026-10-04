#pragma once
#include "adapt/hal.hpp"
#include <filesystem>
#include <utility>
namespace adapt::host {
// Host-only single-writer store. Does not perform physical NVM operations.
class FileSettings : public hal::PersistentSettings {
public:
    explicit FileSettings(std::filesystem::path path) : path_(std::move(path)) {}
    bool load(Settings& out) const override;
    bool save(const Settings& value) override;
    bool corrupt() const { return corrupt_; }
private:
    std::filesystem::path path_;
    mutable uint32_t generation_=0;
    mutable bool corrupt_=false, future_schema_=false;
};
}
