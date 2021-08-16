package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class ShortParcelable(val value: Short) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readInt().toShort()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(value.toInt())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ShortParcelable> {
        override fun createFromParcel(parcel: Parcel): ShortParcelable {
            return ShortParcelable(parcel)
        }

        override fun newArray(size: Int): Array<ShortParcelable?> {
            return arrayOfNulls(size)
        }
    }
}