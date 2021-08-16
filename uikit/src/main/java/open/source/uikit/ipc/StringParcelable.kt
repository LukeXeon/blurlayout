package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class StringParcelable(val value: String?) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<StringParcelable> {
        override fun createFromParcel(parcel: Parcel): StringParcelable {
            return StringParcelable(parcel)
        }

        override fun newArray(size: Int): Array<StringParcelable?> {
            return arrayOfNulls(size)
        }
    }
}