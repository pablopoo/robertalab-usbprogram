package de.fhg.iais.roberta.connection;

import java.util.Observable;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.json.JSONObject;

public abstract class AbstractConnector extends Observable implements IConnector {
    protected static Logger LOG = Logger.getLogger("Connector");

    protected String serverIp = "localhost";
    protected String serverPort = "1999";
    protected final String serverAddress;

    protected ServerCommunicator servcomm;

    protected JSONObject brickData = null;

    protected State state = State.DISCOVER; // First state when program starts
    protected String token = "";
    protected String brickName = "";
    protected boolean userDisconnect = false;

    public AbstractConnector(ResourceBundle serverProps, String brickName) {
        if ( serverProps != null ) {
            this.serverIp = serverProps.getString("serverIp");
            this.serverPort = serverProps.getString("serverPort");
        }
        this.serverAddress = this.serverIp + ":" + this.serverPort;
        this.brickName = brickName;
    }

    @Override
    public void userPressConnectButton() {
        this.state = State.CONNECT_BUTTON_IS_PRESSED;
    }

    @Override
    public void userPressDisconnectButton() {
        LOG.info("DISCONNECTING by user");
        this.userDisconnect = true;
    }

    @Override
    public void close() {
        userPressDisconnectButton();
    }

    @Override
    public void notifyConnectionStateChanged(State state) {
        setChanged();
        notifyObservers(state);
    }

    @Override
    public String getToken() {
        return this.token;
    }

    @Override
    public String getBrickName() {
        return this.brickName;
    }

    @Override
    public void update() {
        // no firmware update intended for general robots
    }

    @Override
    public void updateCustomServerAddress(String customServerAddress) {
        this.servcomm.updateCustomServerAddress(customServerAddress);
        LOG.info("Now using custom address " + customServerAddress);
    }

    @Override
    public void resetToDefaultServerAddress() {
        this.servcomm.updateCustomServerAddress(this.serverAddress);
        LOG.info("Now using default address " + this.serverAddress);
    }

    protected void setupServerCommunicator() {
        this.servcomm = new ServerCommunicator(this.serverAddress);
    }

    /**
     * Reset whole program to DISCOVER state. Also closes nxtcomm!
     *
     * @param additionalerrormessage message for popup
     */
    /**
     * Reset the USB program to the start state (discover).
     *
     * @param additionalerrormessage Display a popup with error message. If this is null, we do not want to display the tooltip.
     */
    protected void reset(State additionalerrormessage) {
        if ( !this.userDisconnect && additionalerrormessage != null ) {
            notifyConnectionStateChanged(additionalerrormessage);
        }
        this.userDisconnect = false;
        this.state = State.DISCOVER;
        notifyConnectionStateChanged(this.state);
    }
}
