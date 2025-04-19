package net.runelite.client.plugins.microbot.qualityoflife.scripts.bank;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.Login;

import java.util.concurrent.TimeUnit;

public class BankpinScript extends Script {
    public boolean run(QoLConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.useBankPin()) return;
                if ((Login.activeProfile.getBankPin() == null || Login.activeProfile.getBankPin().isEmpty()) || Login.activeProfile.getBankPin().equalsIgnoreCase("**bankpin**")) return;

                Rs2Bank.handleBankPin(Encryption.decrypt(Login.activeProfile.getBankPin()));

            } catch(Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
}
