package com.virtual;

import tel.schich.javacan.*;
import tel.schich.javacan.util.CanBroker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class Main {

    public static final NetworkDevice CAN_INTERFACE = lookupDev();
    public static HashMap<String, Set<String>> ATMAMap = new HashMap<>();

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

        CanBroker canBroker = null;
        try {
            canBroker = new CanBroker(Executors.defaultThreadFactory(), Duration.ofMillis(10));
            canBroker.addDevice(CAN_INTERFACE, (ch, frame) -> {
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
            });
            canBroker.addFilter(CanFilter.ANY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Disconnect vcan0");
    }
}
