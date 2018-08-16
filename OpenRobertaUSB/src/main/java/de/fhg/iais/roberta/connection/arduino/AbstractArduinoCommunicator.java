package de.fhg.iais.roberta.connection.arduino;

import de.fhg.iais.roberta.util.Utils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;

import java.util.Properties;

public abstract class AbstractArduinoCommunicator {

    private final Properties commProperties;
    protected String avrPath = ""; //path for avrdude bin
    protected String avrConfPath = ""; //path for the .conf file
    protected final String brickName;

    public AbstractArduinoCommunicator(String brickName) {
        this.commProperties = Utils.loadProperties("classpath:OpenRobertaUSB.properties");
        this.brickName = brickName;
    }

    public abstract void setType(ArduinoType type);

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

    public abstract JSONObject getDeviceInfo();

    public abstract void uploadFile(String portName, String filePath);
}
