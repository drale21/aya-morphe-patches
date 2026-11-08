// Compile-time-only stub of the android framework types used by
// SignatureHookApp. Never dexed into the extension: D8 only receives the
// extension classes, and the references resolve against the real framework
// on the device. Signatures must mirror the real android API exactly.

package android.app

open class Application
