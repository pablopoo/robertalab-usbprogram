package de.fhg.iais.roberta.usb;

import de.fhg.iais.roberta.connection.ArduinoUSBConnector;
import de.fhg.iais.roberta.connection.BotnrollUSBConnector;
import de.fhg.iais.roberta.connection.EV3USBConnector;
import de.fhg.iais.roberta.connection.IConnector;
import de.fhg.iais.roberta.ui.ConnectionView;
import de.fhg.iais.roberta.ui.UIController;
import de.fhg.iais.roberta.util.ORAFormatter;
import org.apache.commons.lang3.SystemUtils;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class USBProgram {
    private static final String LOGFILENAME = "OpenRobertaUSB.log";
    private static final Logger LOG = Logger.getLogger("Connector");
    private static final ConsoleHandler consoleHandler = new ConsoleHandler();
    private static FileHandler fileHandler = null;
    private static File logFile = null;

    private final List<IConnector> connectorList;
    private ConnectionView view = null;
    private UIController controller = null;

    public USBProgram() {
        configureLogger();

        ResourceBundle serverProps = ResourceBundle.getBundle("OpenRobertaUSB");
        this.connectorList = Arrays.<IConnector>asList(
            new EV3USBConnector(serverProps),
            new ArduinoUSBConnector(serverProps),
            new BotnrollUSBConnector(serverProps));

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ResourceBundle messages = getLocale();
                    USBProgram.this.view = new ConnectionView(messages);
                    USBProgram.this.controller = new UIController(USBProgram.this.view, messages);
                }
            });
        } catch ( InterruptedException e ) {
            LOG.severe("UI could not be set up");
            Thread.currentThread().interrupt();
            closeProgram();
        } catch ( InvocationTargetException e ) {
            LOG.severe("UI could not be set up");
            closeProgram();
        }
    }

    private static ResourceBundle getLocale() {
        ResourceBundle rb;
        try {
            rb = ResourceBundle.getBundle("messages", Locale.getDefault());
        } catch ( RuntimeException e ) {
            rb = ResourceBundle.getBundle("messages", Locale.ENGLISH);
        }
//        rb = ResourceBundle.getBundle("messages", Locale.GERMAN);

        LOG.config("Language " + rb.getLocale());
        return rb;
    }

    private boolean updateFoundRobots(List<IConnector> foundRobots) {
        boolean updated = false;
        for ( IConnector connector : this.connectorList ) {
            if (connector.findRobot()) {
                if (!foundRobots.contains(connector)) {
                    foundRobots.add(connector);
                    updated = true;
                }
            } else {
                if (foundRobots.contains(connector)) {
                    foundRobots.remove(connector);
                    updated = true;
                }
            }
        }
        return updated;
    }

    public void run() {
        List<IConnector> foundRobots = new ArrayList<>();

        while ( true ) {
            boolean wasListUpdated = updateFoundRobots(foundRobots);

            if ( foundRobots.isEmpty() ) {
                LOG.info("No robot connected!");
            } else if (foundRobots.size() == 1) {
                LOG.info("Only " + foundRobots.get(0).getBrickName() + " available.");
                this.controller.setConnector(foundRobots.get(0));
                Thread t = new Thread(foundRobots.get(0));
                t.start();
                break;
            } else {
                for (IConnector robot : foundRobots) {
                    LOG.info(robot.getBrickName() + " available.");
                }
                if (wasListUpdated) {
                    LOG.info("list was updated!");
                    this.controller.setConnectorMap(foundRobots);
                }

                IConnector selectedRobot = this.controller.getSelectedRobot();
                if (selectedRobot != null) {
                    LOG.info(selectedRobot.toString());
                    this.controller.setConnector(selectedRobot);
                    Thread t = new Thread(selectedRobot);
                    t.start();
                    break;
                }
            }
        }
    }

    /**
     * Flush and close the file handler before closing the USB program.
     */
    public static void closeProgram() {
        fileHandler.flush();
        fileHandler.close();
        System.exit(0);
    }

    /**
     * Set up a file handler for writing a log file to either %APPDATA% on windows, or user.home on linux or mac. The USB program will log all important actions
     * and events.
     */
    private static void configureLogger() {
        String path = "";
        try {
            if ( SystemUtils.IS_OS_WINDOWS ) {
                path = System.getenv("APPDATA");
            } else if ( SystemUtils.IS_OS_LINUX ) {
                path = System.getProperty("user.home");
            } else if ( SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX ) {
                path = System.getProperty("user.home");
            }
            logFile = new File(path, "OpenRobertaUSB");
            if ( !logFile.exists() ) {
                logFile.mkdir();
            }
            fileHandler = new FileHandler(new File(logFile, LOGFILENAME).getPath(), false);
            fileHandler.setFormatter(new ORAFormatter());
            fileHandler.setLevel(Level.ALL);
        } catch ( IOException | SecurityException e ) {
            LOG.severe("Could not create folders and files needed for logging: " + e.getMessage());
        }
        consoleHandler.setFormatter(new ORAFormatter());
        consoleHandler.setLevel(Level.ALL);
        LOG.setLevel(Level.ALL);
        LOG.addHandler(consoleHandler);
        LOG.addHandler(fileHandler);
        LOG.setUseParentHandlers(false);
        LOG.info("Logging to file: " + new File(logFile, LOGFILENAME).getPath());
    }
}
