#ifndef ADAPT_PROTOCOL_H
#define ADAPT_PROTOCOL_H
#include <stdint.h>
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
#define ACP_MAJOR 0
#define ACP_MINOR 1
#define ACP_MAX_PAYLOAD 128
#define ACP_HEADER_SIZE 10
#define ACP_MAX_FRAME (ACP_HEADER_SIZE + ACP_MAX_PAYLOAD + 2)
#define ACP_RESPONSE 1
#define ACP_EVENT 2
typedef enum {
    ACP_HELLO = 1, ACP_GET_CAPABILITIES, ACP_GET_DEVICE_STATE, ACP_ACTION_EVENT,
    ACP_SET_ACTION_MAPPING, ACP_SET_SETTING, ACP_GET_SETTING, ACP_ENTER_PAIRING,
    ACP_REQUEST_REBOOT, ACP_REQUEST_BOOTLOADER, ACP_PING, ACP_PONG, ACP_ERROR, ACP_LOG_EVENT
} acp_type;
typedef enum {
    ACP_VOICE_ASSISTANT = 1, ACP_VOICE_NOTE, ACP_STUDY_COMPANION, ACP_PHONE_ACTION,
    ACP_PC_ACTION, ACP_COMBINED_ACTION, ACP_CUSTOM_ACTION_1 = 16,
    ACP_CUSTOM_ACTION_2, ACP_CUSTOM_ACTION_3, ACP_CUSTOM_ACTION_4, ACP_CUSTOM_ACTION_5,
    ACP_CUSTOM_ACTION_6, ACP_CUSTOM_ACTION_7, ACP_CUSTOM_ACTION_8
} acp_action;
typedef enum { ACP_OK, ACP_BAD_LENGTH, ACP_BAD_MAGIC, ACP_BAD_VERSION,
    ACP_BAD_TYPE, ACP_BAD_FLAGS, ACP_BAD_CRC, ACP_BAD_PAYLOAD } acp_status;
typedef enum { ACP_ERR_INVALID = 1, ACP_ERR_DENIED, ACP_ERR_BUSY, ACP_ERR_STORAGE,
    ACP_ERR_UNSUPPORTED, ACP_ERR_HAL } acp_error;
typedef struct {
    uint8_t type, flags;
    uint16_t sequence, length;
    uint8_t payload[ACP_MAX_PAYLOAD];
} acp_message;
uint16_t acp_crc16(const uint8_t* data, size_t size);
acp_status acp_encode(const acp_message* message, uint8_t* output, size_t capacity, size_t* written);
acp_status acp_decode(const uint8_t* frame, size_t size, acp_message* output);
uint16_t acp_read16(const uint8_t* p);
uint32_t acp_read32(const uint8_t* p);
uint64_t acp_read64(const uint8_t* p);
void acp_write16(uint8_t* p, uint16_t value);
void acp_write32(uint8_t* p, uint32_t value);
void acp_write64(uint8_t* p, uint64_t value);
const char* acp_type_name(uint8_t type);
#ifdef __cplusplus
}
#endif
#endif
