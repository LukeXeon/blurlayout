package open.source.uikit.ipc

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

class BinderParcelable(val value: IBinder?) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readStrongBinder()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeStrongBinder(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BinderParcelable> {
        override fun createFromParcel(parcel: Parcel): BinderParcelable {
            return BinderParcelable(parcel)
        }

        override fun newArray(size: Int): Array<BinderParcelable?> {
            return arrayOfNulls(size)
        }
    }
}