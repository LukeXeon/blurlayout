package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class LongParcelable(val value: Long) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readLong()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LongParcelable> {
        override fun createFromParcel(parcel: Parcel): LongParcelable {
            return LongParcelable(parcel)
        }

        override fun newArray(size: Int): Array<LongParcelable?> {
            return arrayOfNulls(size)
        }
    }
}