package com.virtual;

import tel.schich.javacan.*;
import tel.schich.javacan.util.CanBroker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class Main {

    public static final NetworkDevice CAN_INTERFACE = lookupDev();
    public static HashMap<String, Set<String>> ATMAMap = new HashMap<>();
    public static ArrayList<String> ATMATrace = new ArrayList<>();
    public static ArrayList<String> currentIDs = new ArrayList<>();
    private static int counter = 0;
    private static int threshold = 10;
    public static boolean profileMatrix[][] = null;
    public static boolean trainingMode = false;
    public static boolean IDSMode = false;

    private static NetworkDevice lookupDev() {
        try {
            return NetworkDevice.lookup("vcan0");
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println("Connect vcan0");

        trainingMode = true;

        CanBroker canBroker = null;
        try {
            canBroker = new CanBroker(Executors.defaultThreadFactory(), Duration.ofMillis(10));
            canBroker.addDevice(CAN_INTERFACE, (ch, frame) -> {
                currentIDs = new ArrayList<>();

                String frame_all = frame.toString();
                //System.out.println(frame_all);
                String[] frame_elements = frame_all.split(",", 4);
                String frame_id = frame_elements[0];
                String frame_data = frame_elements[3];
                frame_id = frame_id.split("=")[1];
                frame_data = frame_data.split("=")[1];
                frame_data = frame_data.replaceAll("[\\[\\],)]", "");
                System.out.println("frame_id: " + frame_id + ", frame_data: " + frame_data);

                Set<String> data;
                if (ATMAMap.containsKey(frame_id)) {
                    data = ATMAMap.get(frame_id);
                } else {
                    data = new HashSet<>();
                }
                data.add(frame_data);
                ATMAMap.put(frame_id, data);

                System.out.println("ATMAMap");
                System.out.println(ATMAMap);

                ATMATrace.add(frame_id);
                System.out.println("in main() -- ATMATrace");
                System.out.println(ATMATrace);

                currentIDs.add(frame_id);
                System.out.println("in main() -- currentIDs");
                System.out.println(currentIDs);

                counter++;
                System.out.println("Counter: " + counter);

                if (trainingMode && counter >= threshold) {
                    createMatrix();
                }
                if (IDSMode) {
                    idsDetect();
                }
            });
            canBroker.addFilter(CanFilter.ANY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Disconnect vcan0");
    }

    public static void createMatrix() {
        // Create the matrix/profile for this vehicle, which enables the IDS to function

        System.out.println("in createMatrix() -- ATMATrace");
        System.out.println(ATMATrace);

        profileMatrix = new boolean[6][6];
        profileMatrix[0][0] = true;
        profileMatrix[3][3] = true;

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        trainingMode = false;
    }

    public static void idsDetect() {
        // Use the matrix/profile to check current traffic,
        // update false positives,
        // and raise alerts

        if (currentIDs.isEmpty()) {
            // No data received; no alert
            return;
        }

        if (false) {
            System.out.println("***** ALERT! Suspicious traffic detected! *****");
        }
    }
}
