package de.fhg.iais.roberta.connection.arduino;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Bob3Communicator extends AbstractArduinoCommunicator {
    private static final Logger LOG = LoggerFactory.getLogger(Bob3Communicator.class);

    public Bob3Communicator(String brickName) {
        super(brickName);
    }

    public void setType(ArduinoType type) {
    }

    @Override
    public JSONObject getDeviceInfo() {
        JSONObject deviceInfo = new JSONObject();

        deviceInfo.put("firmwarename", "bob3");
        deviceInfo.put("robot", "bob3");
        deviceInfo.put("brickname", this.brickName);

        return deviceInfo;
    }

    @Override
    public void uploadFile(String portName, String filePath) {
        setParameters();
        String portPath = "/dev/";
        if ( SystemUtils.IS_OS_WINDOWS ) {
            portPath = "";
        }
        try {
            ProcessBuilder procBuilder = new ProcessBuilder(this.avrPath,
                    "-v",
                    "-D",
                    "-patmega88",
                    "-cavrisp2",
                    "-Uflash:w:" + filePath + ":i",
                    "-C" + this.avrConfPath,
                    "-P" + portPath + portName,
                    "-e");

            Process p = procBuilder.start();
            int ecode = p.waitFor();
            LOG.debug("Exit code {}", ecode);
        } catch ( IOException | InterruptedException e ) {
            LOG.error("Error while uploading to arduino: {}", e.getMessage());
        }
    }
}
