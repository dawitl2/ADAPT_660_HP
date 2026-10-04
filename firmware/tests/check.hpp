#pragma once
#include <cstdlib>
#include <iostream>
#define CHECK(expr) do { if (!(expr)) { std::cerr << __FILE__ << ':' << __LINE__ << ": " #expr "\n"; std::exit(1); } } while (false)
