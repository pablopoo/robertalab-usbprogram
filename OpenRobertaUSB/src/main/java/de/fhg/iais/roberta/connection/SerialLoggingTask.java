package de.fhg.iais.roberta.connection;

import com.fazecast.jSerialComm.SerialPort;
import de.fhg.iais.roberta.ui.UIController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class SerialLoggingTask implements Callable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(SerialLoggingTask.class);

    private UIController uiController;
    private int serialRate;

    public SerialLoggingTask(UIController uiController, int serialRate) {
        this.uiController = uiController;
        this.serialRate = serialRate;
    }

    // https://github.com/Fazecast/jSerialComm/wiki/Nonblocking-Reading-Usage-Example
    @Override
    public Void call() {
        SerialPort[] serialPorts = SerialPort.getCommPorts();
        SerialPort comPort = serialPorts[0];
        comPort.setBaudRate(serialRate);
        comPort.openPort();
        LOG.debug("SerialPort {} {} {} opened, logging with baud rate of {}", comPort.getSystemPortName(), comPort.getDescriptivePortName(), comPort.getPortDescription(), comPort.getBaudRate());
        while(!Thread.currentThread().isInterrupted()) {
            try {
                while (comPort.bytesAvailable() == 0) {
                    Thread.sleep(200);
                }

                byte[] readBuffer = new byte[comPort.bytesAvailable()];
                comPort.readBytes(readBuffer, readBuffer.length);
                this.uiController.appendSerial(readBuffer);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        comPort.closePort();
        return null;
    }
}
