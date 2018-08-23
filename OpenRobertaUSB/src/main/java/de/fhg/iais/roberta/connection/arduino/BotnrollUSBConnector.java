package de.fhg.iais.roberta.connection.arduino;

import java.util.Arrays;
import java.util.ResourceBundle;

public class BotnrollUSBConnector extends ArduinoUSBConnector {


    public BotnrollUSBConnector(ResourceBundle serverProps) {
        super(serverProps, "Bot'n Roll", Arrays.asList(
                new UsbDevice("10C4", "EA60")
        ));
    }
}
