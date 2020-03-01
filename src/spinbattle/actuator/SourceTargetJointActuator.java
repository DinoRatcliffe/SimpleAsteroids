package spinbattle.actuator;

import spinbattle.core.Planet;
import spinbattle.core.SpinGameState;
import spinbattle.core.Transporter;


/**
 *
 *  Class for an AI controller.  This
 */

public class SourceTargetJointActuator implements Actuator {

    int playerId;

    public SourceTargetJointActuator setDoNothing() {
        return this;
    }

    public SourceTargetJointActuator reset() {
        return this;
    }


    public SourceTargetJointActuator copy() {
        SourceTargetJointActuator copy = new SourceTargetJointActuator();
        copy.playerId = playerId;
        return copy;
    }

    public SourceTargetJointActuator setPlayerId(int playerId) {
        this.playerId = playerId;
        return this;
    }

    public int nActions(SpinGameState spinGameState) {
        return spinGameState.planets.size() * spinGameState.planets.size() - spinGameState.planets.size();
    }

    public SpinGameState actuate(int action, SpinGameState gameState) {
        if (gameState.params.transitSpeed == 0) {
            return gameState;
        }

        // matches how python itertools.permutations creates pairs
        int step = gameState.planets.size() - 1;
        int srcPlanet = action / step;
        int targetPlanet = action % step;
        if (targetPlanet >= srcPlanet) {
            targetPlanet += 1;
        }

        Planet source = gameState.planets.get(srcPlanet);
        Planet target = gameState.planets.get(targetPlanet);

        // check we're not trying to transit a planet to itself
        if (source == target) return null;

        if (source.transitReady() && source.ownedBy == playerId) {
            Transporter transit = source.getTransporter();
            if (transit == null) return null;
            // shift 50%
            try {
                transit.setPayload(source, source.shipCount / 2);
                transit.launch(source.position, target.position, playerId, gameState);
                transit.setTarget(targetPlanet);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Transit = " + transit);
                System.out.println("Source  = " + source);
                System.out.println("Target  = " + target);
            }
        }

        return gameState;
    }
}
