#include "adapt/core.hpp"
#include "adapt/host.hpp"
#include "adapt/file_settings.hpp"
#include "adapt/identity.hpp"
#include "adapt/tones.hpp"
#include <iomanip>
#include <cctype>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
using namespace adapt;
static std::string hex(const uint8_t* data, size_t n) {
    std::ostringstream out; out << std::hex << std::setfill('0');
    for (size_t i=0;i<n;++i) out << std::setw(2) << unsigned(data[i]);
    return out.str();
}
static void drain(host::Transport& transport, const char* name) {
    for (const auto& wire : transport.frames) {
        acp_message m{};
        if (acp_decode(wire.data(),wire.size(),&m)!=ACP_OK) continue;
        std::cout << "{\"transport\":\"" << name << "\",\"type\":\"" << acp_type_name(m.type)
            << "\",\"sequence\":" << m.sequence << ",\"flags\":" << unsigned(m.flags)
            << ",\"payload_hex\":\"" << hex(m.payload,m.length) << "\",\"wire_hex\":\""
            << hex(wire.data(),wire.size()) << '"';
        if (m.type==ACP_ACTION_EVENT) std::cout << ",\"gesture\":" << unsigned(m.payload[0])
            << ",\"action\":" << acp_read16(m.payload+1) << ",\"time_ms\":" << acp_read64(m.payload+3);
        if (m.type==ACP_ERROR) std::cout << ",\"error\":" << acp_read16(m.payload);
        if (m.type==ACP_LOG_EVENT) std::cout << ",\"code\":" << acp_read16(m.payload) << ",\"value\":" << acp_read32(m.payload+2);
        std::cout << "}\n";
    }
    transport.frames.clear();
}
int main(int argc, char** argv) {
    host::Backend host;
    std::unique_ptr<host::FileSettings> config_file;
    if (argc==3 && std::string(argv[1])=="--settings") {
        config_file=std::make_unique<host::FileSettings>(argv[2]); host.durable=config_file.get();
    } else if (argc!=1) { std::cerr << "Usage: adapt_sim [--settings PATH]\n"; return 2; }
    auto core=std::make_unique<Core>(host.platform(),255,identity::version);
    if (config_file && config_file->corrupt()) std::cout << "{\"configuration_fallback\":true}\n";
    uint16_t sequence=0;
    bool bootloader_mode=false;
    auto advance=[&](uint32_t ms) { for (uint32_t i=0;i<ms;++i) { ++host.time; core->tick(); } };
    auto level=[&](bool down) { host.down=down; core->tick(); advance(25); };
    auto press=[&](uint32_t duration) { level(true); advance(duration-25); level(false); };
    auto request=[&](acp_type type, uint8_t key=0, uint32_t value=0) {
        acp_message m{}; m.type=static_cast<uint8_t>(type); m.sequence=sequence++;
        if (type==ACP_SET_SETTING) { m.length=5; m.payload[0]=key; acp_write32(m.payload+1,value); }
        if (type==ACP_SET_ACTION_MAPPING) { m.length=3; m.payload[0]=key; acp_write16(m.payload+1,static_cast<uint16_t>(value)); }
        if (type==ACP_GET_SETTING || type==ACP_GET_DIAGNOSTIC) { m.length=1; m.payload[0]=key; }
        if (type==ACP_HELLO) { m.length=2; m.payload[1]=ACP_MINOR; }
        uint8_t wire[ACP_MAX_FRAME]; size_t n=0;
        if (acp_encode(&m,wire,sizeof(wire),&n)==ACP_OK) core->receive(wire,n,host.usb);
    };
    std::cerr << "ADAPT host simulator (ASSUMED hardware). 'help' for commands; JSON frames on stdout.\n";
    std::string line;
    while (std::getline(std::cin,line)) {
        std::istringstream input(line); std::string cmd, arg; input >> cmd;
        try {
            if (cmd.empty()) continue;
            if (cmd=="quit") break;
            if (cmd=="barrier") {
                uint32_t id=0; std::string extra;
                if (!(input>>id) || (input>>extra)) throw 1;
                std::cout << "{\"barrier\":" << id << "}\n" << std::flush; continue;
            }
            if (bootloader_mode && cmd!="power-on" && cmd!="help") throw 1;
            if (cmd=="help") {
                std::cerr << "short | double | long | verylong | press MS | down | up | tick MS\n"
                    "hello | capabilities | state | ping | pairing | battery 0..100 | anc 1..3\n"
                    "jack in/out | bt connect/disconnect | charging on/off | map GESTURE ACTION\n"
                    "set KEY VALUE | get KEY | rx HEX | allow-boot on/off | reboot | bootloader | power-on\n"
                    "lifecycle | metadata | diagnostic INDEX | sleep | wake | power on/off\n"
                    "radio healthy/stalled | recover ok/fail | connecting on/off | audio on/off | call on/off\n"
                    "peers 0..2 | active 0..1 | usb-audio on/off | transport bt/usb/both on/off\n";
            } else if (cmd=="short") { press(100); advance(401); }
            else if (cmd=="double") { press(100); advance(100); press(100); advance(401); }
            else if (cmd=="long") { press(2000); advance(401); }
            else if (cmd=="verylong") { press(5100); advance(401); }
            else if (cmd=="down") level(true);
            else if (cmd=="up") level(false);
            else if (cmd=="press" || cmd=="tick") {
                uint32_t ms=0; if (!(input >> ms) || ms>60000 || (cmd=="press" && ms<25)) throw 1;
                if (cmd=="press") { press(ms); advance(401); } else advance(ms);
            } else if (cmd=="hello") request(ACP_HELLO);
            else if (cmd=="capabilities") request(ACP_GET_CAPABILITIES);
            else if (cmd=="state") request(ACP_GET_DEVICE_STATE);
            else if (cmd=="lifecycle") request(ACP_LIFECYCLE_STATE);
            else if (cmd=="metadata") request(ACP_FIRMWARE_METADATA);
            else if (cmd=="diagnostic") {
                unsigned index=0; if (!(input>>index) || index>255) throw 1;
                request(ACP_GET_DIAGNOSTIC,static_cast<uint8_t>(index));
            }
            else if (cmd=="sleep" || cmd=="wake") {
                host.wake=cmd=="wake"; core->tick(); advance(2); request(ACP_LIFECYCLE_STATE);
            }
            else if (cmd=="power" || cmd=="audio" || cmd=="call" || cmd=="connecting" || cmd=="usb-audio") {
                input>>arg; if (arg!="on" && arg!="off") throw 1; const bool on=arg=="on";
                if (cmd=="power") host.power=on;
                if (cmd=="audio") host.audio=on ? Activity::Active : Activity::Inactive;
                if (cmd=="call") { host.call=on; host.microphone=on ? Activity::Active : Activity::Inactive; }
                if (cmd=="connecting") host.connecting=on;
                if (cmd=="usb-audio") host.usb_audio=on;
                core->tick(); advance(2); request(ACP_LIFECYCLE_STATE);
            }
            else if (cmd=="radio" || cmd=="recover") {
                input>>arg;
                if (cmd=="radio") { if (arg!="healthy" && arg!="stalled") throw 1; host.responsive=arg=="healthy"; }
                else { if (arg!="ok" && arg!="fail") throw 1; host.recovery_ok=arg=="ok"; }
                core->tick();
            }
            else if (cmd=="peers" || cmd=="active") {
                unsigned n=0; if (!(input>>n) || n>(cmd=="peers" ? 2u : 1u) || !host.wireless_enabled) throw 1;
                if (cmd=="peers") { host.peers=static_cast<uint8_t>(n); host.link=n ? Link::Connected : Link::Disconnected; host.active_peer=n ? 0 : 255; }
                else { if (n>=host.peers) throw 1; host.active_peer=static_cast<uint8_t>(n); }
                core->tick(); request(ACP_LIFECYCLE_STATE);
            }
            else if (cmd=="transport") {
                std::string value; input>>arg>>value;
                if ((arg!="bt" && arg!="usb" && arg!="both") || (value!="on" && value!="off")) throw 1;
                if (arg=="bt" || arg=="both") host.bt.connected=value=="on";
                if (arg=="usb" || arg=="both") host.usb.connected=value=="on";
            }
            else if (cmd=="ping") request(ACP_PING);
            else if (cmd=="pairing") request(ACP_ENTER_PAIRING);
            else if (cmd=="reboot") request(ACP_REQUEST_REBOOT);
            else if (cmd=="bootloader") request(ACP_REQUEST_BOOTLOADER);
            else if (cmd=="power-on") { host.boot_pending=true; host.boot_kind=hal::BootRequest::Reboot; }
            else if (cmd=="allow-boot" || cmd=="charging") {
                input >> arg; if (arg!="on" && arg!="off") throw 1;
                if (cmd=="allow-boot") host.allow_boot=arg=="on";
                else host.charging=arg=="on" ? Charging::Yes : Charging::No;
            } else if (cmd=="battery") {
                unsigned n=0; if (!(input >> n) || n>100) throw 1;
                host.battery=static_cast<uint8_t>(n); request(ACP_GET_DEVICE_STATE);
            } else if (cmd=="anc" || cmd=="set" || cmd=="get" || cmd=="map") {
                unsigned key=6; uint32_t value=0;
                if (cmd=="anc") { if (!(input >> value) || value<1 || value>3) throw 1; }
                else if (!(input >> key) || key>255) throw 1;
                if ((cmd=="set" || cmd=="map") && !(input >> value)) throw 1;
                if (cmd=="map" && value>65535) throw 1;
                request(cmd=="get" ? ACP_GET_SETTING : cmd=="map" ? ACP_SET_ACTION_MAPPING : ACP_SET_SETTING,
                    static_cast<uint8_t>(key),value);
            } else if (cmd=="jack") {
                input >> arg; if (arg!="in" && arg!="out") throw 1;
                host.jack=arg=="in"; core->tick(); advance(3); request(ACP_GET_DEVICE_STATE);
            } else if (cmd=="bt") {
                input >> arg; if (arg!="connect" && arg!="disconnect") throw 1;
                if (!host.wireless_enabled && arg=="connect") throw 1;
                host.link=arg=="connect" ? Link::Connected : Link::Disconnected;
                host.peers=arg=="connect" ? 1 : 0; host.active_peer=arg=="connect" ? 0 : 255;
                core->tick(); advance(1);
                request(ACP_GET_DEVICE_STATE);
            } else if (cmd=="rx") {
                input >> arg; if (arg.size()>ACP_MAX_FRAME*2 || arg.size()%2) throw 1;
                for (const unsigned char c : arg) if (!std::isxdigit(c)) throw 1;
                std::vector<uint8_t> wire;
                for (size_t i=0;i<arg.size();i+=2) {
                    const auto part=arg.substr(i,2); size_t used=0;
                    const auto byte=std::stoul(part,&used,16); if (used!=2 || byte>255) throw 1;
                    wire.push_back(static_cast<uint8_t>(byte));
                }
                core->receive(wire.data(),wire.size(),host.usb);
            } else throw 1;
        } catch (...) { std::cout << "{\"command_error\":\"invalid command or argument\"}\n"; }
        drain(host.bt,"mock-bluetooth"); drain(host.usb,"mock-usb");
        for (const auto code : host.feedback) {
            const auto tone=confirmation_tone(code);
            std::cout << "{\"feedback\":" << code << ",\"tone_ms\":" << tone_duration_ms(tone) << ",\"segments\":[";
            for (size_t i=0;i<tone.count;++i) {
                if (i) std::cout << ',';
                const auto s=tone.segments[i];
                std::cout << "{\"hz\":" << s.hz << ",\"ms\":" << s.duration_ms << ",\"amplitude\":" << s.amplitude << '}';
            }
            std::cout << "]}\n";
        }
        host.feedback.clear();
        if (host.boot_pending) {
            host.boot_pending=false;
            if (host.boot_kind==hal::BootRequest::Bootloader) {
                bootloader_mode=true;
                std::cout << "{\"simulated_bootloader\":true,\"physical_backend\":false}\n";
                // No flash protocol; next power-on leaves simulated bootloader.
            } else {
                bootloader_mode=false;
                host.down=false; host.wireless(true); host.link=Link::Disconnected;
                core=std::make_unique<Core>(host.platform(),255,identity::version);
                std::cout << "{\"simulated_reboot\":true}\n";
            }
        }
        std::cout << std::flush;
    }
}
