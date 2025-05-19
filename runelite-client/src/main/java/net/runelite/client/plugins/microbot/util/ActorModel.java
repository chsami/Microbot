package net.runelite.client.plugins.microbot.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

@Getter
@RequiredArgsConstructor
public class ActorModel implements Actor {

    private final Actor actor;

    @Override
    public WorldView getWorldView() {
        return actor.getWorldView();
    }

    @Override
    public int getCombatLevel() {
        return Microbot.getClientThread().runOnClientThreadOptional(actor::getCombatLevel).orElse(0);
    }

    @Override
    public @Nullable String getName() {
        return Microbot.getClientThread().runOnClientThreadOptional(actor::getName).orElse(null);
    }

    @Override
    public boolean isInteracting() {
        return actor.isInteracting();
    }

    @Override
    public Actor getInteracting() {
        return actor.getInteracting();
    }

    @Override
    public int getHealthRatio() {
        return actor.getHealthRatio();
    }

    @Override
    public int getHealthScale() {
        return actor.getHealthScale();
    }

    @Override
    public WorldPoint getWorldLocation() {
        return actor.getWorldLocation();
    }

    @Override
    public LocalPoint getLocalLocation() {
        return actor.getLocalLocation();
    }

    @Override
    public int getOrientation() {
        return actor.getOrientation();
    }

    @Override
    public int getCurrentOrientation() {
        return actor.getCurrentOrientation();
    }

    @Override
    public int getAnimation() {
        return actor.getAnimation();
    }

    @Override
    public int getPoseAnimation() {
        return actor.getPoseAnimation();
    }

    @Override
    public void setPoseAnimation(int animation) {
        actor.setPoseAnimation(animation);
    }

    @Override
    public int getPoseAnimationFrame() {
        return actor.getPoseAnimationFrame();
    }

    @Override
    public void setPoseAnimationFrame(int frame) {
        actor.setPoseAnimationFrame(frame);
    }

    @Override
    public int getIdlePoseAnimation() {
        return actor.getIdlePoseAnimation();
    }

    @Override
    public void setIdlePoseAnimation(int animation) {
        actor.setIdlePoseAnimation(animation);
    }

    @Override
    public int getIdleRotateLeft() {
        return actor.getIdleRotateLeft();
    }

    @Override
    public void setIdleRotateLeft(int animationID) {
        actor.setIdleRotateLeft(animationID);
    }

    @Override
    public int getIdleRotateRight() {
        return actor.getIdleRotateRight();
    }

    @Override
    public void setIdleRotateRight(int animationID) {
        actor.setIdleRotateRight(animationID);
    }

    @Override
    public int getWalkAnimation() {
        return actor.getWalkAnimation();
    }

    @Override
    public void setWalkAnimation(int animationID) {
        actor.setWalkAnimation(animationID);
    }

    @Override
    public int getWalkRotateLeft() {
        return actor.getWalkRotateLeft();
    }

    @Override
    public void setWalkRotateLeft(int animationID) {
        actor.setWalkRotateLeft(animationID);
    }

    @Override
    public int getWalkRotateRight() {
        return actor.getWalkRotateRight();
    }

    @Override
    public void setWalkRotateRight(int animationID) {
        actor.setWalkRotateRight(animationID);
    }

    @Override
    public int getWalkRotate180() {
        return actor.getWalkRotate180();
    }

    @Override
    public void setWalkRotate180(int animationID) {
        actor.setWalkRotate180(animationID);
    }

    @Override
    public int getRunAnimation() {
        return actor.getRunAnimation();
    }

    @Override
    public void setRunAnimation(int animationID) {
        actor.setRunAnimation(animationID);
    }

    @Override
    public void setAnimation(int animation) {
        actor.setAnimation(animation);
    }

    @Override
    public int getAnimationFrame() {
        return actor.getAnimationFrame();
    }

    @Override
    public void setActionFrame(int frame) {
        actor.setActionFrame(frame);
    }

    @Override
    public void setAnimationFrame(int frame) {
        actor.setAnimationFrame(frame);
    }

    @Override
    public IterableHashTable<ActorSpotAnim> getSpotAnims() {
        return actor.getSpotAnims();
    }

    @Override
    public boolean hasSpotAnim(int spotAnimId) {
        return actor.hasSpotAnim(spotAnimId);
    }

    @Override
    public void createSpotAnim(int id, int spotAnimId, int height, int delay) {
        actor.createSpotAnim(id, spotAnimId, height, delay);
    }

    @Override
    public void removeSpotAnim(int id) {
        actor.removeSpotAnim(id);
    }

    @Override
    public void clearSpotAnims() {
        actor.clearSpotAnims();
    }

    @Override
    public int getGraphic() {
        return actor.getGraphic();
    }

    @Override
    public void setGraphic(int graphic) {
        actor.setGraphic(graphic);
    }

    @Override
    public int getGraphicHeight() {
        return actor.getGraphicHeight();
    }

    @Override
    public void setGraphicHeight(int height) {
        actor.setGraphicHeight(height);
    }

    @Override
    public int getSpotAnimFrame() {
        return actor.getSpotAnimFrame();
    }

    @Override
    public void setSpotAnimFrame(int spotAnimFrame) {
        actor.setSpotAnimFrame(spotAnimFrame);
    }

    @Override
    public Polygon getCanvasTilePoly() {
        return actor.getCanvasTilePoly();
    }

    @Override
    public @Nullable Point getCanvasTextLocation(Graphics2D graphics, String text, int zOffset) {
        return actor.getCanvasTextLocation(graphics, text, zOffset);
    }

    @Override
    public Point getCanvasImageLocation(BufferedImage image, int zOffset) {
        return actor.getCanvasImageLocation(image, zOffset);
    }

    @Override
    public Point getCanvasSpriteLocation(SpritePixels sprite, int zOffset) {
        return actor.getCanvasSpriteLocation(sprite, zOffset);
    }

    @Override
    public Point getMinimapLocation() {
        return actor.getMinimapLocation();
    }

    @Override
    public int getLogicalHeight() {
        return actor.getLogicalHeight();
    }

    @Override
    public Shape getConvexHull() {
        return actor.getConvexHull();
    }

    @Override
    public WorldArea getWorldArea() {
        return actor.getWorldArea();
    }

    @Override
    public String getOverheadText() {
        return actor.getOverheadText();
    }

    @Override
    public void setOverheadText(String overheadText) {
        actor.setOverheadText(overheadText);
    }

    @Override
    public int getOverheadCycle() {
        return actor.getOverheadCycle();
    }

    @Override
    public void setOverheadCycle(int cycles) {
        actor.setOverheadCycle(cycles);
    }

    @Override
    public boolean isDead() {
        return actor.isDead();
    }

    @Override
    public void setDead(boolean dead) {
        actor.setDead(dead);
    }

    @Override
    public Model getModel() {
        return actor.getModel();
    }

    @Override
    public int getModelHeight() {
        return actor.getModelHeight();
    }

    @Override
    public void setModelHeight(int modelHeight) {
        actor.setModelHeight(modelHeight);
    }

    @Override
    public Node getNext() {
        return actor.getNext();
    }

    @Override
    public Node getPrevious() {
        return actor.getPrevious();
    }

    @Override
    public long getHash() {
        return actor.getHash();
    }
}
