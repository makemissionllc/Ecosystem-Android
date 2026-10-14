package org.kde.kdeconnect.plugins.ecosystem;
interface IDisplayPower {
    boolean setScreenOn(boolean on);
    boolean wake();
    boolean rotate();
    boolean launchApp(String packageName);
}
