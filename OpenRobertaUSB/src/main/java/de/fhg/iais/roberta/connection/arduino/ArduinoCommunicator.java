package de.fhg.iais.roberta.connection.arduino;

import de.fhg.iais.roberta.util.Utils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class ArduinoCommunicator {
    private static final Logger LOG = LoggerFactory.getLogger(ArduinoCommunicator.class);

    private final Properties commProperties;
    private String avrPath = ""; //path for avrdude bin
    private String avrConfPath = ""; //path for the .conf file
    private final String brickName;
    private final ArduinoType type;

    public ArduinoCommunicator(String brickName, ArduinoType type) {
        this.commProperties = Utils.loadProperties("classpath:OpenRobertaUSB.properties");
        this.brickName = brickName;
        this.type = type;
    }

    public void setParameters() {
        if ( SystemUtils.IS_OS_WINDOWS ) {
            this.avrPath = this.commProperties.getProperty("WinPath");
            this.avrConfPath = this.commProperties.getProperty("WinConfPath");

        } else if ( SystemUtils.IS_OS_LINUX ) {
            this.avrPath = this.commProperties.getProperty("LinPath");
            this.avrConfPath = this.commProperties.getProperty("LinConfPath");

        } else {
            this.avrPath = this.commProperties.getProperty("OsXPath");
            this.avrConfPath = this.commProperties.getProperty("MacConfPath");

        }
    }

    public JSONObject getDeviceInfo() {
        JSONObject deviceInfo = new JSONObject();

        deviceInfo.put("firmwarename", this.type.toString());
        deviceInfo.put("robot", this.type.toString());
        deviceInfo.put("brickname", this.brickName);

        return deviceInfo;
    }

    public void uploadFile(String portName, String filePath) {
        setParameters();
        String portPath = "/dev/";
        if ( SystemUtils.IS_OS_WINDOWS ) {
            portPath = "";
        }
        try {
            String pArg = "";
            String cArg = "";
            String eArg = "";
            if ( (this.type == ArduinoType.UNO) || (this.type == ArduinoType.NANO) ) {
                pArg = "-patmega328p";
                cArg = "-carduino";
            } else if (this.type == ArduinoType.MEGA) {
                pArg = "-patmega2560";
                cArg = "-cwiring";
            } else if (this.type == ArduinoType.BOB3) {
                pArg = "-patmega88";
                cArg = "-cavrisp2";
                eArg = "-e";
            }

            LOG.info("Starting to upload program {} to {}{}", filePath, portPath, portName);
            ProcessBuilder procBuilder = new ProcessBuilder(this.avrPath,
                    "-v",
                    "-D",
                    pArg,
                    cArg,
                    "-Uflash:w:" + filePath + ":i",
                    "-C" + this.avrConfPath,
                    "-P" + portPath + portName,
                    eArg);

//            procBuilder.redirectInput(Redirect.INHERIT);
//            procBuilder.redirectOutput(Redirect.INHERIT);
//            procBuilder.redirectError(Redirect.INHERIT);

            Process p = procBuilder.start();
            int ecode = p.waitFor();
            if (ecode == 0) {
                LOG.info("Program uploaded successfully");
            } else {
                LOG.info("Program was unable to be uploaded: {}", ecode);
            }
            LOG.debug("Exit code {}", ecode);
        } catch ( IOException | InterruptedException e ) {
            LOG.error("Error while uploading to arduino: {}", e.getMessage());
        }
    }
}
