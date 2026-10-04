#include "adapt_protocol.h"
#include "../../firmware/tests/check.hpp"
#include <array>
#include <cstring>
#include <random>
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
    // Exercise every defined structured request/response/event wire shape.
    struct Shape { uint8_t type, flags; uint16_t length; };
    const Shape shapes[] = {
        {ACP_HELLO,0,2},{ACP_HELLO,1,2},{ACP_GET_CAPABILITIES,0,0},{ACP_GET_CAPABILITIES,1,4},
        {ACP_GET_DEVICE_STATE,0,0},{ACP_GET_DEVICE_STATE,1,26},{ACP_ACTION_EVENT,2,11},
        {ACP_SET_ACTION_MAPPING,0,3},{ACP_SET_ACTION_MAPPING,1,3},{ACP_SET_SETTING,0,5},
        {ACP_SET_SETTING,1,5},{ACP_GET_SETTING,0,1},{ACP_GET_SETTING,1,5},
        {ACP_ENTER_PAIRING,0,0},{ACP_ENTER_PAIRING,1,0},{ACP_REQUEST_REBOOT,0,0},
        {ACP_REQUEST_REBOOT,1,0},{ACP_REQUEST_BOOTLOADER,0,0},{ACP_REQUEST_BOOTLOADER,1,0},
        {ACP_PING,0,128},{ACP_PONG,1,128},{ACP_ERROR,1,3},{ACP_LOG_EVENT,2,6}
    };
    for (const auto shape : shapes) {
        acp_message input{}; input.type=shape.type; input.flags=shape.flags;
        input.length=shape.length; input.sequence=65535;
        for (unsigned i=0;i<input.length;++i) input.payload[i]=static_cast<uint8_t>(i+1);
        CHECK(acp_encode(&input,wire.data(),wire.size(),&size)==ACP_OK);
        acp_message result{}; CHECK(acp_decode(wire.data(),size,&result)==ACP_OK);
        CHECK(result.type==input.type && result.flags==input.flags && result.sequence==65535 && result.length==input.length);
        CHECK(std::memcmp(result.payload,input.payload,input.length)==0);
        if (shape.type!=ACP_PING && shape.type!=ACP_PONG) {
            ++input.length; CHECK(acp_encode(&input,wire.data(),wire.size(),&size)==ACP_BAD_PAYLOAD);
        }
    }
    // Action timestamp/action ID round-trip independent of native struct layout.
    acp_message action{}; action.type=ACP_ACTION_EVENT; action.flags=ACP_EVENT; action.length=11;
    action.payload[0]=2; acp_write16(action.payload+1,ACP_CUSTOM_ACTION_8);
    acp_write64(action.payload+3,UINT64_C(0xfedcba9876543210));
    CHECK(acp_encode(&action,wire.data(),wire.size(),&size)==ACP_OK);
    CHECK(acp_decode(wire.data(),size,&decoded)==ACP_OK);
    CHECK(acp_read64(decoded.payload+3)==UINT64_C(0xfedcba9876543210));
    CHECK(acp_read16(decoded.payload+1)==ACP_CUSTOM_ACTION_8);
    // Malformed decoder inputs never partially change the caller's output.
    const auto sentinel=decoded; wire[0]='X';
    CHECK(acp_decode(wire.data(),size,&decoded)==ACP_BAD_MAGIC);
    CHECK(std::memcmp(&decoded,&sentinel,sizeof(decoded))==0);
    // Seeded mutation corpus exercises rejection paths at many lengths/types.
    std::mt19937 random(660);
    for (unsigned i=0;i<10000;++i) {
        for (auto& byte : wire) byte=static_cast<uint8_t>(random());
        const auto n=random()%(ACP_MAX_FRAME+1);
        acp_message output=sentinel;
        if (acp_decode(wire.data(),n,&output)!=ACP_OK)
            CHECK(std::memcmp(&output,&sentinel,sizeof(output))==0);
    }
    std::cout << "23 protocol shapes, CRC, action fields and 10000 seeded malformed frames passed\n";
}
