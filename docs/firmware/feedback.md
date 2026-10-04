# Original acknowledgement tones

Feedback codes 10/11/12 denote accepted short/double/long gestures. Code13 denotes
local protected pairing/recovery. These are our own sine tones, not EPOS prompts:
660 Hz once, 440/silence/660 Hz, rising 440/660/880 Hz, and a longer 220/silence/880
Hz recovery pattern. `tones.cpp` supplies bounded PCM synthesis at 8–96 kHz with
5 ms onset/release ramps. No sampled/vendor audio asset is used.

Normal confirmation can be disabled in settings; protected recovery indication
remains enabled. An acknowledgement means firmware accepted a gesture/pairing
request, not that the Android AI session has started. ACTION_EVENT carries the
mapping and timestamp. Transport failure still signals existing feedback code4.

The HAL signal schedules playback on the SDK audio task; it must not block button
sampling, preempt a call, bypass volume limits or overflow the audio queue. Host
simulation emits patterns as events and can render sample values for tests. No
real DAC is driven. PCM gain limits are not a confirmed acoustic sound level;
speaker sensitivity/mixer headroom require hardware calibration later.
