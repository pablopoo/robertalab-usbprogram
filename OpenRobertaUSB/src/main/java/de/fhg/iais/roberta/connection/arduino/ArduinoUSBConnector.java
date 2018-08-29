package de.fhg.iais.roberta.connection.arduino;

import de.fhg.iais.roberta.connection.AbstractConnector;
import de.fhg.iais.roberta.util.JWMI;
import de.fhg.iais.roberta.util.ORAtokenGenerator;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArduinoUSBConnector extends AbstractConnector {

    private final List<UsbDevice> supportedRobots;

    protected String portName = null;

    private AbstractArduinoCommunicator arducomm = null;

    public ArduinoUSBConnector(ResourceBundle serverProps) {
        this(
            serverProps,
            "Arduino",
            Arrays.asList(
                new UsbDevice("2341", "0043"), // Uno
                new UsbDevice("2A03", "0043"), // Uno, different vendor
                new UsbDevice("2341", "0042"), // Mega
                new UsbDevice("0403", "6001") // Nano, FT32R UART USB
            ));
    }

    protected ArduinoUSBConnector(ResourceBundle serverProps, String brickName, List<UsbDevice> supportedRobots) {
        super(serverProps, brickName);

        this.supportedRobots = supportedRobots;
    }

    @Override
    public boolean findRobot() {
        if ( SystemUtils.IS_OS_LINUX ) {
            return findArduinoLinux();
        } else if ( SystemUtils.IS_OS_WINDOWS ) {
            return findArduinoWindows();
        } else if ( SystemUtils.IS_OS_MAC_OSX ) {
            return findArduinoMac();
        } else {
            return false;
        }
    }

    protected AbstractArduinoCommunicator createArduinoCommunicator() {
        return new ArduinoCommunicator(this.brickName);
    }

    @Override
    protected void runLoopBody() throws InterruptedException {
        switch ( this.state ) {
            case DISCOVER:
                if ( this.portName.isEmpty() ) {
                    LOG.info("No Arduino device connected");
                    Thread.sleep(1000);
                } else {
                    this.arducomm = createArduinoCommunicator();
                    this.state = State.WAIT_FOR_CONNECT_BUTTON_PRESS;
                    notifyConnectionStateChanged(this.state);
                    break;
                }
                break;
            case WAIT_EXECUTION:
                this.state = State.WAIT_EXECUTION;
                notifyConnectionStateChanged(this.state);

                this.state = State.WAIT_FOR_CMD;
                notifyConnectionStateChanged(this.state);

                Thread.sleep(1000);
                break;
            case WAIT_FOR_CONNECT_BUTTON_PRESS:
                //                    // GUI initiates changing state to CONNECT
                Thread.sleep(1000);
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
                    LOG.info("CONNECT {}", io.getMessage());
                    reset(State.ERROR_HTTP);
                }

            case WAIT_FOR_CMD:
                this.brickData = this.arducomm.getDeviceInfo();
                this.brickData.put(KEY_TOKEN, this.token);
                this.brickData.put(KEY_CMD, CMD_PUSH);
                try {
                    JSONObject response = this.servcomm.pushRequest(this.brickData);
                    String cmdKey = response.getString(KEY_CMD);
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

                            if (this.arducomm instanceof ArduinoCommunicator) {
                                this.arducomm.setType(ArduinoType.fromString(response.getString(KEY_SUBTYPE).toLowerCase()));
                            }

                            this.state = State.WAIT_UPLOAD;
                            notifyConnectionStateChanged(this.state);
                            this.arducomm.uploadFile(this.portName, temp.getAbsolutePath());
                            this.state = State.WAIT_EXECUTION;
                            notifyConnectionStateChanged(this.state);
                        } catch ( IOException io ) {
                            LOG.info("Download and run failed: {}", io.getMessage());
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
                    LOG.info("WAIT_FOR_CMD {}", r.getMessage());
                    reset(State.ERROR_HTTP);
                }
            default:
                break;
        }
    }

    // based on https://stackoverflow.com/questions/22042661/mac-osx-get-usb-vendor-id-and-product-id
    protected boolean findArduinoMac() {
        try {
            Runtime rt = Runtime.getRuntime();
            String commands[] =
                {
                    "/bin/sh",
                    "-c",
                    "system_profiler SPUSBDataType"
                        + "    | awk '"
                        + "      /Product ID:/{p=$3}"
                        + "      /Vendor ID:/{v=$3}"
                        + "      /Manufacturer:/{sub(/.*: /,\"\"); m=$0}"
                        + "      /Location ID:/{sub(/.*: /,\"\"); printf(\"%s:%s %s (%s)\\n\", v, p, $0, m);}"
                        + "    '"
                };
            Process pr = rt.exec(commands);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
                String line;
                while ( (line = reader.readLine()) != null ) {
                    for ( UsbDevice device : supportedRobots ) {
                        // search the device in the commands output
                        Matcher m = Pattern.compile("(?i)0x" + device.vendorId + ":0x" + device.productId + " 0x(\\d{3}).* \\/").matcher(line);
                        if ( m.find() ) {
                            // the corresponding tty ID seems to be the third hex number
                            // TODO do better, this is just an ugly workaround that always takes the first tty.usbserial
                            // TODO i do not know any way to correlate the unique id of the port to the device
                            if ( device.vendorId.equalsIgnoreCase("0403") && device.productId.equalsIgnoreCase("6001") ) {
                                Process devPr = rt.exec("ls /dev/");
                                try (BufferedReader devReader = new BufferedReader(new InputStreamReader(devPr.getInputStream()))) {
                                    String devFolder;
                                    while ( (devFolder = devReader.readLine()) != null ) {
                                        if ( devFolder.contains("tty.usbserial") ) {
                                            this.portName = devFolder;
                                            LOG.info("Found robot: {}:{}, using portname {}", device.vendorId, device.productId, this.portName);
                                            return true;
                                        }
                                    }
                                }
                            }
                            this.portName = "tty.usbmodem" + m.group(1) + "1";
                            LOG.info("Found robot: {}:{}, using portname {}", device.vendorId, device.productId, this.portName);
                            return true;
                        }
                    }
                }
            }
        } catch ( IOException e ) {
            return false;
        }
        return false;
    }

    private boolean findArduinoWindows() {
        try {
            for ( UsbDevice device : supportedRobots ) {
                String ArduQueryResult =
                    JWMI.getWMIValue(
                        "SELECT * FROM Win32_PnPEntity WHERE PnPDeviceID " + "LIKE '%VID_" + device.vendorId + "%PID_" + device.productId + "%'",
                        "Caption");
                Matcher m = Pattern.compile(".*\\((COM.)\\)").matcher(ArduQueryResult);
                if ( m.find() ) {
                    this.portName = m.group(1);
                    LOG.info("Found robot: {}:{}, using portname {}", device.vendorId, device.productId, this.portName);
                    return true;
                }
            }
            return false;
        } catch ( Exception e ) {
            LOG.error("Something went wrong when finding Arduinos: {}", e.getMessage());
            return false;
        }
    }

    private boolean findArduinoLinux() {
        File devices = new File("/sys/bus/usb/devices");

        // check every usb device
        for ( File devicesDirectories : devices.listFiles() ) {
            File idVendorFile = new File(devicesDirectories, "idVendor");
            File idProductFile = new File(devicesDirectories, "idProduct");

            // if the id files exist check the content
            if ( idVendorFile.exists() && idProductFile.exists() ) {
                try {
                    String idVendor = Files.lines(idVendorFile.toPath()).findFirst().get();
                    String idProduct = Files.lines(idProductFile.toPath()).findFirst().get();

                    // see if the ids are supported
                    if ( this.supportedRobots.contains(new UsbDevice(idVendor, idProduct)) ) {
                        // recover the tty portname of the device
                        // it can be found in the subdirectory with the same name as the device
                        for ( File subdirectory : devicesDirectories.listFiles() ) {
                            if ( subdirectory.getName().contains(devicesDirectories.getName()) ) {
                                List<File> subsubdirs = Arrays.asList(subdirectory.listFiles());

                                // look for a directory containing tty, in case its only called tty look into it to find the real name
                                subsubdirs.stream().filter(s -> s.getName().contains("tty")).findFirst().ifPresent(
                                    f -> this.portName = f.getName().equals("tty") ? f.list()[0] : f.getName());
                            }
                        }
                        LOG.info("Found robot: {}:{}, using portname {}", idVendor, idProduct, this.portName);
                        return true;
                    }
                } catch ( IOException e ) {
                    // continue if id files do not exist
                }
            }
        }

        return false;
    }

    public String getPort() {
        return this.portName;
    }
}
