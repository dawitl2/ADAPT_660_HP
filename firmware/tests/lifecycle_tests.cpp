#include "adapt/lifecycle.hpp"
#include "check.hpp"
using namespace adapt;
int main() {
    Lifecycle l; LifecycleInput i;
    CHECK(l.state()==LifecycleState::Booting); CHECK(!l.tick(i,0));
    i.pairable=true; l.tick(i,1); CHECK(l.state()==LifecycleState::Pairable);
    i.pairable=false; i.connecting=true; l.tick(i,2); CHECK(l.state()==LifecycleState::Connecting);
    i.connecting=false; i.peers=1; i.active_peer=0; l.tick(i,3); CHECK(l.state()==LifecycleState::Connected);
    i.audio=true; l.tick(i,4); CHECK(l.state()==LifecycleState::AudioActive);
    i.call=true; l.tick(i,5); CHECK(l.state()==LifecycleState::CallActive);
    i.max_peers=2; i.peers=2; i.active_peer=1; l.tick(i,6); CHECK(l.state()==LifecycleState::CallActive);
    i.call=i.audio=false; i.peers=1; i.active_peer=0; l.tick(i,7); CHECK(l.state()==LifecycleState::Connected);
    i.peers=0; i.active_peer=255; l.tick(i,8); CHECK(l.state()==LifecycleState::Connecting);
    i.usb_audio=true; l.tick(i,9); CHECK(l.state()==LifecycleState::UsbMode);
    i.analog=true; l.tick(i,10); CHECK(l.state()==LifecycleState::AnalogMode);
    i.analog=false; l.request_recovery(11); CHECK(l.tick(i,11)); CHECK(l.state()==LifecycleState::Recovery);
    l.restarted(true,11); i.usb_audio=false; i.peers=1; i.active_peer=0;
    l.tick(i,12); CHECK(l.state()==LifecycleState::Connected);
    i.awake=false; l.tick(i,13); CHECK(l.state()==LifecycleState::Off);
    i.awake=true; l.tick(i,14); CHECK(l.state()==LifecycleState::Booting);
    l.tick(i,15); CHECK(l.state()==LifecycleState::Connected);
    i.responsive=false; l.tick(i,16); CHECK(!l.tick(i,2015)); CHECK(l.tick(i,2016));
    l.restarted(false,2016); CHECK(!l.tick(i,2515)); CHECK(l.tick(i,2516));
    l.restarted(false,2516); CHECK(!l.tick(i,3515)); CHECK(l.tick(i,3516));
    l.restarted(false,3516); CHECK(!l.tick(i,10000)); CHECK(l.state()==LifecycleState::Error);
    l.request_recovery(10001); CHECK(l.tick(i,10001)); l.restarted(true,10001);
    i.responsive=true; l.tick(i,10002); CHECK(l.state()==LifecycleState::Connected);
    i.powered=false; l.tick(i,10003); CHECK(l.state()==LifecycleState::Off);
    i.powered=true; l.tick(i,10004); i.connecting=true; i.peers=0; i.active_peer=255;
    l.tick(i,10005); CHECK(!l.tick(i,15004)); CHECK(l.tick(i,15005));
    i.analog=true; CHECK(!l.tick(i,15006)); CHECK(l.state()==LifecycleState::AnalogMode);
    i.analog=false; i.connecting=false; i.radio_available=false;
    l.tick(i,15007); CHECK(l.state()==LifecycleState::Error);
    CHECK(!l.tick(i,1)); CHECK(l.state()==LifecycleState::Error);
    std::cout << "lifecycle, multipoint, sleep/wake, watchdog, bounded retries and analog cancellation passed\n";
}
