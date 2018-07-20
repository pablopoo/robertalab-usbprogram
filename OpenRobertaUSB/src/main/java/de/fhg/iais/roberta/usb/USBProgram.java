package de.fhg.iais.roberta.usb;

import de.fhg.iais.roberta.connection.ArduinoUSBConnector;
import de.fhg.iais.roberta.connection.BotnrollUSBConnector;
import de.fhg.iais.roberta.connection.EV3USBConnector;
import de.fhg.iais.roberta.connection.IConnector;
import de.fhg.iais.roberta.ui.ConnectionView;
import de.fhg.iais.roberta.ui.UIController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class USBProgram {
    private static final Logger LOG = LoggerFactory.getLogger(USBProgram.class);

    static {
        ResourceBundle serverProps = ResourceBundle.getBundle("OpenRobertaUSB");
        connectorList = Arrays.<IConnector>asList(
            new EV3USBConnector(serverProps),
            new ArduinoUSBConnector(serverProps),
            new BotnrollUSBConnector(serverProps));
    }

    private static final List<IConnector> connectorList;
    private static boolean connectorShouldStop = false;
    private ConnectionView view = null;
    private UIController controller = null;

    public USBProgram() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ResourceBundle messages = getLocale();
                    USBProgram.this.view = new ConnectionView(messages);
                    USBProgram.this.controller = new UIController(USBProgram.this.view, messages);
                }
            });
        } catch ( InterruptedException | InvocationTargetException e ) {
            LOG.error("UI could not be set up");
            closeProgram();
        }
    }

    public void run() {
        LOG.info("Entering run method!");
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        while(!Thread.currentThread().isInterrupted()) {
            Future<IConnector> robotSearchFuture = executorService.submit(new RobotSearchTask(connectorList, this.controller));

            try {
                IConnector selectedRobot = robotSearchFuture.get();
                this.controller.setConnector(selectedRobot);

                Future<Boolean> connectorFuture = executorService.submit(selectedRobot);

                while(!connectorFuture.isDone()) {
                    if(connectorShouldStop) {
                        connectorFuture.cancel(true);
                    }
                    Thread.sleep(2000);
                }
                connectorShouldStop = false;
                LOG.info("Connector finished!");
            } catch ( InterruptedException | ExecutionException e ) {
                LOG.error("Something went wrong: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        executorService.shutdown();
    }

    public static void stopConnector() {
        connectorShouldStop = true;
    }

    private static ResourceBundle getLocale() {
        ResourceBundle rb;
        try {
            rb = ResourceBundle.getBundle("messages", Locale.getDefault());
        } catch ( RuntimeException e ) {
            rb = ResourceBundle.getBundle("messages", Locale.ENGLISH);
        }

        LOG.info("Language {}", rb.getLocale());
        return rb;
    }

    public static void closeProgram() {
        System.exit(0);
    }
}
