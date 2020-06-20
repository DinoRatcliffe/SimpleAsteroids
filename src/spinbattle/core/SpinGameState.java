package spinbattle.core;

import ggi.core.AbstractGameState;
import logger.sample.DefaultLogger;
import math.Vector2d;
import spinbattle.actuator.Actuator;
import spinbattle.params.Constants;
import spinbattle.params.SpinBattleParams;
import spinbattle.util.MovableObject;

import com.google.protobuf.Int32Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class SpinGameState implements AbstractGameState {

    public int playerFirst = 0;

    // this tracks all calls to the next method
    // useful for calculating overall stats

    public static int totalTicks = 0;
    public static int totalInstances = 0;

    public SpinGameState() {
        totalInstances++;
    }

    // number of ticks made by this instance
    public int nTicks;

    // stored current score that can be serialized
    public double currentScore;

    // may set a limit on the game length
    // this will be used in the isTerminal() method
    public SpinBattleParams params;

    public ArrayList<Planet> planets;
    public ArrayList<Integer> planetIndicies;
    public ProximityMap proximityMap;
    public VectorField vectorField;
    public DefaultLogger logger;
    static int nPlayers = 2;
    public ArrayList<Integer> playerIndicies;
    public Actuator[] actuators = new Actuator[nPlayers];

    public SpinGameState setLogger(DefaultLogger logger) {
        this.logger = logger;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpinGameState other = (SpinGameState) obj;
        boolean allPlanetsEqual = true;
        for (int i = 0; i < planets.size(); i++) {
            if (!planets.get(i).equals(other.planets.get(i))) {
                allPlanetsEqual = false;
            }
        }

        System.out.println("TESTING EQUAL");
        System.out.println(playerFirst == other.playerFirst);
        System.out.println(totalTicks == other.totalTicks);
        System.out.println(nTicks == other.nTicks);
        System.out.println(currentScore == other.currentScore);
        System.out.println(params.equals(other.params));
        System.out.println(allPlanetsEqual);
        System.out.println("DONE TESTING EQUAL");

        return playerFirst == other.playerFirst &&
                totalTicks == other.totalTicks &&
                nTicks == other.nTicks &&
                currentScore == other.currentScore &&
                params.equals(other.params) &&
                allPlanetsEqual;
    }

    @Override
    public AbstractGameState copy() {
        SpinGameState copy = new SpinGameState();
        // just shallow-copy the params
        copy.params = params;
        copy.nTicks = nTicks;
        copy.currentScore = currentScore;
        // deep copy the planets
        copy.planets = new ArrayList<>();
        copy.planetIndicies = planetIndicies;
        copy.playerIndicies = playerIndicies;
        for (Planet p : planets) {
            copy.planets.add(p.copy());
        }
        // actuators = new Actuator[nPlayers];
        for (int i=0; i<nPlayers; i++) {
            if (actuators[i] != null) copy.actuators[i] = actuators[i].copy();
        }
        // shallow copy the proximity map (which may even be null)
        copy.proximityMap = proximityMap;
        copy.vectorField = vectorField;

        // do NOT copy the logger - this is only used in the "real" game by default
        return copy;
    }

    @Override
    public AbstractGameState next(int[] actions) {
        // System.out.println(Arrays.toString(actions));

        //Collections.shuffle(playerIndicies);

        for (int i : playerIndicies) {
            actuators[i].actuate(actions[i], this);
        }

        //Collections.shuffle(planetIndicies);
        for (int i : planetIndicies) {
            planets.get(i).update(this);
        }
        nTicks++;
        totalTicks++;
        currentScore = getScore();
        return this;
    }

    @Override
    public int nActions() {
        // this may depend on the actuator model, which could be different for each player
        // or could be different anyway for an asymmetric game

        // for now just do something very simple ...
        // but MUST be changed in future
        return planets.size() * planets.size() - planets.size();
    }

    @Override
    public double getScore() {
        if (params.clampZeroScore) return 0;

        double score = 0;
        for (Planet p : planets) {
            score += p.getScore();
        }
        // but it the game is over, add in an early completion bonus
        Integer singleOwner = singleOwner();
        if (singleOwner != null) {
            double tot = 0;
            for (Planet p : planets) tot += p.growthRate;
            double bonus = tot * (params.maxTicks - nTicks);
            // System.out.println("Awarding bonus: " + bonus);
            score += (singleOwner == Constants.playerOne) ? bonus : -bonus;
        }
        return playerFirst == 0 ? score : -score;
    }

    public double getPlayerShips(int playerId) {
        double ships = 0;
        for (Planet p : planets) {
            if (p.ownedBy == playerId) ships += p.shipCount;
        }
        return ships;
    }

    @Override
    public boolean isTerminal() {
        return nTicks > params.maxTicks || singleOwner() != null;
    }

    // if only one player owns planets then the game is over
    public Integer singleOwner() {
        boolean playerOne = false;
        boolean playerTwo = false;

        for (Planet p : planets) {
            playerOne |= p.ownedBy == Constants.playerOne;
            playerTwo |= p.ownedBy == Constants.playerTwo;
            if (playerOne && playerTwo) return null;
        }

        // System.out.println(playerOne + " : " + playerTwo);
        if (playerOne) return Constants.playerOne;
        if (playerTwo) return Constants.playerTwo;
        return null;
    }

    public SpinGameState setParams(SpinBattleParams params) {
        this.params = params;
        // set all the planet params also
        if (planets != null) {
            for (Planet p : planets) {
                p.setParams(params);
            }
        }
        return this;
    }

    static int maxTries = 1000;

    public SpinGameState setPlanets() {
        SpinGameState newState;
        if (params.symmetricMaps) {
            newState = setSymmetricPlanets();
        } else {
            newState = setRandomPlanets();
        }
        newState.planetIndicies = new ArrayList<Integer>();
        for (int i = 0; i<planets.size(); i++) {
            newState.planetIndicies.add(i);
        }

        newState.playerIndicies = new ArrayList<Integer>();
        for (int i = 0; i<nPlayers; i++) {
            newState.playerIndicies.add(i);
        }

        return newState;
    }

    public SpinGameState setRandomPlanets() {
        planets = new ArrayList<>();
        int i=0;
        int whichEven = params.getRandom().nextInt(2);
        // int nToAllocate = params.nPlanets - params.nNeutral;
        while (planets.size() < params.nToAllocate) {
            int owner = (planets.size() % 2 == whichEven ? Constants.playerOne : Constants.playerTwo);
            Planet planet = makePlanet(owner);
            // System.out.println("Made planet for: " + owner + " ... size: " + planets.size());
            planet.growthRate = params.maxGrowth;
            if (valid(planet)) {
                planet.setIndex(planets.size());
                planets.add(planet);
                // System.out.println("Added planet for: " + owner);
            } else {
                // System.out.println("Failed to add planet for: " + owner);
            }
            // System.out.println();
        }

        // System.out.println("To allocate: " + nToAllocate + " : " + planets.size());

        // set the neutral ones
        while (planets.size() < params.nPlanets && i++ < maxTries) {
            Planet planet = makePlanet(Constants.neutralPlayer);
            if (valid(planet)) {
                planet.setIndex(planets.size());
                planets.add(planet);
            }
        }
        // System.out.println(planets);
        if (params.useProximityMap) {
            proximityMap = new ProximityMap().setPlanets(this);
        }
        if (params.useVectorField) {
            vectorField = new VectorField().setParams(params).setField(this);
            // System.out.println("Set VF: " + vectorField);
        }

        currentScore = getScore();
        return this;
    }

    public SpinGameState setSymmetricPlanets() {
        planets = new ArrayList<>();
        int i=0;
        int whichEven = params.getRandom().nextInt(2);
        // int nToAllocate = params.nPlanets - params.nNeutral;
        while (planets.size() < params.nToAllocate / 2) {
            int owner = (i % 2 == whichEven ? Constants.playerOne : Constants.playerTwo);
            Planet planet = makePlanet(owner);
            // System.out.println("Made planet for: " + owner + " ... size: " + planets.size());
            planet.growthRate = params.maxGrowth;
            if (valid_half(planet)) {
                planet.setIndex(planets.size());
                planets.add(planet);

                Planet newPlanet = planet.copy();
                newPlanet.ownedBy = planet.ownedBy == Constants.playerOne ? Constants.playerTwo : Constants.playerOne;
                newPlanet.position.set(params.width - newPlanet.position.x, params.height - newPlanet.position.y);
                newPlanet.setIndex(planets.size());
                planets.add(newPlanet);
                i++;
                // System.out.println("Added planet for: " + owner);
            } else {
                // System.out.println("Failed to add planet for: " + owner);
            }
            // System.out.println();
        }
        // System.out.println("To allocate: " + nToAllocate + " : " + planets.size());

        // set the neutral ones
        while (planets.size() < params.nPlanets && i++ < maxTries) {
            Planet planet = makePlanet(Constants.neutralPlayer);
            if (valid_half(planet)) {
                planet.setIndex(planets.size());
                planets.add(planet);

                // do mirror planet
                Planet newPlanet = planet.copy();
                newPlanet.position.set(params.width - newPlanet.position.x, params.height - newPlanet.position.y);
                newPlanet.setIndex(planets.size());
                planets.add(newPlanet);
            }
        }
        // System.out.println(planets);
        if (params.useProximityMap) {
            proximityMap = new ProximityMap().setPlanets(this);
        }
        if (params.useVectorField) {
            vectorField = new VectorField().setParams(params).setField(this);
            // System.out.println("Set VF: " + vectorField);
        }

        currentScore = getScore();
        return this;
    }

    Planet makePlanet(int owner) {
        Planet planet =new Planet().setParams(params).
                setRandomLocation(params).setOwnership(Constants.neutralPlayer);
        planet.setRandomGrowthRate();
        planet.setOwnership(owner);
        return planet;
    }

    boolean valid(Planet p) {
        double minX = Math.min(p.position.x, params.width - p.position.x);
        double minY = Math.min(p.position.y, params.height - p.position.y);
        // test whether planet is too close to border
        if (Math.min(minX, minY) < p.getRadius() * params.radSep) {
            // System.out.println("Failed border sep:" + minX +  " : " + minY);
            return false;
        }

        // now check proximity to each of the existing ones

        for (Planet x : planets) {
            double sep = x.position.dist(p.position);
            if (sep < params.radSep * (x.getRadius() + p.getRadius())) {
                // System.out.println("Failed planet proximity: " + (int) sep);
                return false;
            }
        }
        return true;

    }

    boolean valid_half(Planet p) {
        double minX = Math.min(p.position.x, params.width/2 - p.position.x);
        double minY = Math.min(p.position.y, params.height - p.position.y);
        // test whether planet is too close to border
        if (Math.min(minX, minY) < p.getRadius() * params.radSep) {
            // System.out.println("Failed border sep:" + minX +  " : " + minY);
            return false;
        }

        // now check proximity to each of the existing ones

        for (Planet x : planets) {
            double sep = x.position.dist(p.position);
            if (sep < params.radSep * (x.getRadius() + p.getRadius())) {
                // System.out.println("Failed planet proximity: " + (int) sep);
                return false;
            }
        }
        return true;

    }

    public void notifyLaunch(Transporter transit) {
        if (logger != null) {
            // logger.logEvent(new LaunchEvent());
            // System.out.println(transit);
        }
    }

    public void notifySelection(Planet source) {
        if (logger != null) {
            // logger.logEvent(new SelectPlanetEvent());
            // System.out.println(source);
        }
    }
    // todo - set up the planets based on the params that have been passed

    public static SpinGameState byteToState(byte[] message) throws Exception {
        SpinGameStateProtos.SpinGameState gs = SpinGameStateProtos.SpinGameState.parseFrom(message);
        SpinGameState newGs = new SpinGameState();

        // set Planets
        ArrayList<Planet> planets = new ArrayList<Planet>();
        for (SpinGameStateProtos.Planet p : gs.getPlanetsList()) {
            Planet newPlanet = new Planet();
            newPlanet.position = new Vector2d(p.getX(), p.getY());
            newPlanet.index = p.getIndex();
            newPlanet.growthRate = p.getGrowthRate();
            newPlanet.shipCount = p.getShipCount();
            newPlanet.ownedBy = p.getOwnedBy();

            SpinGameStateProtos.Transporter t = p.getTransit();
            if (!t.getDefaultInstanceForType().equals(t)) {
                Transporter newTransporter = new Transporter();
                newTransporter.parent = t.getParent();
                newTransporter.target = t.getTarget().getValue();
                newTransporter.ownedBy = t.getOwnedBy();
                newTransporter.payload = t.getPayload();

                MovableObject mo = new MovableObject();
                mo.s = new Vector2d(t.getMosx(), t.getMosy());
                mo.v = new Vector2d(t.getMovx(), t.getMovy());
                newTransporter.mo = mo;

                newPlanet.transit = newTransporter;
            }
            planets.add(newPlanet);
        }

        // set other
        newGs.playerFirst = gs.getPlayerFirst();
        newGs.totalTicks = gs.getTotalTicks();
        newGs.nTicks = gs.getNTicks();
        newGs.currentScore = gs.getCurrentScore();
        newGs.planets = planets;

        return newGs;
    }

    public static byte[] toByteArray(SpinGameState state) {
        SpinGameStateProtos.Params.Builder paramsBuilder = SpinGameStateProtos.Params.newBuilder();
        paramsBuilder.setMaxTicks(state.params.maxTicks);
        paramsBuilder.setWidth(state.params.width);
        paramsBuilder.setHeight(state.params.height);

        SpinGameStateProtos.SpinGameState.Builder gsBuilder = SpinGameStateProtos.SpinGameState.newBuilder();
        gsBuilder.setPlayerFirst(state.playerFirst)
                .setTotalTicks(state.totalTicks)
                .setNTicks(state.nTicks)
                .setCurrentScore(state.currentScore)
                .setParams(paramsBuilder);

        SpinGameStateProtos.Planet.Builder planetBuilder;
        SpinGameStateProtos.Transporter.Builder transporterBuilder;
        Transporter trans;
        for (Planet p : state.planets) {
            planetBuilder = SpinGameStateProtos.Planet.newBuilder();
            planetBuilder.setX(p.position.x)
                    .setY(p.position.y)
                    .setIndex(p.index)
                    .setGrowthRate(p.growthRate)
                    .setShipCount(p.shipCount)
                    .setOwnedBy(p.ownedBy);

            trans = p.transit;
            if (trans != null && trans.target != null) {
                transporterBuilder = SpinGameStateProtos.Transporter.newBuilder();
                transporterBuilder.setMosx(trans.mo.s.x)
                        .setMosy(trans.mo.s.y)
                        .setMovx(trans.mo.v.x)
                        .setMovy(trans.mo.v.y)
                        .setTarget(Int32Value.newBuilder().setValue(trans.target))
                        .setParent(trans.parent)
                        .setOwnedBy(trans.ownedBy)
                        .setPayload(trans.payload);
                planetBuilder.setTransit(transporterBuilder);
            }

            gsBuilder.addPlanets(planetBuilder);
        }
        return gsBuilder.build().toByteArray();
    }
}
