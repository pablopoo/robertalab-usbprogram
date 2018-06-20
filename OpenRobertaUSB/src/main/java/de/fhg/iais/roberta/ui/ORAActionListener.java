package de.fhg.iais.roberta.ui;

import javax.swing.AbstractButton;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;

public class ORAActionListener implements ActionListener {
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
        } else {
            if ( button.isSelected() ) {
                LOG.info("User connect");
                if ( this.uiController.getConnector() != null ) {
                    this.uiController.checkForValidCustomServerAddressAndUpdate();
                    this.uiController.getConnector().userPressConnectButton();
                }
                button.setText(this.uiController.getRb().getString("disconnect"));
            } else {
                LOG.info("User disconnect");
                if ( this.uiController.getConnector() != null ) {
                    this.uiController.getConnector().userPressDisconnectButton();
                }
                button.setText(this.uiController.getRb().getString("connect"));
            }
        }
    }
}
