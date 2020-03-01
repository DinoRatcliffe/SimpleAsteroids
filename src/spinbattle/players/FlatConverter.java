package spinbattle.players;

import ggi.core.AbstractGameState;
import spinbattle.actuator.SourceTargetActuator;
import spinbattle.core.Planet;
import spinbattle.core.SpinGameState;
import spinbattle.core.Transporter;

import java.util.function.BiFunction;

public class FlatConverter implements BiFunction<AbstractGameState, Integer, double[]> {

    @Override
    public double[] apply(AbstractGameState abstractGameState, Integer playerId) {
        return stateToInput((SpinGameState) abstractGameState, playerId);
    }

    private double[] stateToInput(SpinGameState gameState, int playerId) {
        // TODO handle if playerId is 1 -> make it look to network as if it is player 0
        int numPlanets = gameState.planets.size();
        double[] observation = new double[numPlanets * 4 + numPlanets * numPlanets + numPlanets];
        int currentIndex = 0;
        Planet currentPlanet;
        for (int i = 0; i < gameState.planets.size(); i++) {
            currentPlanet = gameState.planets.get(i);
            observation[currentIndex++] = currentPlanet.ownedBy == 0 ? 1 : 0;
            observation[currentIndex++] = currentPlanet.ownedBy == 1 ? 1 : 0;
            observation[currentIndex++] = currentPlanet.shipCount;
            observation[currentIndex++] = currentPlanet.growthRate;

            //transit
            Transporter transporter = currentPlanet.getTransporter();
            if (transporter != null) {
                if (transporter.target != null) {
                    observation[currentIndex + transporter.target] = transporter.payload;
                }
            }

            currentIndex += numPlanets;
        }

        Integer selectedPlanet = ((SourceTargetActuator) gameState.actuators[playerId]).planetSelected;
        if (selectedPlanet != null) {
            observation[currentIndex + selectedPlanet] = 1.0;
        }
        return observation;
    }
}
