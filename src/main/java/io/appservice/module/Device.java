package io.appservice.module;


public interface Device {

    class Info {
        public String version;
        public String sn;
        public String model;
        public String cpu;
        public String device;
        public String manufacturer;
        public String release;
        public String sdk;
        public String hw;
        public String android_id;
    }

    Info getDeviceInfo();

    String getCustomerId();

    String getDeviceId();

    String getPackageName();

    String getCarrier();
}