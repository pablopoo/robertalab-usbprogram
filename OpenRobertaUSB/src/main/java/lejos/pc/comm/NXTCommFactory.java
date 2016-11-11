package lejos.pc.comm;

import java.io.IOException;

import lejos.internal.jni.JNIClass;
import lejos.internal.jni.JNIException;
import lejos.internal.jni.JNILoader;
import lejos.internal.jni.OSInfo;

/**
 * Creates a version of <code>{@link NXTComm}</code> appropriate to the OS in use and protocol
 * (Bluetooth or USB) that is requested.
 *
 * @author From leJOS, modified by Daniel Pyka for Open Roberta
 */
public class NXTCommFactory {

    /**
     * Use USB for the connection
     */
    public static final int USB = 1;
    /**
     * Use Bluetooth for the connection
     */
    public static final int BLUETOOTH = 2;
    /**
     * Use either USB or Bluetooth for the connection. Normally, USB is tested first since it fails faster.
     */
    public static final int ALL_PROTOCOLS = USB | BLUETOOTH;

    private static JNILoader jniloader;

    private static synchronized JNILoader getJNILoader() throws IOException {
        if ( jniloader == null ) {
            jniloader = new JNILoader();
        }
        return jniloader;
    }

    /**
     * Load a comms driver for a protocol (USB or Bluetooth)
     *
     * @param protocol
     *        the protocol
     * @return a driver that supports the nxtComm interface
     * @throws NXTCommException
     */
    public static NXTComm createNXTComm(int protocol) throws NXTCommException {
        JNILoader jnil;
        try {
            jnil = getJNILoader();
        } catch ( IOException e ) {
            throw new NXTCommException(e);
        }
        OSInfo osi;
        try {
            osi = new OSInfo();
        } catch ( IOException e ) {
            osi = null;
        }

        String nxtCommName;
        switch ( protocol ) {
            case NXTCommFactory.USB: {
                boolean fantom = osi.isOS(OSInfo.OS_WINDOWS) || osi.isOS(OSInfo.OS_MACOSX);
                nxtCommName = fantom ? "lejos.pc.comm.NXTCommFantom" : "lejos.pc.comm.NXTCommLibnxt";
                break;
            }
            case NXTCommFactory.BLUETOOTH: {
                nxtCommName = "lejos.pc.comm.NXTCommBluecove";
                break;
            }
            default:
                throw new NXTCommException("unknown protocol");
        }

        try {
            return newNXTCommInstance(nxtCommName, jnil);

        } catch ( NXTCommException e ) {
            throw new NXTCommException(e);
        } catch ( ClassNotFoundException e ) {
            throw new NXTCommException(e);
        } catch ( InstantiationException e ) {
            throw new NXTCommException(e);
        } catch ( IllegalAccessException e ) {
            throw new NXTCommException(e);
        } catch ( JNIException e ) {
            throw new NXTCommException(e);
        }
    }

    private static NXTComm newNXTCommInstance(String classname, JNILoader jnil)
        throws NXTCommException,
        ClassNotFoundException,
        JNIException,
        InstantiationException,
        IllegalAccessException {
        try {
            Class<?> c = Class.forName(classname);
            Object o = c.newInstance();

            if ( o instanceof JNIClass ) {
                ((JNIClass) o).initialize(jnil);
            }

            return (NXTComm) o;
        } catch ( UnsatisfiedLinkError e ) {
            throw new NXTCommException("Cannot load NXTComm driver", e);
        }
    }

}
