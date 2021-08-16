package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class AnyParcelable(val value: Parcelable?) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readParcelable<Parcelable>(AnyParcelable::class.java.classLoader))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(value, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AnyParcelable> {
        override fun createFromParcel(parcel: Parcel): AnyParcelable {
            return AnyParcelable(parcel)
        }

        override fun newArray(size: Int): Array<AnyParcelable?> {
            return arrayOfNulls(size)
        }
    }
}