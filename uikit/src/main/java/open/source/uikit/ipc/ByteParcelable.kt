package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable

class ByteParcelable(val value: Byte) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readByte()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ByteParcelable> {
        override fun createFromParcel(parcel: Parcel): ByteParcelable {
            return ByteParcelable(parcel)
        }

        override fun newArray(size: Int): Array<ByteParcelable?> {
            return arrayOfNulls(size)
        }
    }
}