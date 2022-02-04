package com.virtual;

import tel.schich.javacan.*;
import tel.schich.javacan.util.CanBroker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.Executors;

public class Main {

    public static final NetworkDevice CAN_INTERFACE = lookupDev();

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
            });
            canBroker.addFilter(CanFilter.ANY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Disconnect vcan0");
    }
}
