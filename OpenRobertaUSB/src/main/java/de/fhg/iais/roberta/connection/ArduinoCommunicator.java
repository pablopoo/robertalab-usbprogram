package de.fhg.iais.roberta.connection;

import java.io.IOException;
//import java.lang.ProcessBuilder.Redirect;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;

import de.fhg.iais.roberta.util.Utils;
import jssc.SerialPort;

public class ArduinoCommunicator {
    private final Properties commProperties;
    SerialPort serialPort;
    String connOptions;
    String osKey = "";
    String osKeyPath = "";
    String avrPath; //path for avrdude bin
    String avrConfPath; //path for the .conf file
    String brickName;

    public ArduinoCommunicator(String brickName) {
        this.commProperties = Utils.loadProperties("classpath:OpenRobertaUSB.properties");
        this.brickName = brickName;
    }

    public void setParameters() {
        if ( SystemUtils.IS_OS_WINDOWS ) {
            this.osKey = "WinPath";
            this.osKeyPath = "WinConfPath";
            this.avrPath = this.commProperties.getProperty(this.osKey);
            this.avrConfPath = this.commProperties.getProperty(this.osKeyPath);

        } else if ( SystemUtils.IS_OS_LINUX ) {
            this.osKey = "LinPath";
            this.osKeyPath = "LinConfPath";
            this.avrPath = this.commProperties.getProperty(this.osKey);
            this.avrConfPath = this.commProperties.getProperty(this.osKeyPath);

        } else {
            this.osKey = "OsXPath";
            this.osKeyPath = "MacConfPath";
            this.avrPath = this.commProperties.getProperty(this.osKey);
            this.avrConfPath = this.commProperties.getProperty(this.osKeyPath);

        }
        this.connOptions = " -V -F -p m328p -c arduino -b 115200 ";

    }

    public JSONObject getDeviceInfo() {
        JSONObject deviceInfo = new JSONObject();

        deviceInfo.put("firmwarename", "Arduino");
        deviceInfo.put("robot", "ardu");
        deviceInfo.put("firmwareversion", "1.1.1");
        deviceInfo.put("macaddr", "0.121.99");
        deviceInfo.put("brickname", brickName);
        deviceInfo.put("battery", "90.0");
        deviceInfo.put("menuversion", "1.4.0");

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
        String portPath = "/dev/";
        if ( SystemUtils.IS_OS_WINDOWS ) {
            portPath = "";
        }
        try {
            ProcessBuilder
                procBuilder =
                new ProcessBuilder(this.avrPath,
                    "-v",
                    "-D",
                    "-pm328p",
                    "-carduino",
                    "-Uflash:w:" + filePath + ":i",
                    "-C" + this.avrConfPath,
                    "-P" + portPath + portName);

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
