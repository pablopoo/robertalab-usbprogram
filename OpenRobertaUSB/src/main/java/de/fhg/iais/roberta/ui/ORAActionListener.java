package de.fhg.iais.roberta.ui;

import de.fhg.iais.roberta.usb.USBProgram;

import javax.swing.AbstractButton;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Logger;

public class ORAActionListener extends WindowAdapter implements ActionListener {
    private static final Logger LOG = Logger.getLogger("Connector");

    private final UIController uiController;

    public ORAActionListener(UIController uiController) {
        this.uiController = uiController;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        AbstractButton button = (AbstractButton) actionEvent.getSource();
        if ( button.getActionCommand().equals("close") ) {
            LOG.info("User close");
            this.uiController.closeApplication();
        } else if ( button.getActionCommand().equals("about") ) {
            LOG.info("User about");
            this.uiController.showAboutPopup();
        } else if ( button.getActionCommand().equals("customaddress") ) {
            LOG.info("User custom address");
            this.uiController.showAdvancedOptions();
        } else if ( button.getActionCommand().equals("back") ) {
            LOG.info("User back");
            USBProgram.stopConnector();
            this.uiController.setDiscover();
        } else {
            if ( button.isSelected() ) {
                LOG.info("User connect");
                if ( this.uiController.getConnector() != null ) { //TODO
                    this.uiController.checkForValidCustomServerAddressAndUpdate();
                    this.uiController.getConnector().userPressConnectButton();
                }
                button.setText(this.uiController.getRb().getString("disconnect")); //TODO
            } else {
                LOG.info("User disconnect");
                if ( this.uiController.getConnector() != null ) {
                    this.uiController.getConnector().userPressDisconnectButton();
                }
                button.setText(this.uiController.getRb().getString("connect")); //TODO
            }
        }
    }

    @Override
    public void windowClosing(WindowEvent e) {
        LOG.info("User close");
        this.uiController.closeApplication();
    }
}
