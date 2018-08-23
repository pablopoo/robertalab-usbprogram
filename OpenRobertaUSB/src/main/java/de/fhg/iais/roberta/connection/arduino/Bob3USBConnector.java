package de.fhg.iais.roberta.connection.arduino;

import java.util.Arrays;
import java.util.ResourceBundle;

public class Bob3USBConnector extends ArduinoUSBConnector {

    public Bob3USBConnector(ResourceBundle serverProps) {
        super(serverProps, "Bob3", Arrays.asList(
                new UsbDevice("16C0", "0933")
        ));
    }

    @Override
    protected AbstractArduinoCommunicator createArduinoCommunicator() {
        return new Bob3Communicator(this.brickName);
    }
}
