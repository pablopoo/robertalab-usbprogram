package de.fhg.iais.roberta.connection;

import java.io.IOException;
import java.util.Observable;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.json.JSONObject;

import de.fhg.iais.roberta.util.ORAtokenGenerator;
import lejos.pc.comm.NXTInfo;

public class NXTUSBBTConnector extends Observable implements Runnable, Connector {

    private String serverIp = "localhost";
    private String serverPort = "1999";
    private final String serverAddress;

    private static Logger log = Logger.getLogger("Connector");

    private NXTCommunicator nxtcomm;
    private ServerCommunicator servcomm;

    private State state = State.DISCOVER; // First state when program starts
    private String token = "";
    private String brickName = "";
    private String macAddr = "";
    private boolean userDisconnect = false;

    public NXTUSBBTConnector(ResourceBundle serverProps) {
        if ( serverProps != null ) {
            this.serverIp = serverProps.getString("serverIp");
            this.serverPort = serverProps.getString("serverPort");
        }
        this.serverAddress = this.serverIp + ":" + this.serverPort;
    }

    @Override
    public boolean findRobot() {
        DiscoverNXT discoverNXT = new DiscoverNXT();
        return discoverNXT.discover();

    }

    @Override
    public void run() {
        log.config("Starting NXT Connector Thread.");
        this.servcomm = new ServerCommunicator(this.serverAddress);
        log.config("Server address " + this.serverAddress);
        NXTState nxtState;
        while ( true ) {
            switch ( this.state ) {
                case DISCOVER:
                    DiscoverNXT discoverNXT = new DiscoverNXT();
                    if ( discoverNXT.discover() ) {
                        this.nxtcomm = discoverNXT.createCommunicator();
                        JSONObject deviceInfo = this.nxtcomm.getDeviceInfo();
                        if ( !this.token.equals("") && !this.macAddr.equals("") && !this.brickName.equals("") ) {
                            if ( this.macAddr.equals(deviceInfo.optString("macaddr", "")) && this.brickName.equals(deviceInfo.optString("brickname", "")) ) {
                                this.nxtcomm.playSound("connect");
                                this.state = State.WAIT_FOR_CMD;
                                notifyConnectionStateChanged(State.RECONNECT);
                            }
                        } else {
                            this.state = State.WAIT_FOR_CONNECT_BUTTON_PRESS;
                            notifyConnectionStateChanged(this.state);
                        }
                    } else {
                        log.info("No NXT device connected");
                        sleepUntilNextStep(1000);
                    }
                    break;
                case WAIT_FOR_CONNECT_BUTTON_PRESS:
                    // GUI initiates changing state to CONNECT
                    nxtState = this.nxtcomm.getNXTstate();
                    if ( nxtState == NXTState.PROGRAM_RUNNING || nxtState == NXTState.DISCONNECTED ) {
                        reset(null);
                        log.info("RESET CONNECTION BECAUSE " + nxtState);
                        break;
                    }
                    sleepUntilNextStep(1000);
                    break;
                case CONNECT_BUTTON_IS_PRESSED:
                    this.token = ORAtokenGenerator.generateToken();
                    this.state = State.WAIT_FOR_SERVER;
                    notifyConnectionStateChanged(this.state);
                    JSONObject deviceInfo = this.nxtcomm.getDeviceInfo();
                    if ( deviceInfo == null ) {
                        reset(State.ERROR_BRICK);
                        break;
                    }
                    deviceInfo.put(KEY_TOKEN, this.token);
                    deviceInfo.put(KEY_CMD, CMD_REGISTER);
                    try {
                        //Blocks until the server returns command in its response
                        JSONObject serverResponse = this.servcomm.pushRequest(deviceInfo);
                        String command = serverResponse.getString("cmd");
                        if ( command.equals(CMD_REPEAT) ) {

                            log.info("registration successful");
                            this.brickName = deviceInfo.getString("brickname");
                            this.macAddr = deviceInfo.getString("macaddr");
                            this.nxtcomm.playSound("connect");
                            this.state = State.WAIT_FOR_CMD;
                            notifyConnectionStateChanged(this.state);
                        } else if ( command.equals(CMD_ABORT) ) {
                            log.info("registration timeout");
                            notifyConnectionStateChanged(State.TOKEN_TIMEOUT);
                            this.state = State.DISCOVER;
                            notifyConnectionStateChanged(this.state);
                        } else {
                            throw new RuntimeException("Unexpected command " + command + "from server");
                        }
                    } catch ( IOException e ) {
                        log.info("SERVER COMMUNICATION ERROR " + e.getMessage());
                        reset(State.ERROR_HTTP);
                        resetLastConnectionData();
                    } catch ( RuntimeException e ) {
                        log.info("SERVER COMMUNICATION ERROR " + e.getMessage());
                        reset(State.ERROR_HTTP);
                        resetLastConnectionData();
                    }
                    break;
                case WAIT_FOR_CMD:
                    JSONObject deviceInfoWaitCMD = this.nxtcomm.getDeviceInfo();
                    if ( deviceInfoWaitCMD == null ) {
                        reset(State.ERROR_BRICK);
                        break;
                    }
                    deviceInfoWaitCMD.put(KEY_TOKEN, this.token);
                    deviceInfoWaitCMD.put(KEY_CMD, CMD_PUSH);
                    nxtState = this.nxtcomm.getNXTstate();
                    try {
                        JSONObject pushRequestResponse = this.servcomm.pushRequest(deviceInfoWaitCMD);
                        if ( pushRequestResponse == null ) {

                        }
                        String serverCommand = pushRequestResponse.getString(KEY_CMD);
                        if ( serverCommand.equals(CMD_REPEAT) ) {
                        } else if ( serverCommand.equals(CMD_DOWNLOAD) ) {
                            log.info("Download user program");
                            try {
                                byte[] binaryfile = this.servcomm.downloadProgram(deviceInfoWaitCMD);
                                String filename = this.servcomm.getFilename();
                                boolean success = uploadProgram(binaryfile, filename);
                                if ( success ) {
                                    this.state = State.WAIT_EXECUTION;
                                    this.nxtcomm.playSound("download");
                                } else {
                                    reset(State.ERROR_DOWNLOAD);
                                }
                            } catch ( IOException e ) {
                                log.info("Do not give up yet - make the next push request");
                                reset(State.ERROR_DOWNLOAD);
                            }
                        } else {
                            throw new RuntimeException("Unexpected response from server");
                        }
                    } catch ( RuntimeException e ) {
                        log.info("WAIT_FOR_CMD " + e.getMessage());
                        resetLastConnectionData();
                        reset(State.ERROR_HTTP);
                    } catch ( IOException e ) {
                        log.info("WAIT_FOR_CMD " + e.getMessage());
                        resetLastConnectionData();
                        reset(State.ERROR_HTTP);
                    }
                    break;
                case WAIT_EXECUTION:
                    this.state = State.WAIT_EXECUTION;
                    notifyConnectionStateChanged(this.state);

                    NXTState robotState = this.nxtcomm.getNXTstate();
                    if ( robotState == NXTState.WAITING_FOR_PROGRAM ) {
                        log.info("Program execution finished - enter WAIT_FOR_CMD state again");
                        this.state = State.WAIT_FOR_CMD;
                        notifyConnectionStateChanged(this.state);
                        break;
                    } else {
                        log.info("Robot does not wait for a program because " + robotState);
                    }
                    sleepUntilNextStep(1000);
                    break;

                default:
                    break;
            }
        }
    }

    private boolean uploadProgram(byte[] binaryfile, String filename) {
        try {
            this.nxtcomm.uploadFile(binaryfile, filename);
            return true;
        } catch ( Exception e ) {
            log.info("Download failed: " + e.getMessage());
            return false;
        }
    }

    private void sleepUntilNextStep(int time) {
        try {
            Thread.sleep(time);
        } catch ( InterruptedException e ) {
            // ok
        }
    }

    /**
     * Reset whole program to DISCOVER state. Also closes nxtcomm!
     *
     * @param additionalerrormessage message for popup
     */
    private void reset(State additionalerrormessage) {
        this.nxtcomm.playSound("disconnect");
        if ( (!this.userDisconnect) && (additionalerrormessage != null) ) {
            notifyConnectionStateChanged(additionalerrormessage);
        }
        this.userDisconnect = false;
        this.state = State.DISCOVER;
        notifyConnectionStateChanged(this.state);
    }

    @Override
    public void userPressConnectButton() {
        this.state = State.CONNECT_BUTTON_IS_PRESSED;
    }

    @Override
    public void userPressDisconnectButton() {
        this.userDisconnect = true;
        resetLastConnectionData();
        this.servcomm.abort(); // will throw exception, reset will be called in catch statement
    }

    private void resetLastConnectionData() {
        this.token = "";
        this.macAddr = "";
        this.brickName = "";
    }

    @Override
    public void close() {
        userPressDisconnectButton();
        this.servcomm.shutdown();
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
        // no firmware update intended for NXT
    }

    @Override
    public void updateCustomServerAddress(String customServerAddress) {
        this.servcomm.updateCustomServerAddress(customServerAddress);
        log.info("Now using custom address " + customServerAddress);
    }

    @Override
    public void resetToDefaultServerAddress() {
        this.servcomm.updateCustomServerAddress(this.serverAddress);
        log.info("Now using default address " + this.serverAddress);
    }

    private static class DiscoverNXT {
        private NXTInfo nxtInfo;

        public boolean discover() {
            NXTInfo[] nxts = NXTCommunicator.discover();
            if ( (nxts.length > 0) ) {
                this.nxtInfo = nxts[0];
                return true;
            }
            return false;
        }

        public NXTCommunicator createCommunicator() {
            return new NXTCommunicator(this.nxtInfo, this.nxtInfo.protocol);
        }
    }
}
