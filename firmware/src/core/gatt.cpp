#include "adapt/gatt.hpp"
#include <cstring>
namespace adapt::gatt {
Result Ingress::write(Characteristic ch, const uint8_t* data, size_t n,
                     bool with_response, hal::ControlTransport& peer, uint64_t now) {
    if (!with_response || !peer.authorized()) { disconnect(); return Result::Denied; }
    if (ch!=Characteristic::Command && ch!=Characteristic::Configuration) {
        disconnect(); return Result::WrongCharacteristic;
    }
    if (!data || n<=4 || n>ACP_MAX_FRAME+4) { disconnect(); return Result::Invalid; }
    const auto id=acp_read16(data); const size_t offset=data[2], total=data[3], payload=n-4;
    if (total<ACP_HEADER_SIZE+2 || total>ACP_MAX_FRAME || offset+payload>total) { disconnect(); return Result::Invalid; }
    if (used_ && (now<last_ || now-last_>1000)) disconnect();
    if (offset==0) { used_=0; total_=total; id_=id; destination_=ch; }
    if (!total_ || offset!=used_ || total!=total_ || id!=id_ || destination_!=ch) { disconnect(); return Result::Invalid; }
    std::memcpy(frame_.data()+used_,data+4,payload); used_+=payload; last_=now;
    if (used_!=total_) return Result::Incomplete;
    acp_message message{};
    if (acp_decode(frame_.data(),total_,&message)!=ACP_OK || message.flags!=0) { disconnect(); return Result::Invalid; }
    const bool config=message.type==ACP_SET_ACTION_MAPPING || message.type==ACP_SET_SETTING || message.type==ACP_GET_SETTING;
    if (config!=(ch==Characteristic::Configuration)) { disconnect(); return Result::WrongCharacteristic; }
    const auto length=total_; disconnect(); core_.receive(frame_.data(),length,peer); return Result::Accepted;
}
}
