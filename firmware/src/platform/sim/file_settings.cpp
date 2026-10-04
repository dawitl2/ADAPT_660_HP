#include "adapt/file_settings.hpp"
#include "adapt/config.hpp"
#include <fstream>
#include <cstdio>
#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#else
#include <fcntl.h>
#include <unistd.h>
#endif
namespace adapt::host {
bool FileSettings::load(Settings& out) const {
    corrupt_=future_schema_=false;
    std::ifstream file(path_,std::ios::binary);
    if (!file) { corrupt_=std::filesystem::exists(path_); return false; }
    std::array<uint8_t,config::record_size+1> data{};
    file.read(reinterpret_cast<char*>(data.data()),static_cast<std::streamsize>(data.size()));
    const auto n=static_cast<size_t>(file.gcount());
    future_schema_=n>=6 && data[0]=='A' && data[1]=='C' && data[2]=='F' && data[3]=='G' && acp_read16(data.data()+4)>config::schema;
    corrupt_=!config::decode(data.data(),n,out,generation_); return !corrupt_;
}
bool FileSettings::save(const Settings& value) {
    if (future_schema_) return false; // never overwrite a newer schema silently
    config::Record record{};
    if (!config::encode(value,generation_+1,record)) return false;
    static unsigned serial=0;
    auto temp=path_;
#ifdef _WIN32
    temp+=L".tmp-"+std::to_wstring(GetCurrentProcessId())+L"-"+std::to_wstring(++serial);
    HANDLE file=CreateFileW(temp.c_str(),GENERIC_WRITE,0,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,nullptr);
    if (file==INVALID_HANDLE_VALUE) return false;
    DWORD written=0;
    const bool ok=WriteFile(file,record.data(),static_cast<DWORD>(record.size()),&written,nullptr) &&
        written==record.size() && FlushFileBuffers(file);
    const bool closed=CloseHandle(file)!=0;
    if (!ok || !closed) { DeleteFileW(temp.c_str()); return false; }
    if (!MoveFileExW(temp.c_str(),path_.c_str(),MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH)) {
        DeleteFileW(temp.c_str()); return false;
    }
#else
    temp+=".tmp-"+std::to_string(getpid())+"-"+std::to_string(++serial);
    const int file=open(temp.c_str(),O_WRONLY|O_CREAT|O_EXCL,0600);
    if (file<0) return false;
    const auto written=write(file,record.data(),record.size());
    const bool ok=written==static_cast<ssize_t>(record.size()) && fsync(file)==0;
    const bool closed=close(file)==0;
    if (!ok || !closed) { std::remove(temp.c_str()); return false; }
    if (std::rename(temp.c_str(),path_.c_str())!=0) { std::remove(temp.c_str()); return false; }
    auto parent=path_.parent_path(); if (parent.empty()) parent=".";
    const int directory=open(parent.c_str(),O_RDONLY);
    if (directory>=0) { fsync(directory); close(directory); } // best effort host directory durability
#endif
    ++generation_; corrupt_=false; return true;
}
}
