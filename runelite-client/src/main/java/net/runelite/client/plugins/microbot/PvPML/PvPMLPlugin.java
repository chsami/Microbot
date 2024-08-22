package net.runelite.client.plugins.microbot.PvPML;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.inject.Inject;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = "PvP ML Plugin",
        description = "Integrates a Machine Learning model for PvP in OSRS",
        tags = {"pvp", "machine learning", "automation"}
)
public class PvPMLPlugin extends Plugin
{
    private static final Logger logger = LoggerFactory.getLogger(PvPMLPlugin.class);
    @Getter
    private boolean modelRunning = false;

    @Inject
    private PvPMLOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Override
    protected void startUp() throws Exception
    {
        runModel();
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
        modelRunning = false;
    }

    private void runModel()
    {
        try
        {
            changeDirectory();
            activateCondaEnvironment();
            runEvaluationModel();
            modelRunning = true;
        }
        catch (Exception e)
        {
            logger.error("Error running the ML model", e);
        }
    }

    private void changeDirectory() throws Exception
    {
        ProcessBuilder cdBuilder = new ProcessBuilder("cmd.exe", "/c", "cd", "Documents\\GitHub\\osrs-pvp-reinforcement-learning\\pvp-ml");
        cdBuilder.redirectErrorStream(true);
        Process cdProcess = cdBuilder.start();
        cdProcess.waitFor();
    }

    private void activateCondaEnvironment() throws Exception
    {
        ProcessBuilder condaBuilder = new ProcessBuilder("cmd.exe", "/c", "conda activate ./env");
        condaBuilder.redirectErrorStream(true);
        Process condaProcess = condaBuilder.start();
        condaProcess.waitFor();
    }

    private void runEvaluationModel() throws Exception
    {
        ProcessBuilder evalBuilder = new ProcessBuilder("cmd.exe", "/c", "eval --model-path models/FineTunedNh.zip");
        evalBuilder.redirectErrorStream(true);
        Process evalProcess = evalBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(evalProcess.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null)
        {
            logger.info(line);
        }

        evalProcess.waitFor();
    }

}
