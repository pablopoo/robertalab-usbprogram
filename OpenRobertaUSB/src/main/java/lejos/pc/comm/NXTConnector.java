package lejos.pc.comm;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Connects to a NXT using Bluetooth or USB (or either) and supplies input and output
 * data streams.
 *
 * @author Lawrie Griffiths and Roger Glassey,<br />
 *         modified by Daniel Pyka for Open Roberta
 */
public class NXTConnector {

    private static final Logger log = Logger.getLogger("NXTConnector");

    /**
     * Search for one or more NXTs and return an array of <code>NXTInfo</code> instances for those found.
     *
     * @param nxt the name of the NXT to connect to or <code>null</code> for any
     * @param addr the address of the NXT to connect to or <code>null</code>
     * @param protocols the protocols to use: <code>{@link NXTCommFactory#ALL_PROTOCOLS}</code>,
     *        <code>{@link NXTCommFactory#BLUETOOTH}</code>, or <code>{@link NXTCommFactory#USB}</code>
     * @return an array of <code>NXTInfo</code> instances for found NXTs
     */
    public NXTInfo[] search(int protocols) {
        String searchParam = null;

        NXTInfo[] nxtInfos = new NXTInfo[0];

        // Try USB first
        if ( (protocols & NXTCommFactory.USB) != 0 ) {
            NXTComm nxtComm;
            try {
                nxtComm = NXTCommFactory.createNXTComm(NXTCommFactory.USB);
            } catch ( NXTCommException e ) {
                nxtComm = null;
                log.severe("Error: Failed to load USB comms driver. " + e);
            }
            if ( nxtComm != null ) {
                try {
                    nxtInfos = nxtComm.search(searchParam);
                } catch ( NXTCommException ex ) {
                    log.severe("Error: Search failed. " + ex);
                }
                try {
                    nxtComm.close();
                    nxtComm = null;
                } catch ( IOException e ) {
                    log.warning("Error closing USB nxtComm. " + e);
                }
            }

        }

        if ( nxtInfos.length > 0 ) {
            return nxtInfos;
        }

        //        // If nothing found on USB, try Bluetooth
        //        if ( (protocols & NXTCommFactory.BLUETOOTH) != 0 ) {
        //            NXTComm nxtComm;
        //            // Load Bluetooth driver
        //            try {
        //                nxtComm = NXTCommFactory.createNXTComm(NXTCommFactory.BLUETOOTH);
        //            } catch ( NXTCommException e ) {
        //                log.severe("Error: Failed to load Bluetooth comms driver. " + e);
        //                return nxtInfos;
        //            }
        //
        //            // If none found, do a Bluetooth inquiry
        //            if ( nxtInfos.length == 0 ) {
        //                try {
        //                    nxtInfos = nxtComm.search(searchParam);
        //                } catch ( NXTCommException ex ) {
        //                    log.warning("Error: Search failed. " + ex);
        //                }
        //
        //                log.info("Inquiry found " + nxtInfos.length + " NXTs");
        //
        //                // Save the results in the properties file
        //                for ( int i = 0; i < nxtInfos.length; i++ ) {
        //                    log.info("Name " + i + " = " + nxtInfos[i].name);
        //                    log.info("Address " + i + " = " + nxtInfos[i].deviceAddress);
        //                }
        //            }
        //            try {
        //                nxtComm.close();
        //                nxtComm = null;
        //            } catch ( IOException e ) {
        //                log.warning("Error closing USB nxtComm. " + e);
        //            }
        //        }

        return nxtInfos;
    }

}
