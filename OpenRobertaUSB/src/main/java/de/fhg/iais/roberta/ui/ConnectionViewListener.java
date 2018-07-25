package de.fhg.iais.roberta.ui;

import de.fhg.iais.roberta.usb.USBProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractButton;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ConnectionViewListener extends WindowAdapter implements ActionListener {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionViewListener.class);

    private final UIController uiController;

    public ConnectionViewListener(UIController uiController) {
        this.uiController = uiController;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        AbstractButton button = (AbstractButton) actionEvent.getSource();
        if ( button.getActionCommand().equals("close") ) {
            LOG.debug("User close");
            this.uiController.closeApplication();
        } else if ( button.getActionCommand().equals("about") ) {
            LOG.debug("User about");
            this.uiController.showAboutPopup();
        } else if ( button.getActionCommand().equals("customaddress") ) {
            LOG.debug("User custom address");
            this.uiController.showAdvancedOptions();
        } else if ( button.getActionCommand().equals("back") ) {
            LOG.debug("User back");
            USBProgram.stopConnector();
            this.uiController.setDiscover();
        } else if ( button.getActionCommand().equals("serial")) {
            LOG.debug("User serial");
            this.uiController.showSerialMonitor();
        } else {
            if ( button.isSelected() ) {
                LOG.debug("User connect");
                if ( this.uiController.getConnector() != null ) { //TODO
                    this.uiController.checkForValidCustomServerAddressAndUpdate();
                    this.uiController.getConnector().userPressConnectButton();
                }
                button.setText(this.uiController.getRb().getString("disconnect")); //TODO
            } else {
                LOG.debug("User disconnect");
                if ( this.uiController.getConnector() != null ) {
                    this.uiController.getConnector().userPressDisconnectButton();
                }
                button.setText(this.uiController.getRb().getString("connect")); //TODO
            }
        }
    }

    @Override
    public void windowClosing(WindowEvent e) {
        LOG.debug("User close");
        this.uiController.closeApplication();
    }
}
