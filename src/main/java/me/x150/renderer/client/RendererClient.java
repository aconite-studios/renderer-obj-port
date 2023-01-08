package me.x150.renderer.client;

import me.x150.MessageSubscription;
import me.x150.renderer.event.Event;
import me.x150.renderer.event.Events;
import me.x150.renderer.event.RenderEvent;
import me.x150.renderer.font.FontRenderer;
import me.x150.renderer.render.Renderer2d;
import me.x150.renderer.render.Renderer3d;
import me.x150.renderer.util.RendererUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.apache.commons.lang3.RandomStringUtils;

import java.awt.Color;
import java.awt.Font;

@Environment(EnvType.CLIENT)
public class RendererClient implements ClientModInitializer {
    FontRenderer fr;
    @Override
    public void onInitializeClient() {
//        Events.manager.registerSubscribers(this); // Testing
    }

    @MessageSubscription
    void onHud(RenderEvent.Hud hud) {
        if (hud.getShift() != Event.Shift.POST) return;
        if (fr == null) fr = new FontRenderer(new Font[] {
            new Font("JetBrains Mono", Font.PLAIN, 40),
            new Font("Comfortaa", Font.PLAIN, 40),
        }, 9);
//        String st = "Hello §aworld§r :) abcdefg 12345 !\"§$%" + RandomStringUtils.randomNumeric(10);
//        float stringWidth = fr.getStringWidth(st);
//        float fontHeight = fr.getStringHeight(st);
//        Renderer2d.renderRoundedQuad(hud.getMatrixStack(), Color.BLACK, 5, 5, 15 + stringWidth, 15 + fontHeight, 5, 10);
//        fr.drawString(hud.getMatrixStack(), st, 10, 10, 1 , 0, 0, 1);
        MinecraftClient client = MinecraftClient.getInstance();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            Vec3d vec3d = RendererUtils.worldSpaceToScreenSpace(entity.getPos().add(0, entity.getHeight()/2d, 0));
                        if (RendererUtils.screenSpaceCoordinateIsVisible(vec3d)) {
                            String simpleName = entity.getClass().getSimpleName();
                            fr.drawCenteredString(RendererUtils.getEmptyMatrixStack(), simpleName, (float) vec3d.x, (float) vec3d.y - fr.getStringHeight(simpleName)/2f, 1f, 1f, 1f, 1f);
//            Renderer2d.renderRoundedQuad(RendererUtils.getEmptyMatrixStack(), new Color(255, 0, 0, 100), vec3d.x-10, vec3d.y-10, vec3d.x+10, vec3d.y+10, 5, 10);
                        }
        }
    }

    @MessageSubscription
    void onWorld(RenderEvent.World wor) {
        if (wor.getShift() != Event.Shift.POST) return;
        Renderer3d.renderThroughWalls();
//        Window window = MinecraftClient.getInstance().getWindow();
        Mouse mouse = MinecraftClient.getInstance().mouse;
        Integer value = MinecraftClient.getInstance().options.getGuiScale().getValue();
        Vec3d near = RendererUtils.screenSpaceToWorldSpace(mouse.getX()/value, mouse.getY()/value, 0);
        Vec3d far = RendererUtils.screenSpaceToWorldSpace(mouse.getX()/value, mouse.getY()/value, 1);
        if (near == null || far == null) return;
        assert MinecraftClient.getInstance().player != null;
        RaycastContext rc = new RaycastContext(near, far, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.ANY, MinecraftClient.getInstance().player);
        BlockHitResult raycast = MinecraftClient.getInstance().world.raycast(rc);
        Vec3d pos = raycast.getPos();
//        Renderer3d.renderLine(wor.getMatrixStack(), Color.RED, near, far);
//        Renderer3d.renderEdged(wor.getMatrixStack(), Color.BLUE, Color.RED, pos.subtract(.5, .5, .5), new Vec3d(1,1,1));
        Renderer3d.renderFadingBlock(Color.BLUE, Color.RED, pos.subtract(.5, .5, .5), new Vec3d(1,1,1), 500);
        Renderer3d.renderFadingBlocks(wor.getMatrixStack());
        Renderer3d.stopRenderThroughWalls();
        Renderer3d.renderFilled(wor.getMatrixStack(), Color.RED, Vec3d.ZERO, new Vec3d(1, 1, 1));
    }
}