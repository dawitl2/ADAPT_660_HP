#include "adapt/file_settings.hpp"
#include "adapt/config.hpp"
#include "check.hpp"
#include <chrono>
#include <fstream>
using namespace adapt;
int main() {
    auto root=std::filesystem::temp_directory_path()/std::filesystem::path("adapt-config-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    CHECK(std::filesystem::create_directory(root));
    const auto path=root/"settings.acfg"; host::FileSettings store(path); Settings s;
    CHECK(!store.load(s)); CHECK(!store.corrupt());
    s.mapping[0]=Action::Custom3; CHECK(store.save(s));
    host::FileSettings restart(path); Settings loaded; CHECK(restart.load(loaded)); CHECK(loaded.mapping[0]==Action::Custom3);
    s.confirmation_tones=false; CHECK(restart.save(s)); CHECK(store.load(loaded)); CHECK(!loaded.confirmation_tones);
    const auto old=loaded; s.timing.debounce_ms=0; CHECK(!store.save(s)); CHECK(restart.load(loaded)); CHECK(loaded.mapping==old.mapping);
    { std::ofstream file(path,std::ios::binary|std::ios::trunc); file << "corrupt"; }
    CHECK(!restart.load(loaded)); CHECK(restart.corrupt()); CHECK(loaded.mapping==old.mapping);
    s=Settings{}; CHECK(restart.save(s)); CHECK(store.load(loaded));
    config::Record r; CHECK(config::encode(s,7,r)); acp_write16(r.data()+4,3); acp_write16(r.data()+44,acp_crc16(r.data(),44));
    { std::ofstream file(path,std::ios::binary|std::ios::trunc); file.write(reinterpret_cast<char*>(r.data()),r.size()); }
    CHECK(!restart.load(loaded)); CHECK(!restart.save(s)); // newer schema preserved
    host::FileSettings missing(root/"missing"/"settings.acfg"); CHECK(!missing.save(s));
    // Exact files in this newly created test directory only; no recursive deletion.
    CHECK(std::filesystem::remove(path)); CHECK(std::filesystem::remove(root));
    std::cout << "file config atomic replacement, process reload, corruption and future preservation passed\n";
}
