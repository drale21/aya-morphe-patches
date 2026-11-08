package android.os

interface Parcelable {
    interface Creator<T> {
        fun createFromParcel(source: Parcel): T
        fun newArray(size: Int): Array<T?>
    }
}

abstract class Parcel

object Build {
    object VERSION {
        @JvmField
        val SDK_INT: Int = 0
    }

    object VERSION_CODES {
        const val P: Int = 28
    }
}
