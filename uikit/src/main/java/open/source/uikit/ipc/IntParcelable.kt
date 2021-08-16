package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class IntParcelable(val value: Int) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readInt()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<IntParcelable> {
        override fun createFromParcel(parcel: Parcel): IntParcelable {
            return IntParcelable(parcel)
        }

        override fun newArray(size: Int): Array<IntParcelable?> {
            return arrayOfNulls(size)
        }
    }
}