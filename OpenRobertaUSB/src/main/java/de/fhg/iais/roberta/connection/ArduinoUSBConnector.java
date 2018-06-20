package de.fhg.iais.roberta.connection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;

import de.fhg.iais.roberta.util.JWMI;
import de.fhg.iais.roberta.util.ORAtokenGenerator;

public class ArduinoUSBConnector extends AbstractConnector {
    private String portName;

    private ArduinoCommunicator arducomm;

    public ArduinoUSBConnector(ResourceBundle serverProps) {
        super(serverProps, "arduino");
    }

    @Override
    public boolean findRobot() {
        if ( SystemUtils.IS_OS_LINUX ) {
            return findArduinoLinux();
        } else if ( SystemUtils.IS_OS_WINDOWS ) {
            return findArduWindows();
        } else if ( SystemUtils.IS_OS_MAC_OSX ) {
            return findArduinoMac();
        } else {
            return false;
        }
    }

    @Override
    public void run() {
        LOG.config("Starting Arduino Connector Thread.");
        setupServerCommunicator();
        LOG.config("Server address " + this.serverAddress);
        while ( true ) {
            switch ( this.state ) {
                case DISCOVER:
                    try {
                        getPortName();
                    } catch ( Exception e1 ) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }

                    if ( this.portName.length() > 0 ) {
                        // TODO let user choose which one to connect?
                        this.arducomm = new ArduinoCommunicator(brickName);
                        this.state = State.WAIT_FOR_CONNECT_BUTTON_PRESS;
                        notifyConnectionStateChanged(this.state);
                        break;
                    } else {
                        LOG.info("No Arduino device connected");
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

                    this.state = State.WAIT_FOR_CMD;
                    notifyConnectionStateChanged(this.state);

                    try {
                        Thread.sleep(1000);
                    } catch ( InterruptedException ee ) {
                        // ok
                    }

                    break;
                case WAIT_FOR_CONNECT_BUTTON_PRESS:
                    //                    // GUI initiates changing state to CONNECT
                    try {
                        Thread.sleep(1000);
                    } catch ( InterruptedException e ) {
                        // ok
                    }
                    break;
                case CONNECT_BUTTON_IS_PRESSED:
                    this.token = ORAtokenGenerator.generateToken();
                    this.state = State.WAIT_FOR_SERVER;
                    notifyConnectionStateChanged(this.state);
                    this.brickData = this.arducomm.getDeviceInfo();
                    this.brickData.put(KEY_TOKEN, this.token);
                    this.brickData.put(KEY_CMD, CMD_REGISTER);
                    try {
                        JSONObject serverResponse = this.servcomm.pushRequest(this.brickData);
                        String command = serverResponse.getString("cmd");
                        switch ( command ) {
                            case CMD_REPEAT:
                                this.state = State.WAIT_FOR_CMD;
                                notifyConnectionStateChanged(this.state);
                                break;
                            case CMD_ABORT:
                                LOG.info("registration timeout");
                                notifyConnectionStateChanged(State.TOKEN_TIMEOUT);
                                this.state = State.DISCOVER;
                                notifyConnectionStateChanged(this.state);
                                break;
                            default:
                                throw new RuntimeException("Unexpected command " + command + "from server");
                        }
                    } catch ( IOException | RuntimeException io ) {
                        LOG.info("CONNECT " + io.getMessage());
                        reset(State.ERROR_HTTP);
                    }

                case WAIT_FOR_CMD:
                    this.brickData = this.arducomm.getDeviceInfo();
                    this.brickData.put(KEY_TOKEN, this.token);
                    this.brickData.put(KEY_CMD, CMD_PUSH);
                    try {
                        String cmdKey = this.servcomm.pushRequest(this.brickData).getString(KEY_CMD);
                        if ( cmdKey.equals(CMD_REPEAT) ) {
                            break;
                        } else if ( cmdKey.equals(CMD_DOWNLOAD) ) {
                            LOG.info("Download user program");
                            try {
                                byte[] binaryfile = this.servcomm.downloadProgram(this.brickData);
                                String filename = this.servcomm.getFilename();
                                File temp = File.createTempFile(filename, "");

                                temp.deleteOnExit();

                                if ( !temp.exists() ) {
                                    throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");
                                }

                                try (FileOutputStream os = new FileOutputStream(temp)) {
                                    os.write(binaryfile);
                                }

                                this.arducomm.uploadFile(this.portName, temp.getAbsolutePath());
                                this.state = State.WAIT_EXECUTION;
                            } catch ( IOException | InterruptedException io ) {
                                LOG.info("Download and run failed: " + io.getMessage());
                                LOG.info("Do not give up yet - make the next push request");
                                this.state = State.WAIT_FOR_CMD;

                            }
                        } else if ( cmdKey.equals(CMD_CONFIGURATION) ) {
                            LOG.info("Configuration");
                        } else if ( cmdKey.equals(CMD_UPDATE) ) {
                            LOG.info("Firmware updated not necessary and not supported!");// LOG and go to abort
                        } else if ( cmdKey.equals(CMD_ABORT) ) {
                            throw new RuntimeException("Unexpected response from server");
                        }
                    } catch ( RuntimeException | IOException r ) {
                        LOG.info("WAIT_FOR_CMD " + r.getMessage());
                        reset(State.ERROR_HTTP);
                    }
                default:
                    break;
            }
        }
    }

    private boolean findArduinoMac() {
        try {
            File file = new File("/dev/");
            String[] directories = file.list();
            for ( String directory : directories ) {
                if ( directory.startsWith("cu.Arduino") ) {
                    return true;
                }
            }
            return false;
        } catch ( Exception e ) {
            return false;
        }
    }

    private boolean findArduWindows() {
        try {
            return JWMI.getWMIValue("SELECT * FROM Win32_PnPEntity WHERE Caption LIKE '%(COM%' ", "Caption").contains("Arduino");
        } catch ( Exception e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }

    private boolean findArduinoLinux() {
        try {
            File file = new File("/dev/serial/by-id/");
            String[] directories = file.list();
            for ( String directory : directories ) {
                if ( directory.matches(".*Arduino.*") ) {
                    return true;
                }
            }
            return false;
        } catch ( Exception e ) {
            return false;
        }
    }

    private void getPortName() throws Exception {
        if ( SystemUtils.IS_OS_WINDOWS ) {
            String ArduQueryResult = JWMI.getWMIValue("SELECT * FROM Win32_PnPEntity WHERE Caption LIKE '%(COM%' ", "Caption");
            Matcher m = Pattern.compile("(Arduino \\()(.*)\\)").matcher(ArduQueryResult);
            while ( m.find() ) {
                this.portName = m.group(2);
            }

        } else if ( SystemUtils.IS_OS_LINUX ) {
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec("ls /dev/");

            BufferedReader reader = new BufferedReader(new InputStreamReader(pr.getInputStream()));

            String line;
            while ( (line = reader.readLine()) != null ) {
                Matcher m = Pattern.compile("(ttyACM)").matcher(line);
                if ( m.find() ) {
                    this.portName = line;
                    //  System.out.print(this.portName + "\n");
                }
            }

        } else if ( SystemUtils.IS_OS_MAC_OSX ) {
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec("ls /dev/");

            BufferedReader reader = new BufferedReader(new InputStreamReader(pr.getInputStream()));

            String line;
            while ( (line = reader.readLine()) != null ) {
                Matcher m = Pattern.compile("(cu.Arduino)").matcher(line);
                if ( m.find() ) {
                    this.portName = line;
                    //  System.out.print(this.portName + "\n");
                }
            }
        }
    }
}
