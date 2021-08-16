package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class FloatParcelable(val value: Float) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readFloat()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<FloatParcelable> {
        override fun createFromParcel(parcel: Parcel): FloatParcelable {
            return FloatParcelable(parcel)
        }

        override fun newArray(size: Int): Array<FloatParcelable?> {
            return arrayOfNulls(size)
        }
    }
}