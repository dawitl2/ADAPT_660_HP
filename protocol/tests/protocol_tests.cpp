#include "adapt_protocol.h"
#include "../../firmware/tests/check.hpp"
#include <array>
#include <cstring>
int main() {
    CHECK(acp_crc16(reinterpret_cast<const uint8_t*>("123456789"),9)==0x29b1);
    std::array<uint8_t, ACP_MAX_FRAME> wire{}; size_t size=0;
    acp_message ping{}; ping.type=ACP_PING; ping.sequence=0x1234;
    CHECK(acp_encode(&ping,wire.data(),wire.size(),&size)==ACP_OK);
    CHECK(size==12); CHECK(wire[6]==0x34 && wire[7]==0x12);
    acp_message decoded{}; CHECK(acp_decode(wire.data(),size,&decoded)==ACP_OK);
    CHECK(decoded.type==ping.type && decoded.sequence==ping.sequence);
    for (size_t n=0; n<size; ++n) CHECK(acp_decode(wire.data(),n,&decoded)!=ACP_OK);
    wire[8]=255; wire[9]=255; CHECK(acp_decode(wire.data(),size,&decoded)==ACP_BAD_LENGTH);
    CHECK(acp_encode(&ping,wire.data(),wire.size(),&size)==ACP_OK);
    wire[4]=99; CHECK(acp_decode(wire.data(),size,&decoded)==ACP_BAD_TYPE);
    wire[4]=ACP_PING; wire[3]=9; CHECK(acp_decode(wire.data(),size,&decoded)==ACP_BAD_VERSION);
    wire[3]=ACP_MINOR; wire[5]=3; CHECK(acp_decode(wire.data(),size,&decoded)==ACP_BAD_FLAGS);
    wire[5]=0; wire[6]^=1; CHECK(acp_decode(wire.data(),size,&decoded)==ACP_BAD_CRC);
    ping.length=128; for (unsigned i=0;i<128;++i) ping.payload[i]=static_cast<uint8_t>(i);
    CHECK(acp_encode(&ping,wire.data(),wire.size(),&size)==ACP_OK);
    CHECK(acp_decode(wire.data(),size,&decoded)==ACP_OK);
    CHECK(std::memcmp(ping.payload,decoded.payload,128)==0);
    CHECK(acp_encode(&ping,wire.data(),size-1,&size)==ACP_BAD_LENGTH);
    ping.length=129; CHECK(acp_encode(&ping,wire.data(),wire.size(),&size)==ACP_BAD_LENGTH);
    CHECK(acp_decode(nullptr,0,&decoded)==ACP_BAD_LENGTH);
    std::cout << "protocol CRC, framing and malformed-input cases passed\n";
}
