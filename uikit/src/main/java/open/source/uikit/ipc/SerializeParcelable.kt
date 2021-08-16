package open.source.uikit.ipc

import android.os.Parcel
import android.os.Parcelable
import java.io.*

class SerializeParcelable(val value: Serializable) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.run {
        ObjectInputStream(ByteArrayInputStream(createByteArray()))
            .use { it.readObject() } as Serializable
    })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use {
            it.writeObject(value)
        }
        parcel.writeByteArray(bytes.toByteArray())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SerializeParcelable> {
        override fun createFromParcel(parcel: Parcel): SerializeParcelable {
            return SerializeParcelable(parcel)
        }

        override fun newArray(size: Int): Array<SerializeParcelable?> {
            return arrayOfNulls(size)
        }
    }
}