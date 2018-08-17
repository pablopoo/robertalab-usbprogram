package de.fhg.iais.roberta.connection.arduino;

import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotnrollUSBConnector extends ArduinoUSBConnector {


    public BotnrollUSBConnector(ResourceBundle serverProps) {
        super(serverProps, "Bot'n Roll", Arrays.asList(
            new UsbDevice("10C4", "EA60")
        ));
    }

    @Override
    protected boolean findArduinoMac() {
        try {
            File file = new File("/dev/");
            String[] directories = file.list();
            for ( String directory : directories ) {
                if ( directory.startsWith("cu.SLAB_USBtoUART") ) {
                    return true;
                }
            }
            return false;
        } catch ( Exception e ) {
            return false;
        }
    }

    @Override
    protected void getPortName() throws Exception {
        if ( SystemUtils.IS_OS_MAC_OSX ) {
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec("ls /dev/");

            BufferedReader reader = new BufferedReader(new InputStreamReader(pr.getInputStream()));

            String line;
            while ( (line = reader.readLine()) != null ) {
                Matcher m = Pattern.compile("(cu.SLAB_USBtoUART)").matcher(line);
                if ( m.find() ) {
                    this.portName = line;
                    //  System.out.print(this.portName + "\n");
                }
            }
        }
    }
}
