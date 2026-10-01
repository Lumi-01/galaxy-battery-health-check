package kr.local.galaxybattery;

interface IRemoteBattery {
    String readBattery() = 0;
    String readHardware() = 1;
    String readThermal() = 2;
    void destroy() = 16777114;
}
