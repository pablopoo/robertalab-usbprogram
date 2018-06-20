package de.fhg.iais.roberta.connection;

import java.io.IOException;
import java.util.ResourceBundle;

import org.json.JSONException;
import org.json.JSONObject;

import de.fhg.iais.roberta.util.ORAtokenGenerator;

/**
 * Intended to be used as Singleton(!). This class handles two connections:</br>
 * robot<->USB program: {@link EV3Communicator}</br>
 * USB program<->Open Roberta server: {@link ServerCommunicator}</br>
 * After setting up an object of this class, you want to run this in a separate thread, because our protocol contains blocking http requests.
 * The state will be changed from the gui in another thread.
 *
 * @author dpyka
 * {@link IConnector}
 */
public class EV3USBConnector extends AbstractConnector {
    private static final String brickIp = "10.0.1.1";

    private final EV3Communicator ev3comm;

    private final String[] fwfiles =
        {
            "runtime",
            "jsonlib",
            "websocketlib",
            "ev3menu"
        };

    /**
     * Instantiate the connector with specific properties from the file or use default options defined in this class.
     * Set up a communicator to the EV3 and to the Open Roberta server.
     *
     * @param serverProps the server properties
     */
    public EV3USBConnector(ResourceBundle serverProps) {
        super(serverProps, "ev3");

        LOG.config("Robot ip " + brickIp);

        this.ev3comm = new EV3Communicator(brickIp);
    }

    @Override
    public boolean findRobot() {
        try {
            if ( this.ev3comm.checkBrickState().equals("false") ) { // false ^= no program is running
                LOG.info("EV3 available");
                return true;
            } else {
                LOG.info("EV3 is executing a program");
                return false;
            }
        } catch ( IOException e ) {
            return false;
        }
    }

    @Override
    public void userPressDisconnectButton() {
        super.userPressDisconnectButton();
        try {
            this.ev3comm.disconnectBrick();
        } catch ( IOException e ) {
            // ok
        }

        notifyConnectionStateChanged(State.DISCOVER);
        this.state = State.DISCOVER;
    }

    @Override
    public void close() {
        super.close();

        this.ev3comm.shutdown();
    }

    @Override
    public String getBrickName() {
        // TODO whats in brickData?
        if ( this.brickData != null) {
            String brickname = this.brickData.getString("brickname");
            if ( brickname != null ) {
                this.brickName = brickname;
            }
        }
        return this.brickName;
    }

    @Override
    public void update() {
        this.state = State.UPDATE;
    }

    @Override
    public void run() {
        LOG.config("Starting EV3 Connector Thread.");
        setupServerCommunicator();
        LOG.config("Server address " + this.serverAddress);
        while ( true ) {
            switch ( this.state ) {
                case DISCOVER:
                    try {
                        if ( this.ev3comm.checkBrickState().equals("true") ) {
                        } else if ( this.ev3comm.checkBrickState().equals("false") ) { // brick available and no program running
                            this.state = State.WAIT_FOR_CONNECT_BUTTON_PRESS;
                        }
                        Thread.sleep(1000L);
                    } catch ( InterruptedException e ) {
                        LOG.info(State.DISCOVER + " " + e.getMessage());
                    } catch ( IOException e ) {
                        // ok
                    }
                    notifyConnectionStateChanged(this.state);
                    break;
                case WAIT_EXECUTION:
                    this.state = State.WAIT_EXECUTION;
                    notifyConnectionStateChanged(this.state);
                    try {
                        if ( this.ev3comm.checkBrickState().equals("true") ) {
                            // program is running
                            this.state = State.WAIT_EXECUTION;
                            //notifyConnectionStateChanged(this.state);
                        } else if ( this.ev3comm.checkBrickState().equals("false") ) {
                            // brick available and no program running
                            LOG.info(State.WAIT_EXECUTION + " EV3 plugged in again, no program running, OK");
                            this.state = State.WAIT_FOR_CMD;
                            notifyConnectionStateChanged(this.state);
                        }
                        Thread.sleep(1000L);
                    } catch ( InterruptedException e ) {
                        LOG.info(State.WAIT_EXECUTION + " " + e.getMessage());
                    } catch ( IOException e ) {
                        // ok
                    }
                    break;
                case WAIT_FOR_CONNECT_BUTTON_PRESS:
                    try {
                        if ( this.ev3comm.checkBrickState().equals("true") ) {
                            this.state = State.DISCOVER;
                            notifyConnectionStateChanged(State.DISCOVER);
                        } else if ( this.ev3comm.checkBrickState().equals("false") ) {
                            // wait for user
                        }
                        Thread.sleep(1000L);
                    } catch ( InterruptedException | IOException e ) {
                        // ok
                    }
                    break;
                case CONNECT_BUTTON_IS_PRESSED:
                    this.token = ORAtokenGenerator.generateToken();
                    this.state = State.WAIT_FOR_SERVER;
                    notifyConnectionStateChanged(State.WAIT_FOR_SERVER);
                    try {
                        this.brickData = this.ev3comm.pushToBrick(CMD_REGISTER);
                        this.brickData.put(KEY_TOKEN, this.token);
                        this.brickData.put(KEY_CMD, CMD_REGISTER);
                    } catch ( IOException brickerror ) {
                        LOG.info(State.CONNECT_BUTTON_IS_PRESSED + " " + brickerror.getMessage());
                        reset(State.ERROR_BRICK);
                        break;
                    }
                    try {
                        if ( this.state == State.DISCOVER ) {
                            LOG.info("User is clicking connect togglebutton too fast!");
                            break;
                        }
                        JSONObject serverResponse = this.servcomm.pushRequest(this.brickData);
                        String command = serverResponse.getString("cmd");
                        if ( command.equals(CMD_REPEAT) ) {

                            try {
                                this.brickData = this.ev3comm.pushToBrick(CMD_REPEAT);
                            } catch ( IOException brickerror ) {
                                LOG.info(State.CONNECT_BUTTON_IS_PRESSED + " " + brickerror.getMessage());
                                reset(State.ERROR_BRICK);
                                break;
                            }
                            this.state = State.WAIT_FOR_CMD;
                            notifyConnectionStateChanged(State.WAIT_FOR_CMD);
                        } else if ( command.equals(CMD_ABORT) ) {
                            reset(State.TOKEN_TIMEOUT);
                        } else {
                            LOG.info(State.CONNECT_BUTTON_IS_PRESSED + " Command " + command + " unknown");
                            reset(null);
                        }
                    } catch ( IOException | JSONException servererror ) {
                        LOG.info(State.CONNECT_BUTTON_IS_PRESSED + " " + servererror.getMessage());
                        reset(State.ERROR_HTTP);
                    }
                    break;
                case WAIT_FOR_CMD:
                    try {
                        this.brickData = this.ev3comm.pushToBrick(CMD_REPEAT);
                        this.brickData.put(KEY_TOKEN, this.token);
                        this.brickData.put(KEY_CMD, CMD_PUSH);
                    } catch ( IOException brickerror ) {
                        LOG.info(State.WAIT_FOR_CMD + " " + brickerror.getMessage());
                        reset(State.ERROR_BRICK);
                        break;
                    }
                    String responseCommandFromServer;
                    try {
                        responseCommandFromServer = this.servcomm.pushRequest(this.brickData).getString(KEY_CMD);
                    } catch ( IOException | JSONException servererror ) {
                        // continue to default block
                        LOG.info(State.WAIT_FOR_CMD + " Server response not ok " + servererror.getMessage());
                        reset(State.ERROR_HTTP);
                        break;
                    }
                    if ( responseCommandFromServer.equals(CMD_REPEAT) ) {
                    } else if ( responseCommandFromServer.equals(CMD_ABORT) ) {

                        try {
                            this.ev3comm.disconnectBrick();
                        } catch ( IOException brickerror ) {
                            LOG.info(State.WAIT_FOR_CMD + " Got " + CMD_ABORT + " and Brick disconnect failed " + brickerror.getMessage());
                        }
                        reset(null);
                    } else if ( responseCommandFromServer.equals(CMD_UPDATE) ) {
                        LOG.info("Execute firmware update");
                        LOG.info(this.brickData.toString());
                        String lejosVersion = "";
                        if ( this.brickData.getString("firmwarename").equals("ev3lejosv1") ) {
                            lejosVersion = "v1/";
                        }
                        try {
                            for ( String fwfile : this.fwfiles ) {
                                byte[] binaryfile = this.servcomm.downloadFirmwareFile(lejosVersion + fwfile);
                                this.ev3comm.uploadFirmwareFile(binaryfile, this.servcomm.getFilename());
                            }
                            this.ev3comm.restartBrick();
                            LOG.info("Firmware update successful. Restarting EV3 now!");
                            reset(null);
                            try {
                                Thread.sleep(3000L);
                            } catch ( InterruptedException e ) {
                                // ok;
                            }
                        } catch ( IOException e ) {
                            LOG.info(State.WAIT_FOR_CMD + " Brick update failed " + e.getMessage());
                            reset(State.ERROR_UPDATE);
                        }
                    } else if ( responseCommandFromServer.equals(CMD_DOWNLOAD) ) {
                        LOG.info("Download user program");
                        try {
                            byte[] binaryfile = this.servcomm.downloadProgram(this.brickData);
                            String filename = this.servcomm.getFilename();
                            this.ev3comm.uploadProgram(binaryfile, filename);
                            this.state = State.WAIT_EXECUTION;
                        } catch ( IOException e ) {
                            // do not give up the brick, try another push request
                            // user has to click on run button again
                            LOG.info(State.WAIT_FOR_CMD + " Downlaod file failed " + e.getMessage());
                            this.state = State.WAIT_FOR_CMD;
                        }
                    } else if ( responseCommandFromServer.equals(CMD_CONFIGURATION) ) {
                        LOG.warning("Command " + responseCommandFromServer + " unused, ignore and continue push!");
                    } else {

                        LOG.warning("Command " + responseCommandFromServer + " unknown");
                        reset(null);

                    }
                    break;
                default:
                    break;
            }
        }
    }
}
