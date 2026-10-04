#include "backend.hpp"
#include "adapt/core.hpp"
#include "adapt/identity.hpp"
#include <iostream>
int main() {
    adapt::pending::Backend backend;
    adapt::Core core{backend.platform(),0,adapt::identity::version};
    core.tick(); core.tick();
    if (core.state().battery!=255 || core.state().link!=adapt::Link::Unknown ||
        core.lifecycle()!=adapt::LifecycleState::Error ||
        backend.permitted(adapt::hal::BootRequest::Bootloader) || backend.recover_wireless()) return 1;
    std::cout << "target_adapt660_pending: host skeleton only, UNKNOWN hardware, physical writes disabled\n";
}
