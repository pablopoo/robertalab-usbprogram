package de.fhg.iais.roberta.connection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Observable;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import de.fhg.iais.roberta.util.ORAtokenGenerator;
import jssc.SerialPortException;

public class ArduUSBConnector extends Observable implements Runnable, Connector {

    private String serverIp = "localhost";
    private String serverPort = "1999";
    private final String serverAddress;

    private static final String CRLF = "\r\n";
    private String portName;

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

    public void getPortName() throws Exception {
        String ArduQueryResult = getWMIValue("SELECT * FROM Win32_PnPEntity WHERE Caption LIKE '%(COM%' ", "Caption");
        Matcher m = Pattern.compile("(Silicon Labs CP210x USB to UART Bridge \\()(.*)\\)").matcher(ArduQueryResult);
        while ( m.find() ) {
            this.portName = m.group(2);
        }
    }

    //** jWMI from www.henryranch.net **//

    public static String getVBScript(String wmiQueryStr, String wmiCommaSeparatedFieldName) {
        String vbs = "Dim oWMI : Set oWMI = GetObject(\"winmgmts:\")" + CRLF;
        vbs += "Dim classComponent : Set classComponent = oWMI.ExecQuery(\"" + wmiQueryStr + "\")" + CRLF;
        vbs += "Dim obj, strData" + CRLF;
        vbs += "For Each obj in classComponent" + CRLF;
        String[] wmiFieldNameArray = wmiCommaSeparatedFieldName.split(",");
        for ( int i = 0; i < wmiFieldNameArray.length; i++ ) {
            vbs += "  strData = strData & obj." + wmiFieldNameArray[i] + " & VBCrLf" + CRLF;
        }
        vbs += "Next" + CRLF;
        vbs += "wscript.echo strData" + CRLF;
        return vbs;

    }

    private static String getEnvVar(String envVarName) throws Exception {
        String varName = "%" + envVarName + "%";
        String envVarValue = execute(new String[] {
            "cmd.exe",
            "/C",
            "echo " + varName
        });
        if ( envVarValue.equals(varName) ) {
            throw new Exception("Environment variable '" + envVarName + "' does not exist!");
        }
        return envVarValue;
    }

    private static void writeStrToFile(String filename, String data) throws Exception {
        FileWriter output = new FileWriter(filename);
        output.write(data);
        output.flush();
        output.close();
        output = null;
    }

    public static String getWMIValue(String wmiQueryStr, String wmiCommaSeparatedFieldName) throws Exception {
        String vbScript = getVBScript(wmiQueryStr, wmiCommaSeparatedFieldName);
        String tmpDirName = getEnvVar("TEMP").trim();
        String tmpFileName = tmpDirName + File.separator + "jwmi.vbs";
        writeStrToFile(tmpFileName, vbScript);
        String output = execute(new String[] {
            "cmd.exe",
            "/C",
            "cscript.exe",
            tmpFileName
        });
        new File(tmpFileName).delete();

        return output.trim();
    }

    private static String execute(String[] cmdArray) throws Exception {
        Process process = Runtime.getRuntime().exec(cmdArray);
        BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String output = "";
        String line = "";
        while ( (line = input.readLine()) != null ) {
            //need to filter out lines that don't contain our desired output
            if ( !line.contains("Microsoft") && !line.equals("") ) {
                output += line + CRLF;
            }
        }
        process.destroy();
        process = null;
        return output.trim();
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
                    try {
                        getPortName();
                    } catch ( Exception e1 ) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }

                    if ( (this.portName.length() > 0) ) {
                        // TODO let user choose which one to connect?
                        this.arducomm = new ArduCommunicator(this.portName);
                        this.state = State.WAIT_FOR_CONNECT_BUTTON_PRESS;
                        notifyConnectionStateChanged(this.state);
                        break;
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
                    try {
                        this.brickData = this.arducomm.getDeviceInfo();
                        this.brickData.put(KEY_TOKEN, this.token);
                        this.brickData.put(KEY_CMD, CMD_REGISTER);
                    } catch ( IOException e ) {
                        log.info("CONNECT " + e.getMessage());
                        break;
                    }
                    try {
                        JSONObject serverResponse = this.servcomm.pushRequest(this.brickData);
                        String command = serverResponse.getString("cmd");
                        if ( command.equals(CMD_REPEAT) ) {

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
                        log.info("CONNECT " + e.getMessage());
                        reset(State.ERROR_HTTP, false);
                    } catch ( RuntimeException e ) {
                        log.info("CONNECT " + e.getMessage());
                        reset(State.ERROR_HTTP, false);
                    }

                    break;
                case WAIT_FOR_CMD:
                    try {
                        this.brickData = this.arducomm.getDeviceInfo();
                        this.brickData.put(KEY_TOKEN, this.token);
                        this.brickData.put(KEY_CMD, CMD_PUSH);
                    } catch ( IOException e ) {
                        log.info("WAIT_FOR_CMD " + e.getMessage());
                        this.state = State.WAIT_EXECUTION;
                        break;
                    }
                    try {
                        String cmd = this.servcomm.pushRequest(this.brickData).getString(KEY_CMD);
                        if ( cmd.equals(CMD_REPEAT) ) {

                        } else if ( cmd.equals(CMD_DOWNLOAD) ) {
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

                                this.arducomm.uploadFile(this.portName, temp.getAbsolutePath());
                                this.state = State.WAIT_EXECUTION;
                            } catch ( IOException e ) {
                                log.info("Download and run failed: " + e.getMessage());
                                log.info("Do not give up yet - make the next push request");
                                this.state = State.WAIT_FOR_CMD;
                            } catch ( InterruptedException e ) {
                                log.info("Download and run failed: " + e.getMessage());
                                log.info("Do not give up yet - make the next push request");
                                this.state = State.WAIT_FOR_CMD;
                            }
                        } else if ( cmd.equals(CMD_CONFIGURATION) ) {
                        } else {

                            throw new RuntimeException("Unexpected response from server");
                        }
                    } catch ( RuntimeException e ) {
                        log.info("WAIT_FOR_CMD " + e.getMessage());
                        reset(State.ERROR_HTTP, true);
                    } catch ( IOException e ) {
                        log.info("WAIT_FOR_CMD " + e.getMessage());
                        reset(State.ERROR_HTTP, true);
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
    public void userPressConnectButton() {
        this.state = State.CONNECT_BUTTON_IS_PRESSED;
    }

    @Override
    public void userPressDisconnectButton() {
        this.userDisconnect = true;
        this.servcomm.abort(); // will throw exception, reset will be called in catch statement
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

        try {
            getPortName();
            return getWMIValue("SELECT * FROM Win32_PnPEntity WHERE Caption LIKE '%(COM%' ", "Caption").contains("Silicon Labs");
        } catch ( Exception e ) {
            return false;
        }
    }

    @Override
    public String getBrickName() {
        return "Ardu";
    }

}
