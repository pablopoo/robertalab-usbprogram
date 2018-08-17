package de.fhg.iais.roberta.connection;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Observable;
import java.util.ResourceBundle;

public abstract class AbstractConnector extends Observable implements IConnector {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractConnector.class);

    protected String serverIp = "localhost";
    protected String serverPort = "1999";
    protected final String serverAddress;

    protected ServerCommunicator servcomm = null;

    protected JSONObject brickData = null;

    protected State state = State.DISCOVER; // First state when program starts
    protected String token = "";
    protected String brickName;
    protected boolean userDisconnect = false;

    protected AbstractConnector(ResourceBundle serverProps, String brickName) {
        if ( serverProps != null ) {
            this.serverIp = serverProps.getString("serverIp");
            this.serverPort = serverProps.getString("serverPort");
        }
        this.serverAddress = this.serverIp + ':' + this.serverPort;
        this.brickName = brickName;
    }

    @Override
    public Boolean call() {
        LOG.info("Starting {} connector thread.", this.brickName);
        setupServerCommunicator();
        LOG.info("Server address {}", this.serverAddress);
        while(!Thread.currentThread().isInterrupted()) {
            try {
//                LOG.debug("{} is running! ", Thread.currentThread().getName());
                runLoopBody();
            } catch (InterruptedException e) {
                reset(null);
                LOG.info("Stopping {} connector thread.", this.brickName);
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }

    protected abstract void runLoopBody() throws InterruptedException;

    @Override
    public void userPressConnectButton() {
        this.state = State.CONNECT_BUTTON_IS_PRESSED;
    }

    @Override
    public void userPressDisconnectButton() {
        LOG.info("DISCONNECTING by user");
        this.userDisconnect = true;
        this.state = State.DISCOVER;
        notifyConnectionStateChanged(this.state);
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
        LOG.info("Now using custom address {}", customServerAddress);
    }

    @Override
    public void resetToDefaultServerAddress() {
        this.servcomm.updateCustomServerAddress(this.serverAddress);
        LOG.info("Now using default address {}", this.serverAddress);
    }

    private void setupServerCommunicator() {
        this.servcomm = new ServerCommunicator(this.serverAddress);
    }

    /**
     * Reset the USB program to the start state (discover).
     *
     * @param additionalerrormessage Display a popup with error message. If this is null, we do not want to display the tooltip.
     */
    protected void reset(State additionalerrormessage) {
        if ( !this.userDisconnect && (additionalerrormessage != null) ) {
            notifyConnectionStateChanged(additionalerrormessage);
        }
        this.userDisconnect = false;
        this.state = State.DISCOVER;
        notifyConnectionStateChanged(this.state);
    }
}
