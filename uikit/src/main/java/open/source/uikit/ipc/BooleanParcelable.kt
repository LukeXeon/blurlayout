package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class BooleanParcelable(val value: Boolean) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readByte() != 0.toByte()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (value) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BooleanParcelable> {
        override fun createFromParcel(parcel: Parcel): BooleanParcelable {
            return BooleanParcelable(parcel)
        }

        override fun newArray(size: Int): Array<BooleanParcelable?> {
            return arrayOfNulls(size)
        }
    }
}