package de.fhg.iais.roberta.connection.arduino;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ArduinoCommunicator extends AbstractArduinoCommunicator {
    private static final Logger LOG = LoggerFactory.getLogger(ArduinoCommunicator.class);

    private ArduinoType type = ArduinoType.UNO;

    public ArduinoCommunicator(String brickName) {
        super(brickName);
    }

    public void setType(ArduinoType type) {
        this.type = type;
    }

    public JSONObject getDeviceInfo() {
        JSONObject deviceInfo = new JSONObject();

        deviceInfo.put("firmwarename", "Arduino");
        deviceInfo.put("robot", "ardu");
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
            if ( (this.type == ArduinoType.UNO) || (this.type == ArduinoType.NANO) ) {
                pArg = "-patmega328p";
                cArg = "-carduino";
            } else if (this.type == ArduinoType.MEGA) {
                pArg = "-patmega2560";
                cArg = "-cwiring";
            }

            ProcessBuilder procBuilder = new ProcessBuilder(this.avrPath,
                    "-v",
                    "-D",
                    pArg,
                    cArg,
                    "-Uflash:w:" + filePath + ":i",
                    "-C" + this.avrConfPath,
                    "-P" + portPath + portName);

            procBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            procBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            procBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process p = procBuilder.start();
            int ecode = p.waitFor();
            LOG.error("Exit code {}", ecode);
        } catch ( IOException | InterruptedException e ) {
            LOG.error("Error while uploading to arduino: {}", e.getMessage());
        }
    }
}
