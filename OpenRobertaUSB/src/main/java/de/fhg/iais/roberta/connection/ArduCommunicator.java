package de.fhg.iais.roberta.connection;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;

import jssc.SerialPort;
import jssc.SerialPortException;

public class ArduCommunicator {

    private final String portName;
    SerialPort serialPort;
    String connOptions;
    String avrPath; //path for avrdude bin
    String avrConfPath; //path for the .conf file
    //Boolean uploadInProgress;

    public ArduCommunicator(String portName) {
        this.portName = portName;
        this.connOptions = "";
    }

    public void connect() {
        this.serialPort = new SerialPort(this.portName);
        if ( this.serialPort.isOpened() == false ) {
            try {
                this.serialPort.openPort();// Open serial port
                this.serialPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            } catch ( SerialPortException ex ) {
                System.out.println(ex);
            }
        }
    }

    public void disconnect() {
        try {
            this.serialPort.closePort();
        } catch ( SerialPortException ex ) {
            System.out.println(ex);
        }
    }

    public void setParameters() {

        String userdir = System.getProperty("user.dir") + File.separator;

        if ( SystemUtils.IS_OS_WINDOWS ) {
            this.avrConfPath = new String("\"C:\\Users\\bagridag\\Downloads\\OpenRoberta\\Arduino\\hardware\\tools\\avr\\etc\\avrdude.conf\"");
            // avrConfPath = new String(userdir + "hardware/tools/avr/etc/avrdude.conf");
            // avrPath= new String(userdir + "hardware/tools/avr/bin/");
            this.avrPath = new String("\"C:\\Users\\bagridag\\Downloads\\OpenRoberta\\Arduino\\hardware\\tools\\avr\\bin\\avrdude\"");
        } else if ( SystemUtils.IS_OS_LINUX ) {
            this.avrConfPath = new String("hardware/tools/avrdude.conf");
            this.avrPath = new String("hardware/tools/");
        } else {
            this.avrConfPath = new String("hardware/tools/avr/etc/avrdude.conf");
            this.avrPath = new String("hardware/tools/avr/bin/");
        }
        this.connOptions = " -V -F -p m328p -c arduino -b 115200 ";

    }

    public JSONObject getDeviceInfo() throws IOException {
        JSONObject deviceInfo = new JSONObject();

        deviceInfo.put("firmwarename", "Arduino");

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

    public void uploadFile(String portName, File FileName) throws IOException, InterruptedException {
        setParameters();
        String command;
        command = this.avrPath + " -C " + this.avrConfPath + this.connOptions + " -Uflash:w:" + FileName + ":i";
        Process proc = Runtime.getRuntime().exec(command);
        proc.waitFor();

    }

}
