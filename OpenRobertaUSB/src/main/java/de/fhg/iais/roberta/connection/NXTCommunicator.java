package de.fhg.iais.roberta.connection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Arrays;

import org.json.JSONObject;

import lejos.nxt.remote.DeviceInfo;
import lejos.nxt.remote.NXTCommand;
import lejos.pc.comm.NXTComm;
import lejos.pc.comm.NXTCommException;
import lejos.pc.comm.NXTCommFactory;
import lejos.pc.comm.NXTConnector;
import lejos.pc.comm.NXTInfo;

public class NXTCommunicator {

    private static final int MAX_BUFFER_SIZE = 58; // from lejos.nxt.remote.NXTCommand
    private static final byte[] APROGRAMISRUNNING = {
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

    public void connect() throws NXTCommException {
        this.nxtCommunication = NXTCommFactory.createNXTComm(this.protocol);
        this.nxtCommunication.open(this.nxt, NXTComm.LCP);
        this.nxtCommand = new NXTCommand(this.nxtCommunication);
    }

    public JSONObject getDeviceInfo() throws IOException {
        JSONObject deviceInfo = new JSONObject();
        deviceInfo.put("firmwarename", "lejos");
        //deviceInfo.put("firmwareversion", this.nxtCommand.getFirmwareVersion().firmwareVersion);
        deviceInfo.put("firmwareversion", "0.9.0-beta");
        deviceInfo.put("battery", new DecimalFormat("#.#").format(((float) this.nxtCommand.getBatteryLevel()) / 1000));
        deviceInfo.put("menuversion", "1.4.0");
        //deviceInfo.put("protocolversion", this.nxtCommand.getFirmwareVersion().protocolVersion);
        //deviceInfo.put("localaddress", this.nxtCommand.getLocalAddress());
        DeviceInfo info = this.nxtCommand.getDeviceInfo();
        deviceInfo.put("macaddr", info.bluetoothAddress);
        deviceInfo.put("brickname", info.NXTname.trim());
        //deviceInfo.put("freeflash", info.freeFlash);
        //deviceInfo.put("signalstrength", info.signalStrength);
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

    /**
     * @return true if a program is currently running, false otherwise
     * @throws IOException
     */
    public boolean isProgramRunning() throws IOException {
        return !Arrays.equals(APROGRAMISRUNNING, this.nxtCommand.getCurrentProgramName().getBytes());
    }

    public void uploadFile(byte[] binaryfile, String nxtFileName) throws IOException {
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

    public void uploadAndRunFile(byte[] binaryfile, String nxtFileName) throws IOException, InterruptedException {
        if ( nxtFileName.length() > NXTCommand.MAX_FILENAMELENGTH ) {
            nxtFileName = nxtFileName.substring(0, NXTCommand.MAX_FILENAMELENGTH - 1);
        }
        if ( !isProgramRunning() ) {
            this.nxtCommand.delete(nxtFileName);
            Thread.sleep(200); // give the nxt time to react
            uploadFile(binaryfile, nxtFileName);
            Thread.sleep(200);
            this.nxtCommand.startProgram(nxtFileName);
            Thread.sleep(200);
        }
    }
    
    

    public void disconnect() {
        try {
            this.nxtCommand.close();
            this.nxtCommand.disconnect();
        } catch ( IOException | NullPointerException e ) {
            // ok
        }
        try {
            this.nxtCommunication.close();
        } catch ( IOException | NullPointerException e ) {
            // ok
        }
    }

}
