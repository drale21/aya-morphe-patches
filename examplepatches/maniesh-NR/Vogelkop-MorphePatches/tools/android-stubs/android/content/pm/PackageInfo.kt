package android.content.pm

class PackageInfo {
    var packageName: String? = null
    var signatures: Array<Signature>? = null
    var signingInfo: SigningInfo? = null

    companion object {
        @JvmField
        val CREATOR: android.os.Parcelable.Creator<PackageInfo> = object : android.os.Parcelable.Creator<PackageInfo> {
            override fun createFromParcel(source: android.os.Parcel): PackageInfo = PackageInfo()
            override fun newArray(size: Int): Array<PackageInfo?> = arrayOfNulls(size)
        }
    }
}

class SigningInfo {
    fun getApkContentsSigners(): Array<Signature>? = null
    fun getSigningCertificateHistory(): Array<Signature>? = null
}

open class PackageManager {
    class NameNotFoundException : Exception()
}
