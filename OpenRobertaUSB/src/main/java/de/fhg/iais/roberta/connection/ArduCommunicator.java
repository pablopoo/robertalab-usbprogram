package de.fhg.iais.roberta.connection;

import java.io.IOException;
//import java.lang.ProcessBuilder.Redirect;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;

import de.fhg.iais.roberta.util.Utils;
import jssc.SerialPort;
import jssc.SerialPortException;

public class ArduCommunicator {
    private final Properties commProperties;
    private final String portName;
    SerialPort serialPort;
    String connOptions;
    String line;
    String osKey = "";
    String avrPath; //path for avrdude bin
    String avrConfPath; //path for the .conf file
    //Boolean uploadInProgress;

    public ArduCommunicator(String portName) {
        this.portName = portName;
        this.commProperties = Utils.loadProperties("classpath:OpenRobertaUSB.properties");
        // this.connOptions = "";
    }

    public void connect() throws SerialPortException {
        this.serialPort = new SerialPort(this.portName);
        if ( this.serialPort.isOpened() == false ) {
            this.serialPort.openPort();// Open serial port
            this.serialPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        }
    }

    public void disconnect() {
        try {
            if ( this.serialPort.isOpened() ) {
                this.serialPort.closePort();
            }
        } catch ( SerialPortException ex ) {
            System.out.println(ex);
        }
    }

    public void setParameters() {

        // String userdir = System.getProperty("user.dir") + File.separator;

        if ( SystemUtils.IS_OS_WINDOWS ) {
            this.osKey = "WinPath";
            this.avrPath = this.commProperties.getProperty(this.osKey);

            //this.avrConfPath = new String("./resources/windows/arduino/avrdude.conf");
            // avrConfPath = new String(userdir + "hardware/tools/avr/etc/avrdude.conf");
            // avrPath= new String(userdir + "hardware/tools/avr/bin/");
            //this.avrPath = new String("./resources/windows/arduino/avrdude.exe");
        } else if ( SystemUtils.IS_OS_LINUX ) {
            this.osKey = "LinPath";
            this.avrPath = this.commProperties.getProperty(this.osKey);

            // this.avrConfPath = new String("hardware/tools/avrdude.conf");
            //this.avrPath = new String("hardware/tools/");
        } else {
            this.osKey = "OsXPath";
            this.avrPath = this.commProperties.getProperty(this.osKey);

            //this.avrConfPath = new String("hardware/tools/avr/etc/avrdude.conf");
            //this.avrPath = new String("hardware/tools/avr/bin/");
        }
        this.connOptions = " -V -F -p m328p -c arduino -b 115200 ";

    }

    public JSONObject getDeviceInfo() throws IOException {
        JSONObject deviceInfo = new JSONObject();

        deviceInfo.put("firmwarename", "Arduino");
        deviceInfo.put("robot", "ardu");
        deviceInfo.put("firmwareversion", "1.1.1");
        deviceInfo.put("macaddr", "0.121.99");
        deviceInfo.put("brickname", "Ardu");
        deviceInfo.put("battery", "90.0");
        deviceInfo.put("menuversion", "1.4.0");

        //deviceInfo.put("firmwareversion", this.nxtCommand.getFirmwareVersion().firmwareVersion);
        //DeviceInfo info = this.nxtCommand.getDeviceInfo();
        //deviceInfo.put("macaddr", info.bluetoothAddress);
        deviceInfo.put("brickname", "Ardu");
        //deviceInfo.put("battery", new DecimalFormat("#.#").format(((float) this.nxtCommand.getBatteryLevel()) / 1000));
        //deviceInfo.put("menuversion", "1.4.0");
        //deviceInfo.put("protocolversion", this.nxtCommand.getFirmwareVersion().protocolVersion);
        //deviceInfo.put("connectionType", (this.protocol==1) ? "USB" : "Bluetooth");
        //deviceInfo.put("localaddress", this.nxtCommand.getLocalAddress());
        //deviceInfo.put("freeflash", info.freeFlash);
        //deviceInfo.put("signalstrength", info.signalStrength);
        return deviceInfo;
    }

    /**
     * @return true if a program is currently running, false otherwise
     * @throws IOException
     * @throws InterruptedException
     */
    /* public boolean isProgramRunning() throws IOException {
        return !Arrays.equals(APROGRAMISRUNNING, this.nxtCommand.getCurrentProgramName().getBytes());
    }*/

    public void uploadFile(String portName, String filePath) throws IOException, InterruptedException {
        setParameters();
        String command;
        command =
            this.avrPath
                + " -C "
                + this.avrConfPath
                + this.connOptions
                + "-P"
                + portName
                + " -Uflash:w:"
                + "C:/dev/OpenRoberta/gitWorkspaces/robertalab-usbprogram/OpenRobertaUSB/"
                + filePath
                + ":i";

        try {
            ProcessBuilder procBuilder = new ProcessBuilder(new String[] {
                this.avrPath,
                "-v",
                "-D",
                "-pm328p",
                "-carduino",
                "-Uflash:w:" + filePath + ":i",
                "-P" + portName

                //                "-C " + this.avrConfPath
            });

//            procBuilder.redirectInput(Redirect.INHERIT);
//            procBuilder.redirectOutput(Redirect.INHERIT);
//            procBuilder.redirectError(Redirect.INHERIT);
            Process p = procBuilder.start();
            int ecode = p.waitFor();
            System.err.println("Exit code " + ecode);

        } catch ( Exception e ) {

            e.printStackTrace();

        }

    }

    public boolean isConnected() {
        return this.serialPort.isOpened();
    }

}
