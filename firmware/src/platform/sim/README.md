# Functional simulation platform

The tested host HAL remains `include/adapt/host.hpp`; simulator process entry is
`simulator/main.cpp`. Phase 1 APIs remain available. Platform-specific durable file
storage lives here, separate from the allocation-free core and pending target.
All radio, sensors, pairing, audio and boot state is synthetic. The TCP bridge only
starts `adapt_sim`; it never selects the pending or a physical backend.
