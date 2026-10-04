#pragma once
#include <array>
#include <cstddef>
#include <cstdint>
namespace adapt {
struct LogRecord { uint64_t time_ms=0; uint32_t serial=0; uint16_t code=0; uint32_t value=0; };
class RingLog {
public:
    static constexpr size_t capacity=32;
    void push(uint64_t time, uint16_t code, uint32_t value) {
        records_[next_]={time,serial_++,code,value}; next_=(next_+1)%capacity;
        if (count_<capacity) ++count_;
    }
    size_t size() const { return count_; }
    bool newest(size_t index, LogRecord& out) const {
        if (index>=count_) return false;
        out=records_[(next_+capacity-1-index)%capacity]; return true;
    }
private:
    std::array<LogRecord,capacity> records_{};
    size_t next_=0, count_=0;
    uint32_t serial_=0;
};
}
