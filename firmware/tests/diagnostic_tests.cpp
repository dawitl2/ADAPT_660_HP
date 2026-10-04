#include "adapt/diagnostics.hpp"
#include "check.hpp"
using namespace adapt;
int main() {
    RingLog log; LogRecord r; CHECK(!log.newest(0,r));
    for (uint32_t i=0;i<100;++i) log.push(1000+i,16,i);
    CHECK(log.size()==32); CHECK(log.newest(0,r)); CHECK(r.serial==99 && r.value==99 && r.time_ms==1099);
    CHECK(log.newest(31,r)); CHECK(r.serial==68); CHECK(!log.newest(32,r));
    std::cout << "diagnostic ring wrap, bounded retention and ordering passed\n";
}
