# mpv-stub

Compile-time stand-in for libMPV. JellyVault does not bundle libMPV: the stub's `MpvPlayer` throws
`MpvStubException`, and playback uses ExoPlayer.
