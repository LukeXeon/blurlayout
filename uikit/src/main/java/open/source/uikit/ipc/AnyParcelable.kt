package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class AnyParcelable(val value: Any?) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readValue(AnyParcelable::class.java.classLoader))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeValue(value)
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