package me.x150.renderer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import me.x150.renderer.util.AlphaOverride;
import me.x150.renderer.util.RendererUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Renderer3d {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    static final List<FadingBlock> fades = new CopyOnWriteArrayList<>();
    private static boolean renderThroughWalls = false;

    public static void renderThroughWalls() {
        renderThroughWalls = true;
    }

    public static void stopRenderThroughWalls() {
        renderThroughWalls = false;
    }

    public static boolean rendersThroughWalls() {
        return renderThroughWalls;
    }

    public static void setupRender() {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(renderThroughWalls ? GL11.GL_ALWAYS : GL11.GL_LEQUAL);
    }

    public static void endRender() {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static float transformColor(float f) {
        return AlphaOverride.compute(f);
    }

    public static void renderFadingBlock(Color outlineColor, Color fillColor, Vec3d start, Vec3d dimensions, long lifeTimeMs) {
        FadingBlock fb = new FadingBlock(outlineColor, fillColor, start, dimensions, System.currentTimeMillis(), lifeTimeMs);

        fades.removeIf(fadingBlock -> fadingBlock.start.equals(start) && fadingBlock.dimensions.equals(dimensions));
        fades.add(fb);
    }

    public static void renderFadingBlocks(MatrixStack stack) {
        fades.removeIf(FadingBlock::isDead);
        for (FadingBlock fade : fades) {
            if (fade == null) {
                continue;
            }
            long lifetimeLeft = fade.getLifeTimeLeft();
            double progress = lifetimeLeft / (double) fade.lifeTime;
            progress = MathHelper.clamp(progress, 0, 1);
            double ip = 1 - progress;
            //                stack.push();
            Color out = modifyColor(fade.outline, -1, -1, -1, (int) (fade.outline.getAlpha() * progress));
            Color fill = modifyColor(fade.fill, -1, -1, -1, (int) (fade.fill.getAlpha() * progress));
            renderEdged(stack, fill, out, fade.start.add(new Vec3d(0.2, 0.2, 0.2).multiply(ip)), fade.dimensions.subtract(new Vec3d(.4, .4, .4).multiply(ip)));
            //                stack.pop();
        }
    }

    static Vec3d transformVec3d(Vec3d in) {
        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();
        return in.subtract(camPos);
    }

    static float[] getColor(Color c) {
        return new float[] { c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, transformColor(c.getAlpha() / 255f) };
    }

    private static void useBuffer(VertexFormat.DrawMode mode, VertexFormat format, Supplier<ShaderProgram> shader, Consumer<BufferBuilder> runner) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();

        bb.begin(mode, format);

        runner.accept(bb);

        setupRender();
        RenderSystem.setShader(shader);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        endRender();
    }

    public static void renderQuad(MatrixStack stack, Color color, Vec3d tl, Vec3d tr, Vec3d bl, Vec3d br) {
        Matrix4f m = stack.peek().getPositionMatrix();
        float red = color.getRed() / 255f;
        float green = color.getGreen() / 255f;
        float blue = color.getBlue() / 255f;
        float alpha = transformColor(color.getAlpha() / 255f);
        Vec3d ftl = transformVec3d(tl);
        Vec3d ftr = transformVec3d(tr);
        Vec3d fbl = transformVec3d(bl);
        Vec3d fbr = transformVec3d(br);
        useBuffer(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR, GameRenderer::getPositionColorProgram, bufferBuilder -> {
            bufferBuilder.vertex(m, (float) ftl.x, (float) ftl.y, (float) ftl.z).color(red, green, blue, alpha).next();
            bufferBuilder.vertex(m, (float) ftr.x, (float) ftr.y, (float) ftr.z).color(red, green, blue, alpha).next();
            bufferBuilder.vertex(m, (float) fbr.x, (float) fbr.y, (float) fbr.z).color(red, green, blue, alpha).next();
            bufferBuilder.vertex(m, (float) fbl.x, (float) fbl.y, (float) fbl.z).color(red, green, blue, alpha).next();
        });
    }

    public static void renderOutline(MatrixStack stack, Color color, Vec3d start, Vec3d dimensions) {
        Matrix4f m = stack.peek().getPositionMatrix();
        genericAABBRender(VertexFormat.DrawMode.DEBUG_LINES,
            VertexFormats.POSITION_COLOR,
            GameRenderer::getPositionColorProgram,
            m,
            start,
            dimensions,
            color,
            (buffer, x1, y1, z1, x2, y2, z2, red, green, blue, alpha, matrix) -> {
                buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x1, y2, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z1).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z1).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x2, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z1).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x1, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z2).color(red, green, blue, alpha).next();
            });
    }

    public static void renderEdged(MatrixStack stack, Color colorFill, Color colorOutline, Vec3d start, Vec3d dimensions) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        float[] fill = getColor(colorFill);
        float[] outline = getColor(colorOutline);

        Vec3d vec3d = transformVec3d(start);
        Vec3d end = vec3d.add(dimensions);
        float x1 = (float) vec3d.x;
        float y1 = (float) vec3d.y;
        float z1 = (float) vec3d.z;
        float x2 = (float) end.x;
        float y2 = (float) end.y;
        float z2 = (float) end.z;
        float redFill = fill[0];
        float greenFill = fill[1];
        float blueFill = fill[2];
        float alphaFill = fill[3];
        float redOutline = outline[0];
        float greenOutline = outline[1];
        float blueOutline = outline[2];
        float alphaOutline = outline[3];
        useBuffer(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR, GameRenderer::getPositionColorProgram, buffer -> {
            buffer.vertex(matrix, x1, y2, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x1, y2, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y2, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y2, z1).color(redFill, greenFill, blueFill, alphaFill).next();

            buffer.vertex(matrix, x1, y1, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y1, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y2, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x1, y2, z2).color(redFill, greenFill, blueFill, alphaFill).next();

            buffer.vertex(matrix, x2, y2, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y1, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y1, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y2, z1).color(redFill, greenFill, blueFill, alphaFill).next();

            buffer.vertex(matrix, x2, y2, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y1, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x1, y1, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x1, y2, z1).color(redFill, greenFill, blueFill, alphaFill).next();

            buffer.vertex(matrix, x1, y2, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x1, y1, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x1, y1, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x1, y2, z2).color(redFill, greenFill, blueFill, alphaFill).next();

            buffer.vertex(matrix, x1, y1, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y1, z1).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x2, y1, z2).color(redFill, greenFill, blueFill, alphaFill).next();
            buffer.vertex(matrix, x1, y1, z2).color(redFill, greenFill, blueFill, alphaFill).next();
        });

        useBuffer(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR, GameRenderer::getPositionColorProgram, buffer -> {
            buffer.vertex(matrix, x1, y1, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x1, y1, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x1, y1, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y1, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y1, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y1, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y1, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x1, y1, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();

            buffer.vertex(matrix, x1, y2, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x1, y2, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x1, y2, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y2, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y2, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y2, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y2, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x1, y2, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();

            buffer.vertex(matrix, x1, y1, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x1, y2, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();

            buffer.vertex(matrix, x2, y1, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y2, z1).color(redOutline, greenOutline, blueOutline, alphaOutline).next();

            buffer.vertex(matrix, x2, y1, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x2, y2, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();

            buffer.vertex(matrix, x1, y1, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
            buffer.vertex(matrix, x1, y2, z2).color(redOutline, greenOutline, blueOutline, alphaOutline).next();
        });
    }

    private static void genericAABBRender(VertexFormat.DrawMode mode, VertexFormat format, Supplier<ShaderProgram> shader, Matrix4f stack, Vec3d start, Vec3d dimensions, Color color, RenderAction action) {
        float red = color.getRed() / 255f;
        float green = color.getGreen() / 255f;
        float blue = color.getBlue() / 255f;
        float alpha = transformColor(color.getAlpha() / 255f);
        //            stack.push();
        Vec3d vec3d = transformVec3d(start);
        Vec3d end = vec3d.add(dimensions);
        float x1 = (float) vec3d.x;
        float y1 = (float) vec3d.y;
        float z1 = (float) vec3d.z;
        float x2 = (float) end.x;
        float y2 = (float) end.y;
        float z2 = (float) end.z;
        useBuffer(mode, format, shader, bufferBuilder -> action.run(bufferBuilder, x1, y1, z1, x2, y2, z2, red, green, blue, alpha, stack));
        //            stack.pop();
    }

    public static void renderFilled(MatrixStack stack, Color color, Vec3d start, Vec3d dimensions) {
        Matrix4f s = stack.peek().getPositionMatrix();
        genericAABBRender(VertexFormat.DrawMode.QUADS,
            VertexFormats.POSITION_COLOR,
            GameRenderer::getPositionColorProgram,
            s,
            start,
            dimensions,
            color,
            (buffer, x1, y1, z1, x2, y2, z2, red, green, blue, alpha, matrix) -> {
                buffer.vertex(matrix, x1, y2, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z1).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x1, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z2).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y2, z1).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x2, y2, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z1).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x1, y2, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y2, z2).color(red, green, blue, alpha).next();

                buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z1).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x2, y1, z2).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y1, z2).color(red, green, blue, alpha).next();
            });
    }

    public static void renderLine(MatrixStack matrices, Color color, Vec3d start, Vec3d end) {
        Matrix4f s = matrices.peek().getPositionMatrix();
        genericAABBRender(VertexFormat.DrawMode.DEBUG_LINES,
            VertexFormats.POSITION_COLOR,
            GameRenderer::getPositionColorProgram,
            s,
            start,
            end.subtract(start),
            color,
            (buffer, x, y, z, x1, y1, z1, red, green, blue, alpha, matrix) -> {
                buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).next();
                buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).next();
            });
    }

    /**
     * @param original       the original color
     * @param redOverwrite   the new red (or -1 for original)
     * @param greenOverwrite the new green (or -1 for original)
     * @param blueOverwrite  the new blue (or -1 for original)
     * @param alphaOverwrite the new alpha (or -1 for original)
     *
     * @return the modified color
     */
    public static Color modifyColor(Color original, int redOverwrite, int greenOverwrite, int blueOverwrite, int alphaOverwrite) {
        return new Color(redOverwrite == -1 ? original.getRed() : redOverwrite,
            greenOverwrite == -1 ? original.getGreen() : greenOverwrite,
            blueOverwrite == -1 ? original.getBlue() : blueOverwrite,
            alphaOverwrite == -1 ? original.getAlpha() : alphaOverwrite);
    }

    interface RenderAction {
        void run(BufferBuilder buffer, float x, float y, float z, float x1, float y1, float z1, float red, float green, float blue, float alpha, Matrix4f matrix);
    }

    record FadingBlock(Color outline, Color fill, Vec3d start, Vec3d dimensions, long created, long lifeTime) {
        long getLifeTimeLeft() {
            return Math.max(0, (created - System.currentTimeMillis()) + lifeTime);
        }

        boolean isDead() {
            return getLifeTimeLeft() == 0;
        }
    }
}