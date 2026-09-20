package net.runelite.client.plugins.microbot.api.actor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.IEntity;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

@Getter
@RequiredArgsConstructor
public class Rs2ActorModel implements Actor
{

    private final Actor actor;

    @Override
    public WorldView getWorldView()
    {
        return Microbot.getClientThread().invoke(() -> actor == null ? null : actor.getWorldView());
    }

    @Override
    public LocalPoint getCameraFocus() {
        return Microbot.getClientThread().runOnClientThreadOptional(actor::getCameraFocus).orElse(null);
    }

    @Override
    public int getCombatLevel()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(actor::getCombatLevel).orElse(0);
    }

    @Override
    public @Nullable String getName()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(actor::getName).orElse(null);
    }

    @Override
    public boolean isInteracting()
    {
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> actor.isInteracting());
    }

    @Override
    public Actor getInteracting()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(actor::getInteracting).orElse(null);
    }

    @Override
    public int getHealthRatio()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(actor::getHealthRatio).orElse(0);
    }

    @Override
    public int getHealthScale()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(actor::getHealthScale).orElse(0);
    }

    @Override
    public WorldPoint getWorldLocation()
    {
        return Microbot.getClientThread().invoke(() -> {
            if (actor == null) return null;
            WorldView worldView = actor.getWorldView();
            if (worldView != null && !worldView.isTopLevel()) {
                return projectActorLocationToMainWorld();
            }
            return actor.getWorldLocation();
        });
    }

    public WorldPoint getSceneWorldLocation()
    {
        return Microbot.getClientThread().invoke(() -> actor == null ? null : actor.getWorldLocation());
    }

    @Override
    public LocalPoint getLocalLocation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getLocalLocation());
    }

    @Override
    public int getOrientation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getOrientation());
    }

    @Override
    public int getCurrentOrientation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getCurrentOrientation());
    }

    @Override
    public int getAnimation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getAnimation());
    }

    @Override
    public int getPoseAnimation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getPoseAnimation());
    }

    @Override
    public void setPoseAnimation(int animation)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setPoseAnimation(animation);
            return null;
        });
    }

    @Override
    public int getPoseAnimationFrame()
    {
        return Microbot.getClientThread().invoke(() -> actor.getPoseAnimationFrame());
    }

    @Override
    public void setPoseAnimationFrame(int frame)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setPoseAnimationFrame(frame);
            return null;
        });
    }

    @Override
    public int getIdlePoseAnimation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getIdlePoseAnimation());
    }

    @Override
    public void setIdlePoseAnimation(int animation)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setIdlePoseAnimation(animation);
            return null;
        });
    }

    @Override
    public int getIdleRotateLeft()
    {
        return Microbot.getClientThread().invoke(() -> actor.getIdleRotateLeft());
    }

    @Override
    public void setIdleRotateLeft(int animationID)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setIdleRotateLeft(animationID);
            return null;
        });
    }

    @Override
    public int getIdleRotateRight()
    {
        return Microbot.getClientThread().invoke(() -> actor.getIdleRotateRight());
    }

    @Override
    public void setIdleRotateRight(int animationID)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setIdleRotateRight(animationID);
            return null;
        });
    }

    @Override
    public int getWalkAnimation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getWalkAnimation());
    }

    @Override
    public void setWalkAnimation(int animationID)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setWalkAnimation(animationID);
            return null;
        });
    }

    @Override
    public int getWalkRotateLeft()
    {
        return Microbot.getClientThread().invoke(() -> actor.getWalkRotateLeft());
    }

    @Override
    public void setWalkRotateLeft(int animationID)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setWalkRotateLeft(animationID);
            return null;
        });
    }

    @Override
    public int getWalkRotateRight()
    {
        return Microbot.getClientThread().invoke(() -> actor.getWalkRotateRight());
    }

    @Override
    public void setWalkRotateRight(int animationID)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setWalkRotateRight(animationID);
            return null;
        });
    }

    @Override
    public int getWalkRotate180()
    {
        return Microbot.getClientThread().invoke(() -> actor.getWalkRotate180());
    }

    @Override
    public void setWalkRotate180(int animationID)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setWalkRotate180(animationID);
            return null;
        });
    }

    @Override
    public int getRunAnimation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getRunAnimation());
    }

    @Override
    public void setRunAnimation(int animationID)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setRunAnimation(animationID);
            return null;
        });
    }

    @Override
    public void setAnimation(int animation)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setAnimation(animation);
            return null;
        });
    }

    @Override
    public int getAnimationFrame()
    {
        return Microbot.getClientThread().invoke(() -> actor.getAnimationFrame());
    }

    @Override
    public void setActionFrame(int frame)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setAnimationFrame(frame);
            return null;
        });
    }

    @Override
    public void setAnimationFrame(int frame)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setAnimationFrame(frame);
            return null;
        });
    }

    @Override
    public IterableHashTable<ActorSpotAnim> getSpotAnims()
    {
        return Microbot.getClientThread().invoke(() -> actor.getSpotAnims());
    }

    @Override
    public boolean hasSpotAnim(int spotAnimId)
    {
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> actor.hasSpotAnim(spotAnimId));
    }

    @Override
    public void createSpotAnim(int id, int spotAnimId, int height, int delay)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.createSpotAnim(id, spotAnimId, height, delay);
            return null;
        });
    }

    @Override
    public void removeSpotAnim(int id)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.removeSpotAnim(id);
            return null;
        });
    }

    @Override
    public void clearSpotAnims()
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.clearSpotAnims();
            return null;
        });
    }

    @Override
    public int getGraphic()
    {
        return Microbot.getClientThread().invoke(() -> actor.getGraphic());
    }

    @Override
    public void setGraphic(int graphic)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setGraphic(graphic);
            return null;
        });
    }

    @Override
    public int getGraphicHeight()
    {
        return Microbot.getClientThread().invoke(() -> actor.getGraphicHeight());
    }

    @Override
    public void setGraphicHeight(int height)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setGraphicHeight(height);
            return null;
        });
    }

    @Override
    public int getSpotAnimFrame()
    {
        return Microbot.getClientThread().invoke(() -> actor.getSpotAnimFrame());
    }

    @Override
    public void setSpotAnimFrame(int spotAnimFrame)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setSpotAnimFrame(spotAnimFrame);
            return null;
        });
    }

    @Override
    public Polygon getCanvasTilePoly()
    {
        return Microbot.getClientThread().invoke(() -> actor.getCanvasTilePoly());
    }

    @Override
    public @Nullable Point getCanvasTextLocation(Graphics2D graphics, String text, int zOffset)
    {
        return Microbot.getClientThread().invoke(() -> actor.getCanvasTextLocation(graphics, text, zOffset));
    }

    @Override
    public Point getCanvasImageLocation(BufferedImage image, int zOffset)
    {
        return Microbot.getClientThread().invoke(() -> actor.getCanvasImageLocation(image, zOffset));
    }

    @Override
    public Point getCanvasSpriteLocation(SpritePixels sprite, int zOffset)
    {
        return Microbot.getClientThread().invoke(() -> actor.getCanvasSpriteLocation(sprite, zOffset));
    }

    @Override
    public Point getMinimapLocation()
    {
        return Microbot.getClientThread().invoke(() -> actor.getMinimapLocation());
    }

    @Override
    public int getLogicalHeight()
    {
        return Microbot.getClientThread().invoke(() -> actor.getLogicalHeight());
    }

    @Override
    public Shape getConvexHull()
    {
        return Microbot.getClientThread().invoke(() -> actor.getConvexHull());
    }

    @Override
    public WorldArea getWorldArea()
    {
        return Microbot.getClientThread().invoke(() -> actor.getWorldArea());
    }

    @Override
    public String getOverheadText()
    {
        return Microbot.getClientThread().invoke(() -> actor.getOverheadText());
    }

    @Override
    public void setOverheadText(String overheadText)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setOverheadText(overheadText);
            return null;
        });
    }

    @Override
    public int getOverheadCycle()
    {
        return Microbot.getClientThread().invoke(() -> actor.getOverheadCycle());
    }

    @Override
    public void setOverheadCycle(int cycles)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setOverheadCycle(cycles);
            return null;
        });
    }

    @Override
    public boolean isDead()
    {
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> actor.isDead());
    }

    @Override
    public void setDead(boolean dead)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setDead(dead);
            return null;
        });
    }

    @Override
    public int getFootprintSize() {
        return Microbot.getClientThread().invoke(() -> actor.getFootprintSize());
    }

    @Override
    public int getAnimationHeightOffset()
    {
        return Microbot.getClientThread().invoke(() -> actor.getAnimationHeightOffset());
    }

    @Override
    public int getRenderMode() {
        return Microbot.getClientThread().invoke(() -> actor.getRenderMode());
    }

    @Override
    public Model getModel()
    {
        return Microbot.getClientThread().invoke(() -> actor.getModel());
    }

    @Override
    public int getModelHeight()
    {
        return Microbot.getClientThread().invoke(() -> actor.getModelHeight());
    }

    @Override
    public void setModelHeight(int modelHeight)
    {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            actor.setModelHeight(modelHeight);
            return null;
        });
    }

    @Override
    public Node getNext()
    {
        return Microbot.getClientThread().invoke(() -> actor.getNext());
    }

    @Override
    public Node getPrevious()
    {
        return Microbot.getClientThread().invoke(() -> actor.getPrevious());
    }

    @Override
    public long getHash()
    {
        return Microbot.getClientThread().invoke(() -> actor.getHash());
    }

    public WorldPoint projectActorLocationToMainWorld() {
        return Microbot.getClientThread().invoke(this::projectActorLocationOnClientThread);
    }

    private WorldPoint projectActorLocationOnClientThread() {
        if (actor == null) return null;
        WorldPoint actorLocation = actor.getWorldLocation();
        WorldView wv = actor.getWorldView();
        if (wv == null || actorLocation == null) return actorLocation;
        LocalPoint localPoint = LocalPoint.fromWorld(wv, actorLocation);

        if (localPoint == null)
        {
            return actorLocation;
        }

        var mainWorldProjection = wv.getMainWorldProjection();

        if (mainWorldProjection == null)
        {
            return actorLocation;
        }

        float[] projection = mainWorldProjection
                .project(localPoint.getX(), 0, localPoint.getY());

        return WorldPoint.fromLocal(
                Microbot.getClient().getTopLevelWorldView(),
                (int) projection[0],
                (int) projection[2],
                0
        );
    }
}
