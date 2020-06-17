/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.UnsignedDecimalEncodedValue;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import java.util.*;

/**
 * Defines bit layout for bus
 *
 * @author Peter Karich
 * @author Nop
 * @author RouteXL
 */
public class BusFlagEncoder extends AbstractFlagEncoder {
    protected final Map<String, Integer> trackTypeSpeedMap = new HashMap<>();
    protected final Set<String> badSurfaceSpeedMap = new HashSet<>();
    private boolean speedTwoDirections;
    // This value determines the maximal possible on roads with bad surfaces
    protected int badSurfaceSpeed;

    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    protected final Map<String, Integer> defaultSpeedMap = new HashMap<>();

    public BusFlagEncoder() {
        this(5, 5, 0);
    }

    public BusFlagEncoder(PMap properties) {
        this(properties.getInt("speed_bits", 5),
                properties.getDouble("speed_factor", 5),
                properties.getBool("turn_costs", false) ? 1 : 0);

        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
        blockBarriersByDefault(properties.getBool("block_barriers", true));
        setSpeedTwoDirections(properties.getBool("speed_two_directions", false));
    }

    public BusFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts) {
        super(speedBits, speedFactor, maxTurnCosts);
        restrictions.addAll(Arrays.asList("psv","service","busway","bus","trolleybus","motorcar", "motor_vehicle", "vehicle", "access"));
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");

        intendedValues.add("yes");
        intendedValues.add("permissive");
        intendedValues.add("designated");
        intendedValues.add("service");

        potentialBarriers.add("gate");
        potentialBarriers.add("lift_gate");
        potentialBarriers.add("kissing_gate");
        potentialBarriers.add("swing_gate");

        // absoluteBarriers.add("fence"); // Allow       
        absoluteBarriers.add("bollard");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");
        absoluteBarriers.add("cycle_barrier");
        absoluteBarriers.add("motorcycle_barrier");
        absoluteBarriers.add("block");

        badSurfaceSpeedMap.add("cobblestone");
        badSurfaceSpeedMap.add("grass_paver");
        badSurfaceSpeedMap.add("gravel");
        badSurfaceSpeedMap.add("sand");
        badSurfaceSpeedMap.add("paving_stones");
        badSurfaceSpeedMap.add("dirt");
        badSurfaceSpeedMap.add("ground");
        badSurfaceSpeedMap.add("grass");
        badSurfaceSpeedMap.add("unpaved");
        badSurfaceSpeedMap.add("compacted");

        // autobahn
        defaultSpeedMap.put("motorway", 90);
        defaultSpeedMap.put("motorway_link", 60);
        defaultSpeedMap.put("motorroad", 80);
        // bundesstraße
        defaultSpeedMap.put("trunk", 60);
        defaultSpeedMap.put("trunk_link", 55);
        // linking bigger town
        defaultSpeedMap.put("primary", 55);
        defaultSpeedMap.put("primary_link", 50);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 45);
        defaultSpeedMap.put("secondary_link", 35);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 40);
        defaultSpeedMap.put("tertiary_link", 30);
        defaultSpeedMap.put("unclassified", 20);
        defaultSpeedMap.put("residential", 20);
        // spielstraße
        defaultSpeedMap.put("living_street", 5);
        defaultSpeedMap.put("service", 10);
        // unknown road
        defaultSpeedMap.put("road", 10);
        // forestry stuff
        defaultSpeedMap.put("track", 10);
        
        trackTypeSpeedMap.put("grade1", 15); // paved
        trackTypeSpeedMap.put("grade2", 10); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials
        trackTypeSpeedMap.put(null, defaultSpeedMap.get("track"));
        
        // limit speed on bad surfaces to 30 km/h
        badSurfaceSpeed = 25;
        maxPossibleSpeed = 100;
        speedDefault = defaultSpeedMap.get("secondary");
    }
    
    public BusFlagEncoder setSpeedTwoDirections(boolean value) {
        speedTwoDirections = value;
        return this;
    }
    

    public TransportationMode getTransportationMode() {
        return TransportationMode.MOTOR_VEHICLE;
    }
    
    @Override
    public int getVersion() {
        return 2;
    }

    /**
     * Define the place of the speedBits in the edge flags for car.
     */
    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // first two bits are reserved for route handling in superclass
        super.createEncodedValues(registerNewEncodedValue, prefix, index);
        registerNewEncodedValue.add(avgSpeedEnc = new UnsignedDecimalEncodedValue(EncodingManager.getKey(prefix, "average_speed"), speedBits, speedFactor, speedTwoDirections));
    }

    protected double getSpeed(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (!Helper.isEmpty(highwayValue) && way.hasTag("motorroad", "yes")
                && !"motorway".equals(highwayValue) && !"motorway_link".equals(highwayValue)) {
            highwayValue = "motorroad";
        }
        Integer speed = defaultSpeedMap.get(highwayValue);
        if (speed == null)
            throw new IllegalStateException(toString() + ", no speed found for: " + highwayValue + ", tags: " + way);

        if (highwayValue.equals("track")) {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt)) {
                Integer tInt = trackTypeSpeedMap.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        // TODO: Ferries have conditionals, like opening hours or are closed during some time in the year
        String highwayValue = way.getTag("highway");
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                if (restrictedValues.contains(firstValue))
                    return EncodingManager.Access.CAN_SKIP;
                if (intendedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle"))
                    return EncodingManager.Access.FERRY;
            }
            return EncodingManager.Access.CAN_SKIP;
        }

        if ("track".equals(highwayValue) && trackTypeSpeedMap.get(way.getTag("tracktype")) == null)
            return EncodingManager.Access.CAN_SKIP;

        if (!defaultSpeedMap.containsKey(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return EncodingManager.Access.CAN_SKIP;

        // multiple restrictions needs special handling compared to foot and bike, see also motorcycle
        if (!firstValue.isEmpty()) {
            if (restrictedValues.contains(firstValue) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                return EncodingManager.Access.CAN_SKIP;
            if (intendedValues.contains(firstValue))
                return EncodingManager.Access.WAY;
        }

        // do not drive street cars into fords
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return EncodingManager.Access.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return EncodingManager.Access.CAN_SKIP;
        else
            return EncodingManager.Access.WAY;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access accept) {
        if (accept.canSkip())
            return edgeFlags;

        if (!accept.isFerry()) {
            // get assumed speed from highway type
            double speed = getSpeed(way);
            speed = applyMaxSpeed(way, speed);

            speed = applyBadSurfaceSpeed(way, speed);

            setSpeed(false, edgeFlags, speed);
            if (speedTwoDirections)
            	setSpeed(true, edgeFlags, speed);

            boolean isRoundabout = roundaboutEnc.getBool(false, edgeFlags);
            if (isOneway(way) || isRoundabout) {
                if (isForwardOneway(way))
                    accessEnc.setBool(false, edgeFlags, true);
                if (isBackwardOneway(way))
                    accessEnc.setBool(true, edgeFlags, true);
            } else {
                accessEnc.setBool(false, edgeFlags, true);
                accessEnc.setBool(true, edgeFlags, true);
            }

        } else {
            double ferrySpeed = getFerrySpeed(way);
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
            setSpeed(false, edgeFlags, ferrySpeed);
            if (speedTwoDirections)
            	setSpeed(true, edgeFlags, ferrySpeed);
        }

        return edgeFlags;
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isBackwardOneway(ReaderWay way) {
        return way.hasTag("oneway", "-1")
                || way.hasTag("vehicle:forward", "no")
                || way.hasTag("motor_vehicle:forward", "no");
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isForwardOneway(ReaderWay way) {
        return !way.hasTag("oneway", "-1")
                && !way.hasTag("vehicle:forward", "no")
                && !way.hasTag("motor_vehicle:forward", "no");
    }

    protected boolean isOneway(ReaderWay way) {
        return way.hasTag("oneway", oneways)
                || way.hasTag("vehicle:backward")
                || way.hasTag("vehicle:forward")
                || way.hasTag("motor_vehicle:backward")
                || way.hasTag("motor_vehicle:forward");
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed
     */
    protected double applyBadSurfaceSpeed(ReaderWay way, double speed) {
        // limit speed if bad surface
        if (badSurfaceSpeed > 0 && speed > badSurfaceSpeed && way.hasTag("surface", badSurfaceSpeedMap))
            speed = badSurfaceSpeed;
        return speed;
    }

    @Override
    public String toString() {
        return "bus";
    }
}