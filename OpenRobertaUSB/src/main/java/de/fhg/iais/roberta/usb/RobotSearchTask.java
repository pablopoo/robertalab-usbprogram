package de.fhg.iais.roberta.usb;

import de.fhg.iais.roberta.connection.IConnector;
import de.fhg.iais.roberta.ui.UIController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class RobotSearchTask implements Callable<IConnector> {
    private static final Logger LOG = Logger.getLogger("Connector");

    private final List<IConnector> connectorList;
    private final UIController controller;

    public RobotSearchTask(List<IConnector> connectorList, UIController controller) {
        this.connectorList = connectorList;
        this.controller = controller;
    }

    private boolean updateFoundRobots(Collection<IConnector> foundRobots) {
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

    public IConnector call() {
        List<IConnector> foundRobots = new ArrayList<>();

        while (true) {
            boolean wasListUpdated = updateFoundRobots(foundRobots);

            if ( foundRobots.isEmpty() ) {
                LOG.info("No robot connected!");
            } else if (foundRobots.size() == 1) {
                LOG.info("Only " + foundRobots.get(0).getBrickName() + " available.");
                return foundRobots.get(0);
            } else {
                if (wasListUpdated) {
                    LOG.info("list was updated!");
                    for (IConnector robot : foundRobots) {
                        LOG.info(robot.getBrickName() + " available.");
                    }
                    this.controller.setConnectorMap(foundRobots);
                }

                IConnector selectedRobot = this.controller.getSelectedRobot();
                if (selectedRobot != null) {
                    LOG.info(selectedRobot.toString());
                    return selectedRobot;
                }
            }
        }
    }
}
