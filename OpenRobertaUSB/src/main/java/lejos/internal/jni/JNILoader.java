package lejos.internal.jni;


import java.io.IOException;


import de.fhg.iais.roberta.util.NativeUtils;

/**
 * This class handles the reference to the native libraries. The fixed folder structure from leJOS is removed completely. We load our native libraries from
 * maven src.main.resources.
 *
 * @author From leJOS, modified by Daniel Pyka for Open Roberta
 */
public class JNILoader {

    public JNILoader() {
        // ok
    }

    /**
     * Load the native library for USB connection (e.g. fantom.dll under windows).<br />
     * Both files are no longer located in the folder structure of leJOS. They are stored in src.main.resources from maven now to be compatible with our project
     * structure!<br />
     * TODO Under MAC OS another library file is referenced for USB connection. TEST!!! There seems to be a bug with file names in NXTCommLibnxt by leJOS.
     * <br />
     * This method is called from NXTCommFantom and NXTCommLibnxt from the pccomm library.
     *
     * @param caller NXTCommFantom or NXTCommLibnxt
     * @param libname file name without extension, will be added by java.lang.System.mapLibraryName()
     * @throws JNIException file not found most likely
     * @throws IOException 
     */
    public void loadLibrary(Class<?> caller, String libname) throws JNIException, IOException {
    	       NativeUtils.loadLibraryFromJar("/"+System.mapLibraryName(libname));
    }
    
  
    
}
