package kr.local.galaxybattery;

interface IRemoteBattery {
    String readBattery() = 0;
    String readHardware() = 1;
    void destroy() = 16777114;
}
