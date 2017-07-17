package de.fhg.iais.roberta.connection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.logging.Logger;

import org.json.JSONObject;

import lejos.nxt.remote.DeviceInfo;
import lejos.nxt.remote.NXTCommand;
import lejos.pc.comm.NXTComm;
import lejos.pc.comm.NXTCommException;
import lejos.pc.comm.NXTCommFactory;
import lejos.pc.comm.NXTConnector;
import lejos.pc.comm.NXTInfo;

public class NXTCommunicator {

    private static Logger log = Logger.getLogger("NXTCommunicator");

    private static final int MAX_BUFFER_SIZE = 58; // from lejos.nxt.remote.NXTCommand
    private static final byte[] APROGRAMISRUNNING =
        {
            (byte) -17,
            (byte) -65,
            (byte) -67,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0,
            (byte) 0
        };

    private final NXTInfo nxt;
    private final int protocol;
    private NXTComm nxtCommunication = null;
    private NXTCommand nxtCommand = null;

    public NXTCommunicator(NXTInfo nxt, int protocol) {
        this.nxt = nxt;
        this.protocol = protocol;
    }

    public static NXTInfo[] discover() {
        NXTInfo[] nxts = new NXTConnector().search(NXTCommFactory.ALL_PROTOCOLS);
        return nxts;
    }

    private void connect() throws NXTCommException {
        this.nxtCommunication = NXTCommFactory.createNXTComm(this.protocol);
        this.nxtCommunication.open(this.nxt, NXTComm.LCP);
        this.nxtCommand = new NXTCommand(this.nxtCommunication);
    }

    public JSONObject getDeviceInfo() {
        JSONObject deviceInfo = new JSONObject();
        try {
            connect();
            DeviceInfo info = this.nxtCommand.getDeviceInfo();

            deviceInfo.put("firmwarename", "NXT");
            deviceInfo.put("robot", "nxt");
            deviceInfo.put("firmwareversion", this.nxtCommand.getFirmwareVersion().firmwareVersion);
            deviceInfo.put("macaddr", info.bluetoothAddress);
            deviceInfo.put("brickname", info.NXTname.trim());
            deviceInfo.put("battery", new DecimalFormat("#.#").format(((float) this.nxtCommand.getBatteryLevel()) / 1000));

        } catch ( IOException e ) {
            return null;
        } catch ( NXTCommException e ) {
            return null;
        } finally {
            disconnect();
        }
        return deviceInfo;
    }

    public void playAscending() throws IOException {
        int C2 = 523;
        for ( int i = 4; i < 8; i++ ) {
            this.nxtCommand.playTone(C2 * i / 4, 100);
            try {
                Thread.sleep(100);
            } catch ( InterruptedException e ) {
                // ok
            }
        }
    }

    public void playDescending() throws IOException {
        int C2 = 523;
        for ( int i = 7; i > 3; i-- ) {
            this.nxtCommand.playTone(C2 * i / 4, 100);
            try {
                Thread.sleep(100);
            } catch ( InterruptedException e ) {
                // ok
            }
        }
    }

    public void playProgramDownload() throws IOException {
        int C2 = 300;
        for ( int i = 12; i < 16; i++ ) {
            this.nxtCommand.playTone(C2 * i / 8, 100);

            try {
                Thread.sleep(100);
            } catch ( InterruptedException e ) {
                // ok
            }
        }
    }

    /**
     * @return true if a program is currently running, false otherwise
     * @throws IOException
     */
    public NXTState getNXTstate() {
        try {
            connect();
            boolean isRunning = !Arrays.equals(APROGRAMISRUNNING, this.nxtCommand.getCurrentProgramName().getBytes());
            return isRunning ? NXTState.PROGRAM_RUNNING : NXTState.WAITING_FOR_PROGRAM;
        } catch ( IOException e ) {
            return NXTState.DISCONNECTED;
        } catch ( NXTCommException e ) {
            return NXTState.DISCONNECTED;
        } finally {
            disconnect();
        }

    }

    public void uploadFile(byte[] binaryfile, String nxtFileName) throws IOException, NXTCommException {
        nxtFileName = nxtFileName.split("\\.")[0];
        if ( nxtFileName.length() > NXTCommand.MAX_FILENAMELENGTH - 5 ) {
            nxtFileName = nxtFileName.substring(0, NXTCommand.MAX_FILENAMELENGTH - 5);
        }
        nxtFileName = nxtFileName + ".rxe";
        if ( getNXTstate() == NXTState.WAITING_FOR_PROGRAM ) {
            try {
                connect();
                this.nxtCommand.delete(nxtFileName);
                sleep(200); // give the nxt time to react
                writeFileToNXT(binaryfile, nxtFileName);
                sleep(200);
                // this.nxtCommand.startProgram(nxtFileName); this will execute the program but we do not want
            } finally {
                disconnect();
            }
        }
    }

    public void playSound(String type) {
        try {
            connect();
            if ( type.equals("connect") ) {
                playAscending();
            } else if ( type.equals("disconnect") ) {
                playDescending();
            } else if ( type.equals("download") ) {
                playProgramDownload();
            }
        } catch ( IOException e ) {
            log.info("playing " + type + " sound sequence failed");
        } catch ( NXTCommException e ) {
            log.info("playing " + type + " sound sequence failed");
        } finally {
            disconnect();
        }
    }

    private void writeFileToNXT(byte[] binaryfile, String nxtFileName) throws IOException {
        InputStream is = new ByteArrayInputStream(binaryfile);
        byte handle = this.nxtCommand.openWrite(nxtFileName, binaryfile.length);
        byte[] data = new byte[MAX_BUFFER_SIZE];
        int len;
        while ( (len = is.read(data)) > 0 ) {
            this.nxtCommand.writeFile(handle, data, 0, len);
        }
        this.nxtCommand.setVerify(true);
        this.nxtCommand.closeFile(handle);
    }

    private void disconnect() {
        try {
            this.nxtCommand.close();
            this.nxtCommand.disconnect();
        } catch ( Exception e ) {
            // ok
        }
        try {
            this.nxtCommunication.close();
        } catch ( Exception e ) {
            // ok
        }
    }

    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch ( InterruptedException e ) {
            // ok
        }
    }

}
