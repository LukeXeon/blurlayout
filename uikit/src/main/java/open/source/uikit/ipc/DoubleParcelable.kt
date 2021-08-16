package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class DoubleParcelable(val value: Double) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readDouble()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<DoubleParcelable> {
        override fun createFromParcel(parcel: Parcel): DoubleParcelable {
            return DoubleParcelable(parcel)
        }

        override fun newArray(size: Int): Array<DoubleParcelable?> {
            return arrayOfNulls(size)
        }
    }
}