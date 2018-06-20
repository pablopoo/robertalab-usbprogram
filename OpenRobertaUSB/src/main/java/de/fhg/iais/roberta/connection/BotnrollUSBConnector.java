package de.fhg.iais.roberta.connection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;

import de.fhg.iais.roberta.util.JWMI;
import de.fhg.iais.roberta.util.ORAtokenGenerator;

public class BotnrollUSBConnector extends ArduinoUSBConnector {

    public BotnrollUSBConnector(ResourceBundle serverProps) {
        super(serverProps, "botnroll");
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
    protected boolean findArduWindows() {
        try {
            return JWMI.getWMIValue("SELECT * FROM Win32_PnPEntity WHERE Caption LIKE '%(COM%' ", "Caption").contains("Silicon Labs");
        } catch ( Exception e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected boolean findArduinoLinux() {
        try {
            File file = new File("/dev/serial/by-id/");
            String[] directories = file.list();
            for ( String directory : directories ) {
                if ( directory.matches(".*Silicon_Labs_CP2104_USB_to_UART_Bridge_Controller.*") ) {
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
        if ( SystemUtils.IS_OS_WINDOWS ) {
            String ArduQueryResult = JWMI.getWMIValue("SELECT * FROM Win32_PnPEntity WHERE Caption LIKE '%(COM%' ", "Caption");
            Matcher m = Pattern.compile("(Silicon Labs CP210x USB to UART Bridge \\()(.*)\\)").matcher(ArduQueryResult);
            while ( m.find() ) {
                portName = m.group(2);
            }

        } else if ( SystemUtils.IS_OS_LINUX ) {
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec("ls /dev/");

            BufferedReader reader = new BufferedReader(new InputStreamReader(pr.getInputStream()));

            String line;
            while ( (line = reader.readLine()) != null ) {
                Matcher m = Pattern.compile("(ttyUSB)").matcher(line);
                if ( m.find() ) {
                    this.portName = line;
                    //  System.out.print(this.portName + "\n");
                }
            }

        } else if ( SystemUtils.IS_OS_MAC_OSX ) {
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
