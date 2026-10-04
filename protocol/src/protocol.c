#include "adapt_protocol.h"
#include <string.h>
uint16_t acp_read16(const uint8_t* p) { return (uint16_t)(p[0] | ((uint16_t)p[1] << 8)); }
uint32_t acp_read32(const uint8_t* p) { return (uint32_t)acp_read16(p) | ((uint32_t)acp_read16(p+2) << 16); }
uint64_t acp_read64(const uint8_t* p) { return (uint64_t)acp_read32(p) | ((uint64_t)acp_read32(p+4) << 32); }
void acp_write16(uint8_t* p, uint16_t v) { p[0]=(uint8_t)v; p[1]=(uint8_t)(v >> 8); }
void acp_write32(uint8_t* p, uint32_t v) { acp_write16(p,(uint16_t)v); acp_write16(p+2,(uint16_t)(v >> 16)); }
void acp_write64(uint8_t* p, uint64_t v) { acp_write32(p,(uint32_t)v); acp_write32(p+4,(uint32_t)(v >> 32)); }
uint16_t acp_crc16(const uint8_t* p, size_t n) {
    uint16_t crc=0xffff;
    size_t i;
    for (i=0; i<n; ++i) {
        unsigned bit;
        crc ^= (uint16_t)((uint16_t)p[i] << 8);
        for (bit=0; bit<8; ++bit) crc=(uint16_t)((crc & 0x8000) ? (crc << 1) ^ 0x1021 : crc << 1);
    }
    return crc;
}
static acp_status validate(const acp_message* m) {
    uint16_t expected=0;
    if (m->length > ACP_MAX_PAYLOAD) return ACP_BAD_LENGTH;
    if (m->type < ACP_HELLO || m->type > ACP_FIRMWARE_METADATA) return ACP_BAD_TYPE;
    if (m->flags > ACP_EVENT) return ACP_BAD_FLAGS;
    if (m->type == ACP_ACTION_EVENT || m->type == ACP_LOG_EVENT) {
        if (m->flags != ACP_EVENT) return ACP_BAD_FLAGS;
    } else if (m->type == ACP_ERROR || m->type == ACP_PONG) {
        if (m->flags != ACP_RESPONSE) return ACP_BAD_FLAGS;
    } else if (m->flags == ACP_EVENT && m->type != ACP_LIFECYCLE_STATE) return ACP_BAD_FLAGS;
    if (m->type == ACP_PING && m->flags != 0) return ACP_BAD_FLAGS;
    switch(m->type) {
    case ACP_HELLO: expected=2; break;
    case ACP_GET_CAPABILITIES: expected=m->flags ? 4 : 0; break;
    case ACP_GET_DEVICE_STATE: expected=m->flags ? 26 : 0; break;
    case ACP_ACTION_EVENT: expected=11; break;
    case ACP_SET_ACTION_MAPPING: expected=3; break;
    case ACP_SET_SETTING: expected=5; break;
    case ACP_GET_SETTING: expected=m->flags ? 5 : 1; break;
    case ACP_ERROR: expected=3; break;
    case ACP_LOG_EVENT: expected=6; break;
    case ACP_GET_DIAGNOSTIC: expected=m->flags ? 19 : 1; break;
    case ACP_LIFECYCLE_STATE: expected=m->flags ? 5 : 0; break;
    case ACP_FIRMWARE_METADATA: expected=m->flags ? 83 : 0; break;
    case ACP_PING: case ACP_PONG: return ACP_OK;
    default: expected=0; break;
    }
    return m->length == expected ? ACP_OK : ACP_BAD_PAYLOAD;
}
acp_status acp_encode(const acp_message* m, uint8_t* out, size_t cap, size_t* written) {
    acp_status status;
    size_t n;
    if (written) *written=0;
    if (!m || !out || !written) return ACP_BAD_LENGTH;
    status=validate(m);
    if (status != ACP_OK) return status;
    n=ACP_HEADER_SIZE+m->length;
    if (cap < n+2) return ACP_BAD_LENGTH;
    out[0]='A'; out[1]='C'; out[2]=ACP_MAJOR; out[3]=ACP_MINOR;
    out[4]=m->type; out[5]=m->flags;
    acp_write16(out+6,m->sequence); acp_write16(out+8,m->length);
    memcpy(out+ACP_HEADER_SIZE,m->payload,m->length);
    acp_write16(out+n,acp_crc16(out,n)); *written=n+2;
    return ACP_OK;
}
acp_status acp_decode(const uint8_t* frame, size_t size, acp_message* out) {
    acp_message m;
    acp_status status;
    if (!frame || !out || size < ACP_HEADER_SIZE+2 || size > ACP_MAX_FRAME) return ACP_BAD_LENGTH;
    if (frame[0]!='A' || frame[1]!='C') return ACP_BAD_MAGIC;
    if (frame[2]!=ACP_MAJOR || frame[3]!=ACP_MINOR) return ACP_BAD_VERSION;
    memset(&m,0,sizeof(m)); m.type=frame[4]; m.flags=frame[5];
    m.sequence=acp_read16(frame+6); m.length=acp_read16(frame+8);
    if (m.length > ACP_MAX_PAYLOAD || size != ACP_HEADER_SIZE+(size_t)m.length+2) return ACP_BAD_LENGTH;
    status=validate(&m);
    if (status != ACP_OK) return status;
    if (acp_read16(frame+size-2)!=acp_crc16(frame,size-2)) return ACP_BAD_CRC;
    memcpy(m.payload,frame+ACP_HEADER_SIZE,m.length);
    *out=m; // no partial output on failure
    return ACP_OK;
}
const char* acp_type_name(uint8_t t) {
    static const char* names[]={"UNKNOWN","HELLO","GET_CAPABILITIES","GET_DEVICE_STATE",
        "ACTION_EVENT","SET_ACTION_MAPPING","SET_SETTING","GET_SETTING","ENTER_PAIRING",
        "REQUEST_REBOOT","REQUEST_BOOTLOADER","PING","PONG","ERROR","LOG_EVENT",
        "GET_DIAGNOSTIC","LIFECYCLE_STATE","FIRMWARE_METADATA"};
    return t<=ACP_FIRMWARE_METADATA ? names[t] : names[0];
}
