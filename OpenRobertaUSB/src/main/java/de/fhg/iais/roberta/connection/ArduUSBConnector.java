package de.fhg.iais.roberta.connection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Observable;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.json.JSONObject;

import de.fhg.iais.roberta.util.ORAtokenGenerator;
import jssc.SerialPortException;
import jssc.SerialPortList;

public class ArduUSBConnector extends Observable implements Runnable, Connector {

    private String serverIp = "localhost";
    private String serverPort = "1999";
    private final String serverAddress;
    // private SerialPort serialPort;
    private String[] portNames;

    private static Logger log = Logger.getLogger("Connector");

    private ArduCommunicator arducomm;
    private ServerCommunicator servcomm;

    private JSONObject brickData = null;
    private State state = State.DISCOVER; // First state when program starts
    private String token = "";
    private boolean userDisconnect = false;

    public ArduUSBConnector(ResourceBundle serverProps) {
        if ( serverProps != null ) {
            this.serverIp = serverProps.getString("serverIp");
            this.serverPort = serverProps.getString("serverPort");
        }
        this.serverAddress = this.serverIp + ":" + this.serverPort;
    }

    private void setupServerCommunicator() {
        this.servcomm = new ServerCommunicator(this.serverAddress);
    }

    @Override
    public void run() {
        log.config("Starting Arduino Connector Thread.");
        setupServerCommunicator();
        log.config("Server address " + this.serverAddress);
        while ( true ) {
            switch ( this.state ) {
                case DISCOVER:

                    this.portNames = SerialPortList.getPortNames(); //search arduino in port names instead

                    if ( (this.portNames.length > 0) ) {
                        try {
                            // TODO let user choose which one to connect?
                            this.arducomm = new ArduCommunicator(this.portNames[this.portNames.length - 1]);
                            this.arducomm.connect();
                            this.state = State.WAIT_FOR_CONNECT;
                            notifyConnectionStateChanged(this.state);
                            break;

                        } catch ( SerialPortException e ) {
                            //                            e.printStackTrace();
                        } finally {
                            this.arducomm.disconnect();
                            try {
                                Thread.sleep(1000);
                            } catch ( InterruptedException e ) {
                                // ok
                            }
                        }
                    } else {
                        log.info("No Arduino device connected");
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
                        this.arducomm.connect();
                        if ( this.arducomm.isConnected() ) {
                            log.info("Program execution finished - enter WAIT_FOR_CMD state again");
                            this.state = State.WAIT_FOR_CMD;
                            notifyConnectionStateChanged(this.state);
                            break;
                        } else {
                            log.info("Program is running - Cable is plugged in (WAIT_EXECUTION)");
                        }
                    } catch ( SerialPortException e ) {
                        log.info("Program is running - cable is not plugged in (WAIT_EXECUTION)");
                    } finally {
                        this.arducomm.disconnect();
                        try {
                            Thread.sleep(1000);
                        } catch ( InterruptedException ee ) {
                            // ok
                        }
                    }
                    break;
                case WAIT_FOR_CONNECT:
                    //                    // GUI initiates changing state to CONNECT
                    try {
                        this.arducomm.connect();
                    } catch ( SerialPortException e ) {
                        log.info("WAIT_FOR_CONNECT " + e.getMessage());
                        reset(null, false);
                    } finally {
                        this.arducomm.disconnect();
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
                        this.arducomm.connect();
                        this.brickData = this.arducomm.getDeviceInfo();
                        this.brickData.put(KEY_TOKEN, this.token);
                        this.brickData.put(KEY_CMD, CMD_REGISTER);
                    } catch ( IOException | SerialPortException e ) {
                        log.info("CONNECT " + e.getMessage());
                        break;
                    }
                    try {
                        JSONObject serverResponse = this.servcomm.pushRequest(this.brickData);
                        String command = serverResponse.getString("cmd");
                        switch ( command ) {
                            case CMD_REPEAT:
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
                        this.arducomm.disconnect();
                    }
                    break;
                case WAIT_FOR_CMD:
                    try {
                        this.arducomm.connect();
                        this.brickData = this.arducomm.getDeviceInfo();
                        this.brickData.put(KEY_TOKEN, this.token);
                        this.brickData.put(KEY_CMD, CMD_PUSH);
                        this.arducomm.disconnect();
                    } catch ( IOException | SerialPortException e ) {
                        log.info("WAIT_FOR_CMD " + e.getMessage());
                        this.state = State.WAIT_EXECUTION;
                        //                        reset(State.ERROR_BRICK, true);
                        break;
                    }
                    try {
                        switch ( this.servcomm.pushRequest(this.brickData).getString(KEY_CMD) ) {
                            case CMD_REPEAT:
                                break;
                            case CMD_DOWNLOAD:
                                log.info("Download user program");
                                try {
                                    byte[] binaryfile = this.servcomm.downloadProgram(this.brickData);
                                    String filename = this.servcomm.getFilename();
                                    File temp = File.createTempFile(filename, "");

                                    temp.deleteOnExit();

                                    if ( !temp.exists() ) {
                                        throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");
                                    }

                                    FileOutputStream os = new FileOutputStream(temp);
                                    try {
                                        os.write(binaryfile);
                                    } finally {
                                        os.close();
                                    }

                                    this.arducomm.uploadFile(this.portNames[this.portNames.length - 1], temp.getAbsolutePath());
                                    this.state = State.WAIT_EXECUTION;
                                } catch ( IOException | InterruptedException e ) {
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
                        this.arducomm.disconnect();
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
                this.arducomm.connect();
                //                //                this.arducomm.playDescending();
            } catch ( SerialPortException e ) {
                log.info("reset - Play descending failed");
            }
        }
        if ( (!this.userDisconnect) && (additionalerrormessage != null) ) {
            notifyConnectionStateChanged(additionalerrormessage);
        }
        this.arducomm.disconnect();
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

    /*
    @Override
    public String getBrickName() {
        String brickname = this.brickData.getString("brickname");
        if ( brickname != null ) {
            return brickname;
        } else {
            return "";
        }
    }*/

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

    @Override
    public boolean findRobot() {
        this.portNames = SerialPortList.getPortNames(); //search arduino in port names instead
        if ( (this.portNames.length > 0) ) {
            return true;
        }
        return false;
    }

    @Override
    public String getBrickName() {
        return "Ardu";
    }

}
