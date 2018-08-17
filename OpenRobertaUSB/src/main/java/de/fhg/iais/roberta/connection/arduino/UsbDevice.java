package de.fhg.iais.roberta.connection.arduino;

import java.util.Objects;

class UsbDevice {
    final String vendorId;
    final String productId;

    UsbDevice(String vendorId, String productId) {
        this.vendorId = vendorId;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof UsbDevice)) {
            return false;
        }
        UsbDevice usbDevice = (UsbDevice) obj;
        return this.vendorId.equalsIgnoreCase(usbDevice.vendorId)
            && this.productId.equalsIgnoreCase(usbDevice.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.vendorId, this.productId);
    }
}
