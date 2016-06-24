package de.fhg.iais.roberta.connection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Observable;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.json.JSONObject;

import de.fhg.iais.roberta.util.ORAtokenGenerator;
import lejos.pc.comm.NXTCommException;
import lejos.pc.comm.NXTInfo;

public class NXTUSBBTConnector extends Observable implements Runnable, Connector {

    private String serverIp = "localhost";
    private String serverPort = "1999";
    private final String serverAddress;

    private static Logger log = Logger.getLogger("Connector");

    private NXTCommunicator nxtcomm;
    private ServerCommunicator servcomm;

    private JSONObject brickData = null;
    private State state = State.DISCOVER; // First state when program starts
    private String token = "";
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
        try {
            NXTInfo[] nxts = NXTCommunicator.discover();
            if ( nxts.length > 0 ) {
                this.nxtcomm = new NXTCommunicator(nxts[0], nxts[0].protocol);
                this.nxtcomm.connect();
                boolean isRunningProgram = this.nxtcomm.isProgramRunning();
                log.info("program is running: " + isRunningProgram);
                this.nxtcomm.disconnect();
                return !isRunningProgram;
            } else {
                log.info("No NXT device connected yet");
                return false;
            }
        } catch ( NullPointerException | IOException | NXTCommException e ) {
            log.info(e.getMessage());
            return false;
        }
    }

    private void setupServerCommunicator() {
        this.servcomm = new ServerCommunicator(this.serverAddress);
    }

    @Override
    public void run() {
        log.config("Starting NXT Connector Thread.");
        setupServerCommunicator();
        log.config("Server address " + this.serverAddress);
        while ( true ) {
            switch ( this.state ) {
                case DISCOVER:
                    NXTInfo[] nxts = NXTCommunicator.discover();
                    if ( (nxts.length > 0) ) {
                        try {
                            // TODO let user choose which one to connect?
                            this.nxtcomm = new NXTCommunicator(nxts[0], nxts[0].protocol);
                            this.nxtcomm.connect();
                            if ( !this.nxtcomm.isProgramRunning() ) {
                                this.state = State.WAIT_FOR_CONNECT;
                                notifyConnectionStateChanged(this.state);
                                break;
                            } else {
                                log.info("NXT is running a program");
                            }
                        } catch ( NXTCommException | IOException e ) {
                            log.info("DISCOVER " + e.getMessage());
                        } finally {
                            this.nxtcomm.disconnect();
                            try {
                                Thread.sleep(1000);
                            } catch ( InterruptedException e ) {
                                // ok
                            }
                        }
                    } else {
                        log.info("No NXT device connected");
                        try {
                            Thread.sleep(1000);
                        } catch ( InterruptedException e ) {
                            // ok
                        }
                    }
                    break;
                case WAIT_EXECUTION:
                    this.state = State.WAIT_EXECUTION;
                    notifyConnectionStateChanged(this.state);
                    try {
                        this.nxtcomm.connect();
                        if ( !this.nxtcomm.isProgramRunning() ) {
                            log.info("Program execution finished - enter WAIT_FOR_CMD state again");
                            this.state = State.WAIT_FOR_CMD;
                            notifyConnectionStateChanged(this.state);
                            break;
                        } else {
                            log.info("Program is running - Cable is plugged in (WAIT_EXECUTION)");
                        }
                    } catch ( NXTCommException | IOException e ) {
                        log.info("Program is running - cable is not plugged in (WAIT_EXECUTION)");
                    } finally {
                        this.nxtcomm.disconnect();
                        try {
                            Thread.sleep(1000);
                        } catch ( InterruptedException ee ) {
                            // ok
                        }
                    }
                    break;
                case WAIT_FOR_CONNECT:
                    // GUI initiates changing state to CONNECT
                    try {
                        this.nxtcomm.connect();
                        if ( this.nxtcomm.isProgramRunning() ) {
                            reset(null, false);
                            break;
                        }
                    } catch ( IOException | NXTCommException e ) {
                        log.info("WAIT_FOR_CONNECT " + e.getMessage());
                        reset(null, false);
                    } finally {
                        this.nxtcomm.disconnect();
                        try {
                            Thread.sleep(1000);
                        } catch ( InterruptedException e ) {
                            // ok
                        }
                    }
                    break;
                case CONNECT:
                    this.token = ORAtokenGenerator.generateToken();
                    this.state = State.WAIT_FOR_SERVER;
                    notifyConnectionStateChanged(this.state);
                    try {
                        this.nxtcomm.connect();
                        this.brickData = this.nxtcomm.getDeviceInfo();
                        this.brickData.put(KEY_TOKEN, this.token);
                        this.brickData.put(KEY_CMD, CMD_REGISTER);
                    } catch ( IOException | NXTCommException e ) {
                        log.info("CONNECT " + e.getMessage());
                        reset(State.ERROR_BRICK, false);
                        break;
                    }
                    try {
                        JSONObject serverResponse = this.servcomm.pushRequest(this.brickData);
                        String command = serverResponse.getString("cmd");
                        switch ( command ) {
                            case CMD_REPEAT:
                                try {
                                    log.info("registration successful");
                                    this.nxtcomm.playAscending();
                                } catch ( IOException e ) {
                                    log.info("registration - playing sound sequence failed");
                                }
                                this.state = State.WAIT_FOR_CMD;
                                notifyConnectionStateChanged(this.state);
                                break;
                            case CMD_ABORT:
                                log.info("registration timeout");
                                notifyConnectionStateChanged(State.TOKEN_TIMEOUT);
                                this.state = State.DISCOVER;
                                notifyConnectionStateChanged(this.state);
                                break;
                            default:
                                throw new RuntimeException("Unexpected command " + command + "from server");
                        }
                    } catch ( IOException | RuntimeException e ) {
                        log.info("CONNECT " + e.getMessage());
                        reset(State.ERROR_HTTP, false);
                    } finally {
                        this.nxtcomm.disconnect();
                    }
                    break;
                case WAIT_FOR_CMD:
                    try {
                        this.nxtcomm.connect();
                        this.brickData = this.nxtcomm.getDeviceInfo();
                        this.brickData.put(KEY_TOKEN, this.token);
                        this.brickData.put(KEY_CMD, CMD_PUSH);
                        this.nxtcomm.disconnect();
                    } catch ( IOException | NXTCommException e ) {
                        log.info("WAIT_FOR_CMD " + e.getMessage());
                        reset(State.ERROR_BRICK, true);
                        break;
                    }
                    try {
                        switch ( this.servcomm.pushRequest(this.brickData).getString(KEY_CMD) ) {
                            case CMD_REPEAT:
                                break;
                            case CMD_DOWNLOAD:
                                log.info("Download user program");
                                try {
                                    // TODO since we do not have nxt files on our server, download a lejos ev3 file but do nothing with it.
                                    // Load another file instead, (TestNBCprogram.rxe) from top level directory of the project.
                                    byte[] binaryfile = this.servcomm.downloadProgram(this.brickData);
                                    String filename = this.servcomm.getFilename();
//                                    File file = new File("TestNBCprogram.rxe");
                                    this.nxtcomm.connect();
                                    this.nxtcomm.uploadAndRunFile(binaryfile, filename);
                                    this.nxtcomm.disconnect();
                                    this.state = State.WAIT_EXECUTION;
                                } catch ( IOException | InterruptedException | NXTCommException e ) {
                                    log.info("Download and run failed: " + e.getMessage());
                                    log.info("Do not give up yet - make the next push request");
                                    this.state = State.WAIT_FOR_CMD;
                                }
                                break;
                            case CMD_CONFIGURATION:
                                break;
                            case CMD_UPDATE: // log and go to abort
                                log.info("Firmware updated not necessary and not supported!");
                            case CMD_ABORT: // go to default
                            default:
                                throw new RuntimeException("Unexpected response from server");
                        }
                    } catch ( RuntimeException | IOException e ) {
                        log.info("WAIT_FOR_CMD " + e.getMessage());
                        reset(State.ERROR_HTTP, true);
                    } finally {
                        log.info("Push request finished");
                        this.nxtcomm.disconnect();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Reset whole program to DISCOVER state. Also closes nxtcomm!
     *
     * @param additionalerrormessage message for popup
     */
    private void reset(State additionalerrormessage, boolean playDisconnectSound) {
        if ( playDisconnectSound ) {
            try {
                this.nxtcomm.connect();
                this.nxtcomm.playDescending();
            } catch ( IOException | NXTCommException e ) {
                log.info("reset - Play descending failed");
            }
        }
        if ( (!this.userDisconnect) && (additionalerrormessage != null) ) {
            notifyConnectionStateChanged(additionalerrormessage);
        }
        this.nxtcomm.disconnect();
        this.userDisconnect = false;
        this.state = State.DISCOVER;
        notifyConnectionStateChanged(this.state);
    }

    @Override
    public void connect() {
        this.state = State.CONNECT;
    }

    @Override
    public void disconnect() {
        this.userDisconnect = true;
        this.servcomm.abort(); // will throw exception, reset will be called in catch statement
    }

    @Override
    public void close() {
        disconnect();
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
        String brickname = this.brickData.getString("brickname");
        if ( brickname != null ) {
            return brickname;
        } else {
            return "";
        }
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

}
